/*
  * Licensed to the Apache Software Foundation (ASF) under one
  * or more contributor license agreements.  See the NOTICE file
  * distributed with this work for additional information
  * regarding copyright ownership.  The ASF licenses this file
  * to you under the Apache License, Version 2.0 (the
  * "License"); you may not use this file except in compliance
  * with the License.  You may obtain a copy of the License at
  *
  *      http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

package org.apache.hudi.table.action.clean;

import static org.apache.hudi.client.utils.MetadataTableUtils.shouldUseBatchLookup;
import static org.apache.hudi.common.util.MapUtils.nonEmpty;
import static org.apache.hudi.table.action.clean.CleanPlanner.SAVEPOINTED_TIMESTAMPS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.hudi.avro.model.HoodieActionInstant;
import org.apache.hudi.avro.model.HoodieCleanFileInfo;
import org.apache.hudi.avro.model.HoodieCleanMetadata;
import org.apache.hudi.avro.model.HoodieCleanerPlan;
import org.apache.hudi.common.engine.HoodieEngineContext;
import org.apache.hudi.common.model.CleanFileInfo;
import org.apache.hudi.common.model.HoodieCleaningPolicy;
import org.apache.hudi.common.table.timeline.HoodieInstant;
import org.apache.hudi.common.table.timeline.HoodieTimeline;
import org.apache.hudi.common.table.timeline.TimelineMetadataUtils;
import org.apache.hudi.common.util.CleanerUtils;
import org.apache.hudi.common.util.Option;
import org.apache.hudi.common.util.collection.Pair;
import org.apache.hudi.config.HoodieWriteConfig;
import org.apache.hudi.exception.HoodieException;
import org.apache.hudi.exception.HoodieIOException;
import org.apache.hudi.table.HoodieTable;
import org.apache.hudi.table.action.BaseActionExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CleanPlanActionExecutor<T, I, K, O>
        extends BaseActionExecutor<T, I, K, O, Option<HoodieCleanerPlan>> {

    private static final Logger LOG = LoggerFactory.getLogger(CleanPlanActionExecutor.class);
    private final Option<Map<String, String>> extraMetadata;

    public CleanPlanActionExecutor(
            HoodieEngineContext context,
            HoodieWriteConfig config,
            HoodieTable<T, I, K, O> table,
            String instantTime,
            Option<Map<String, String>> extraMetadata) {
        super(context, config, table, instantTime);
        this.extraMetadata = extraMetadata;
    }

    private int getCommitsSinceLastCleaning() {
        Option<HoodieInstant> lastCleanInstant =
                table.getActiveTimeline().getCleanerTimeline().filterCompletedInstants().lastInstant();
        HoodieTimeline commitTimeline =
                table.getActiveTimeline().getCommitsTimeline().filterCompletedInstants();

        int numCommits;
        if (lastCleanInstant.isPresent()
                && !table.getActiveTimeline().isEmpty(lastCleanInstant.get())) {
            try {
                HoodieCleanMetadata cleanMetadata =
                        TimelineMetadataUtils.deserializeHoodieCleanMetadata(
                                table.getActiveTimeline().getInstantDetails(lastCleanInstant.get()).get());
                String lastCompletedCommitTimestamp = cleanMetadata.getLastCompletedCommitTimestamp();
                numCommits = commitTimeline.findInstantsAfter(lastCompletedCommitTimestamp).countInstants();
            } catch (IOException e) {
                throw new HoodieIOException(
                        "Parsing of last clean instant " + lastCleanInstant.get() + " failed", e);
            }
        } else {
            numCommits = commitTimeline.countInstants();
        }

        return numCommits;
    }

    private boolean needsCleaning(CleaningTriggerStrategy strategy) {
        if (strategy == CleaningTriggerStrategy.NUM_COMMITS) {
            int numberOfCommits = getCommitsSinceLastCleaning();
            int maxInlineCommitsForNextClean = config.getCleaningMaxCommits();
            return numberOfCommits >= maxInlineCommitsForNextClean;
        } else {
            throw new HoodieException(
                    "Unsupported cleaning trigger strategy: " + config.getCleaningTriggerStrategy());
        }
    }

    /**
      * Generates List of files to be cleaned.
      *
      * @param context HoodieEngineContext
      * @return Cleaner Plan
      */
    HoodieCleanerPlan requestClean(HoodieEngineContext context) {
        try {
            CleanPlanner<T, I, K, O> planner = new CleanPlanner<>(context, table, config);
            Option<HoodieInstant> earliestInstant = planner.getEarliestCommitToRetain();
            context.setJobStatus(
                    this.getClass().getSimpleName(),
                    "Obtaining list of partitions to be cleaned: " + config.getTableName());
            List<String> partitionsToClean = planner.getPartitionPathsToClean(earliestInstant);

            if (partitionsToClean.isEmpty()) {
                LOG.info("Nothing to clean here. It is already clean");
                return HoodieCleanerPlan.newBuilder()
                        .setPolicy(HoodieCleaningPolicy.KEEP_LATEST_COMMITS.name())
                        .build();
            }
            LOG.info(
                    "Earliest commit to retain for clean : "
                            + (earliestInstant.isPresent() ? earliestInstant.get().getTimestamp() : "null"));
            LOG.info(
                    "Total partitions to clean : "
                            + partitionsToClean.size()
                            + ", with policy "
                            + config.getCleanerPolicy());
            int cleanerParallelism = Math.min(partitionsToClean.size(), config.getCleanerParallelism());
            LOG.info("Using cleanerParallelism: " + cleanerParallelism);

            context.setJobStatus(
                    this.getClass().getSimpleName(),
                    "Generating list of file slices to be cleaned: " + config.getTableName());

            Map<String, List<HoodieCleanFileInfo>> cleanOps = new HashMap<>();
            List<String> partitionsToDelete = new ArrayList<>();
            boolean shouldUseBatchLookup =
                    shouldUseBatchLookup(table.getMetaClient().getTableConfig(), config);
            for (int i = 0; i < partitionsToClean.size(); i += cleanerParallelism) {
                // Handles at most 'cleanerParallelism' number of partitions once at a time to avoid
                // overlarge memory pressure to the timeline server
                // (remote or local embedded), thus to reduce the risk of an OOM exception.
                List<String> subPartitionsToClean =
                        partitionsToClean.subList(
                                i, Math.min(i + cleanerParallelism, partitionsToClean.size()));
                if (shouldUseBatchLookup) {
                    LOG.info(
                            "Load partitions and files into file system view in advance. Paths: {}",
                            subPartitionsToClean);
                    table.getHoodieView().loadPartitions(subPartitionsToClean);
                }
                Map<String, Pair<Boolean, List<CleanFileInfo>>> cleanOpsWithPartitionMeta =
                        context
                                .map(
                                        subPartitionsToClean,
                                        partitionPathToClean ->
                                                Pair.of(
                                                        partitionPathToClean,
                                                        planner.getDeletePaths(partitionPathToClean, earliestInstant)),
                                        cleanerParallelism)
                                .stream()
                                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));

                cleanOps.putAll(
                        cleanOpsWithPartitionMeta.entrySet().stream()
                                .filter(e -> !e.getValue().getValue().isEmpty())
                                .collect(
                                        Collectors.toMap(
                                                Map.Entry::getKey,
                                                e ->
                                                        CleanerUtils.convertToHoodieCleanFileInfoList(
                                                                e.getValue().getValue()))));

                partitionsToDelete.addAll(
                        cleanOpsWithPartitionMeta.entrySet().stream()
                                .filter(entry -> entry.getValue().getKey())
                                .map(Map.Entry::getKey)
                                .collect(Collectors.toList()));
            }

            return new HoodieCleanerPlan(
                    earliestInstant
                            .map(
                                    x ->
                                            new HoodieActionInstant(x.getTimestamp(), x.getAction(), x.getState().name()))
                            .orElse(null),
                    planner.getLastCompletedCommitTimestamp(),
                    config.getCleanerPolicy().name(),
                    Collections.emptyMap(),
                    CleanPlanner.LATEST_CLEAN_PLAN_VERSION,
                    cleanOps,
                    partitionsToDelete,
                    prepareExtraMetadata(planner.getSavepointedTimestamps()));
        } catch (IOException e) {
            throw new HoodieIOException("Failed to schedule clean operation", e);
        }
    }

    private Map<String, String> prepareExtraMetadata(List<String> savepointedTimestamps) {
        if (savepointedTimestamps.isEmpty()) {
            return Collections.emptyMap();
        } else {
            return Collections.singletonMap(
                    SAVEPOINTED_TIMESTAMPS, savepointedTimestamps.stream().collect(Collectors.joining(",")));
        }
    }

    /**
      * Creates a Cleaner plan if there are files to be cleaned and stores them in instant file.
      * Cleaner Plan contains absolute file paths.
      *
      * @param startCleanTime Cleaner Instant Time
      * @return Cleaner Plan if generated
      */
    protected Option<HoodieCleanerPlan> requestClean(String startCleanTime) {
        final HoodieCleanerPlan cleanerPlan = requestClean(context);
        Option<HoodieCleanerPlan> option = Option.empty();
        if (nonEmpty(cleanerPlan.getFilePathsToBeDeletedPerPartition())
                && cleanerPlan.getFilePathsToBeDeletedPerPartition().values().stream()
                                .mapToInt(List::size)
                                .sum()
                        > 0) {
            // Only create cleaner plan which does some work
            final HoodieInstant cleanInstant =
                    new HoodieInstant(
                            HoodieInstant.State.REQUESTED, HoodieTimeline.CLEAN_ACTION, startCleanTime);
            // Save to both aux and timeline folder
            try {
                table
                        .getActiveTimeline()
                        .saveToCleanRequested(
                                cleanInstant, TimelineMetadataUtils.serializeCleanerPlan(cleanerPlan));
                LOG.info("Requesting Cleaning with instant time " + cleanInstant);
            } catch (IOException e) {
                LOG.error("Got exception when saving cleaner requested file", e);
                throw new HoodieIOException(e.getMessage(), e);
            }
            option = Option.of(cleanerPlan);
        }

        return option;
    }

    @Override
    public Option<HoodieCleanerPlan> execute() {
        if (!needsCleaning(config.getCleaningTriggerStrategy())) {
            return Option.empty();
        }
        // Plan a new clean action
        return requestClean(instantTime);
    }
}
