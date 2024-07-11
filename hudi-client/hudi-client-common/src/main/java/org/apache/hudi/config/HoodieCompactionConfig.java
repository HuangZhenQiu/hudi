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

package org.apache.hudi.config;

import static org.apache.hudi.common.config.HoodieReaderConfig.ENABLE_OPTIMIZED_LOG_BLOCKS_SCAN;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import javax.annotation.concurrent.Immutable;
import org.apache.hudi.common.config.ConfigClassProperty;
import org.apache.hudi.common.config.ConfigGroups;
import org.apache.hudi.common.config.ConfigProperty;
import org.apache.hudi.common.config.HoodieConfig;
import org.apache.hudi.common.config.HoodieReaderConfig;
import org.apache.hudi.table.action.compact.CompactionTriggerStrategy;
import org.apache.hudi.table.action.compact.strategy.CompactionStrategy;
import org.apache.hudi.table.action.compact.strategy.LogFileSizeBasedCompactionStrategy;

/** Compaction related config. */
@Immutable
@ConfigClassProperty(
        name = "Compaction Configs",
        groupName = ConfigGroups.Names.WRITE_CLIENT,
        description =
                "Configurations that control compaction " + "(merging of log files onto a new base files).")
public class HoodieCompactionConfig extends HoodieConfig {

    public static final ConfigProperty<String> INLINE_COMPACT =
            ConfigProperty.key("hoodie.compact.inline")
                    .defaultValue("false")
                    .withDocumentation(
                            "When set to true, compaction service is triggered after each write. While being "
                                    + " simpler operationally, this adds extra latency on the write path.");

    public static final ConfigProperty<String> SCHEDULE_INLINE_COMPACT =
            ConfigProperty.key("hoodie.compact.schedule.inline")
                    .defaultValue("false")
                    .markAdvanced()
                    .withDocumentation(
                            "When set to true, compaction service will be attempted for inline scheduling after each write. Users have to ensure "
                                    + "they have a separate job to run async compaction(execution) for the one scheduled by this writer. Users can choose to set both "
                                    + "`hoodie.compact.inline` and `hoodie.compact.schedule.inline` to false and have both scheduling and execution triggered by any async process. "
                                    + "But if `hoodie.compact.inline` is set to false, and `hoodie.compact.schedule.inline` is set to true, regular writers will schedule compaction inline, "
                                    + "but users are expected to trigger async job for execution. If `hoodie.compact.inline` is set to true, regular writers will do both scheduling and "
                                    + "execution inline for compaction");

    public static final ConfigProperty<String> ENABLE_LOG_COMPACTION =
            ConfigProperty.key("hoodie.log.compaction.enable")
                    .defaultValue("false")
                    .markAdvanced()
                    .sinceVersion("0.14.0")
                    .withDocumentation(
                            "By enabling log compaction through this config, log compaction will also get enabled for the metadata table.");

    public static final ConfigProperty<String> INLINE_LOG_COMPACT =
            ConfigProperty.key("hoodie.log.compaction.inline")
                    .defaultValue("false")
                    .markAdvanced()
                    .sinceVersion("0.13.0")
                    .withDocumentation(
                            "When set to true, logcompaction service is triggered after each write. While being "
                                    + " simpler operationally, this adds extra latency on the write path.");

    public static final ConfigProperty<String> INLINE_COMPACT_NUM_DELTA_COMMITS =
            ConfigProperty.key("hoodie.compact.inline.max.delta.commits")
                    .defaultValue("5")
                    .withDocumentation(
                            "Number of delta commits after the last compaction, before scheduling of a new compaction is attempted. "
                                    + "This config takes effect only for the compaction triggering strategy based on the number of commits, "
                                    + "i.e., NUM_COMMITS, NUM_COMMITS_AFTER_LAST_REQUEST, NUM_AND_TIME, and NUM_OR_TIME.");

    public static final ConfigProperty<String> INLINE_COMPACT_TIME_DELTA_SECONDS =
            ConfigProperty.key("hoodie.compact.inline.max.delta.seconds")
                    .defaultValue(String.valueOf(60 * 60))
                    .markAdvanced()
                    .withDocumentation(
                            "Number of elapsed seconds after the last compaction, before scheduling a new one. "
                                    + "This config takes effect only for the compaction triggering strategy based on the elapsed time, "
                                    + "i.e., TIME_ELAPSED, NUM_AND_TIME, and NUM_OR_TIME.");

    public static final ConfigProperty<String> INLINE_COMPACT_TRIGGER_STRATEGY =
            ConfigProperty.key("hoodie.compact.inline.trigger.strategy")
                    .defaultValue(CompactionTriggerStrategy.NUM_COMMITS.name())
                    .markAdvanced()
                    .withDocumentation(CompactionTriggerStrategy.class);

    public static final ConfigProperty<String> PARQUET_SMALL_FILE_LIMIT =
            ConfigProperty.key("hoodie.parquet.small.file.limit")
                    .defaultValue(String.valueOf(104857600))
                    .markAdvanced()
                    .withDocumentation(
                            "During upsert operation, we opportunistically expand existing small files on storage, instead of writing"
                                    + " new files, to keep number of files to an optimum. This config sets the file size limit below which a file on storage "
                                    + " becomes a candidate to be selected as such a `small file`. By default, treat any file <= 100MB as a small file."
                                    + " Also note that if this set <= 0, will not try to get small files and directly write new files");

    public static final ConfigProperty<String> RECORD_SIZE_ESTIMATION_THRESHOLD =
            ConfigProperty.key("hoodie.record.size.estimation.threshold")
                    .defaultValue("1.0")
                    .markAdvanced()
                    .withDocumentation(
                            "We use the previous commits' metadata to calculate the estimated record size and use it "
                                    + " to bin pack records into partitions. If the previous commit is too small to make an accurate estimation, "
                                    + " Hudi will search commits in the reverse order, until we find a commit that has totalBytesWritten "
                                    + " larger than (PARQUET_SMALL_FILE_LIMIT_BYTES * this_threshold)");

    // 500GB of target IO per compaction (both read and write
    public static final ConfigProperty<String> TARGET_IO_PER_COMPACTION_IN_MB =
            ConfigProperty.key("hoodie.compaction.target.io")
                    .defaultValue(String.valueOf(500 * 1024))
                    .markAdvanced()
                    .withDocumentation(
                            "Amount of MBs to spend during compaction run for the LogFileSizeBasedCompactionStrategy. "
                                    + "This value helps bound ingestion latency while compaction is run inline mode.");

    public static final ConfigProperty<Long> COMPACTION_LOG_FILE_SIZE_THRESHOLD =
            ConfigProperty.key("hoodie.compaction.logfile.size.threshold")
                    .defaultValue(0L)
                    .markAdvanced()
                    .withDocumentation(
                            "Only if the log file size is greater than the threshold in bytes,"
                                    + " the file group will be compacted.");

    public static final ConfigProperty<Long> COMPACTION_LOG_FILE_NUM_THRESHOLD =
            ConfigProperty.key("hoodie.compaction.logfile.num.threshold")
                    .defaultValue(0L)
                    .markAdvanced()
                    .sinceVersion("0.13.0")
                    .withDocumentation(
                            "Only if the log file num is greater than the threshold,"
                                    + " the file group will be compacted.");

    public static final ConfigProperty<String> COMPACTION_STRATEGY =
            ConfigProperty.key("hoodie.compaction.strategy")
                    .defaultValue(LogFileSizeBasedCompactionStrategy.class.getName())
                    .markAdvanced()
                    .withDocumentation(
                            "Compaction strategy decides which file groups are picked up for "
                                    + "compaction during each compaction run. By default. Hudi picks the log file "
                                    + "with most accumulated unmerged data");

    public static final ConfigProperty<String> TARGET_PARTITIONS_PER_DAYBASED_COMPACTION =
            ConfigProperty.key("hoodie.compaction.daybased.target.partitions")
                    .defaultValue("10")
                    .markAdvanced()
                    .withDocumentation(
                            "Used by org.apache.hudi.io.compact.strategy.DayBasedCompactionStrategy to denote the number of "
                                    + "latest partitions to compact during a compaction run.");

    /** Configs related to specific table types. */
    public static final ConfigProperty<String> COPY_ON_WRITE_INSERT_SPLIT_SIZE =
            ConfigProperty.key("hoodie.copyonwrite.insert.split.size")
                    .defaultValue(String.valueOf(500000))
                    .markAdvanced()
                    .withDocumentation(
                            "Number of inserts assigned for each partition/bucket for writing. "
                                    + "We based the default on writing out 100MB files, with at least 1kb records (100K records per file), and "
                                    + "  over provision to 500K. As long as auto-tuning of splits is turned on, this only affects the first "
                                    + "  write, where there is no history to learn record sizes from.");

    public static final ConfigProperty<String> COPY_ON_WRITE_AUTO_SPLIT_INSERTS =
            ConfigProperty.key("hoodie.copyonwrite.insert.auto.split")
                    .defaultValue("true")
                    .markAdvanced()
                    .withDocumentation(
                            "Config to control whether we control insert split sizes automatically based on average"
                                    + " record sizes. It's recommended to keep this turned on, since hand tuning is otherwise extremely"
                                    + " cumbersome.");

    public static final ConfigProperty<String> COPY_ON_WRITE_RECORD_SIZE_ESTIMATE =
            ConfigProperty.key("hoodie.copyonwrite.record.size.estimate")
                    .defaultValue(String.valueOf(1024))
                    .markAdvanced()
                    .withDocumentation(
                            "The average record size. If not explicitly specified, hudi will compute the "
                                    + "record size estimate compute dynamically based on commit metadata. "
                                    + " This is critical in computing the insert parallelism and bin-packing inserts into small files.");

    public static final ConfigProperty<String> LOG_COMPACTION_BLOCKS_THRESHOLD =
            ConfigProperty.key("hoodie.log.compaction.blocks.threshold")
                    .defaultValue("5")
                    .markAdvanced()
                    .sinceVersion("0.13.0")
                    .withDocumentation(
                            "Log compaction can be scheduled if the no. of log blocks crosses this threshold value. "
                                    + "This is effective only when log compaction is enabled via "
                                    + INLINE_LOG_COMPACT.key());

    /** @deprecated Use {@link #INLINE_COMPACT} and its methods instead */
    @Deprecated public static final String INLINE_COMPACT_PROP = INLINE_COMPACT.key();
    /** @deprecated Use {@link #INLINE_COMPACT_NUM_DELTA_COMMITS} and its methods instead */
    @Deprecated
    public static final String INLINE_COMPACT_NUM_DELTA_COMMITS_PROP =
            INLINE_COMPACT_NUM_DELTA_COMMITS.key();
    /** @deprecated Use {@link #INLINE_COMPACT_TIME_DELTA_SECONDS} and its methods instead */
    @Deprecated
    public static final String INLINE_COMPACT_TIME_DELTA_SECONDS_PROP =
            INLINE_COMPACT_TIME_DELTA_SECONDS.key();
    /** @deprecated Use {@link #INLINE_COMPACT_TRIGGER_STRATEGY} and its methods instead */
    @Deprecated
    public static final String INLINE_COMPACT_TRIGGER_STRATEGY_PROP =
            INLINE_COMPACT_TRIGGER_STRATEGY.key();
    /** @deprecated Use {@link #PARQUET_SMALL_FILE_LIMIT} and its methods instead */
    @Deprecated
    public static final String PARQUET_SMALL_FILE_LIMIT_BYTES = PARQUET_SMALL_FILE_LIMIT.key();
    /** @deprecated Use {@link #PARQUET_SMALL_FILE_LIMIT} and its methods instead */
    @Deprecated
    public static final String DEFAULT_PARQUET_SMALL_FILE_LIMIT_BYTES =
            PARQUET_SMALL_FILE_LIMIT.defaultValue();
    /** @deprecated Use {@link #RECORD_SIZE_ESTIMATION_THRESHOLD} and its methods instead */
    @Deprecated
    public static final String RECORD_SIZE_ESTIMATION_THRESHOLD_PROP =
            RECORD_SIZE_ESTIMATION_THRESHOLD.key();
    /** @deprecated Use {@link #RECORD_SIZE_ESTIMATION_THRESHOLD} and its methods instead */
    @Deprecated
    public static final String DEFAULT_RECORD_SIZE_ESTIMATION_THRESHOLD =
            RECORD_SIZE_ESTIMATION_THRESHOLD.defaultValue();
    /** @deprecated Use {@link #COPY_ON_WRITE_INSERT_SPLIT_SIZE} and its methods instead */
    @Deprecated
    public static final String COPY_ON_WRITE_TABLE_INSERT_SPLIT_SIZE =
            COPY_ON_WRITE_INSERT_SPLIT_SIZE.key();
    /** @deprecated Use {@link #COPY_ON_WRITE_INSERT_SPLIT_SIZE} and its methods instead */
    @Deprecated
    public static final String DEFAULT_COPY_ON_WRITE_TABLE_INSERT_SPLIT_SIZE =
            COPY_ON_WRITE_INSERT_SPLIT_SIZE.defaultValue();
    /** @deprecated Use {@link #COPY_ON_WRITE_AUTO_SPLIT_INSERTS} and its methods instead */
    @Deprecated
    public static final String COPY_ON_WRITE_TABLE_AUTO_SPLIT_INSERTS =
            COPY_ON_WRITE_AUTO_SPLIT_INSERTS.key();
    /** @deprecated Use {@link #COPY_ON_WRITE_AUTO_SPLIT_INSERTS} and its methods instead */
    @Deprecated
    public static final String DEFAULT_COPY_ON_WRITE_TABLE_AUTO_SPLIT_INSERTS =
            COPY_ON_WRITE_AUTO_SPLIT_INSERTS.defaultValue();
    /** @deprecated Use {@link #COPY_ON_WRITE_RECORD_SIZE_ESTIMATE} and its methods instead */
    @Deprecated
    public static final String COPY_ON_WRITE_TABLE_RECORD_SIZE_ESTIMATE =
            COPY_ON_WRITE_RECORD_SIZE_ESTIMATE.key();
    /** @deprecated Use {@link #COPY_ON_WRITE_RECORD_SIZE_ESTIMATE} and its methods instead */
    @Deprecated
    public static final String DEFAULT_COPY_ON_WRITE_TABLE_RECORD_SIZE_ESTIMATE =
            COPY_ON_WRITE_RECORD_SIZE_ESTIMATE.defaultValue();
    /** @deprecated Use {@link #TARGET_IO_PER_COMPACTION_IN_MB} and its methods instead */
    @Deprecated
    public static final String TARGET_IO_PER_COMPACTION_IN_MB_PROP =
            TARGET_IO_PER_COMPACTION_IN_MB.key();
    /** @deprecated Use {@link #TARGET_IO_PER_COMPACTION_IN_MB} and its methods instead */
    @Deprecated
    public static final String DEFAULT_TARGET_IO_PER_COMPACTION_IN_MB =
            TARGET_IO_PER_COMPACTION_IN_MB.defaultValue();
    /** @deprecated Use {@link #COMPACTION_STRATEGY} and its methods instead */
    @Deprecated public static final String COMPACTION_STRATEGY_PROP = COMPACTION_STRATEGY.key();
    /** @deprecated Use {@link #COMPACTION_STRATEGY} and its methods instead */
    @Deprecated
    public static final String DEFAULT_COMPACTION_STRATEGY = COMPACTION_STRATEGY.defaultValue();
    /**
      * @deprecated Use {@link HoodieReaderConfig#COMPACTION_LAZY_BLOCK_READ_ENABLE} and its methods
      *     instead
      */
    @Deprecated
    public static final String COMPACTION_LAZY_BLOCK_READ_ENABLED_PROP =
            HoodieReaderConfig.COMPACTION_LAZY_BLOCK_READ_ENABLE.key();
    /**
      * @deprecated Use {@link HoodieReaderConfig#COMPACTION_LAZY_BLOCK_READ_ENABLE} and its methods
      *     instead
      */
    @Deprecated
    public static final String DEFAULT_COMPACTION_LAZY_BLOCK_READ_ENABLED =
            HoodieReaderConfig.COMPACTION_LAZY_BLOCK_READ_ENABLE.defaultValue();
    /**
      * @deprecated Use {@link HoodieReaderConfig#COMPACTION_REVERSE_LOG_READ_ENABLE} and its methods
      *     instead
      */
    @Deprecated
    public static final String COMPACTION_REVERSE_LOG_READ_ENABLED_PROP =
            HoodieReaderConfig.COMPACTION_REVERSE_LOG_READ_ENABLE.key();
    /**
      * @deprecated Use {@link HoodieReaderConfig#COMPACTION_REVERSE_LOG_READ_ENABLE} and its methods
      *     instead
      */
    @Deprecated
    public static final String DEFAULT_COMPACTION_REVERSE_LOG_READ_ENABLED =
            HoodieReaderConfig.COMPACTION_REVERSE_LOG_READ_ENABLE.defaultValue();
    /** @deprecated Use {@link #INLINE_COMPACT} and its methods instead */
    @Deprecated private static final String DEFAULT_INLINE_COMPACT = INLINE_COMPACT.defaultValue();
    /** @deprecated Use {@link #INLINE_COMPACT_NUM_DELTA_COMMITS} and its methods instead */
    @Deprecated
    private static final String DEFAULT_INLINE_COMPACT_NUM_DELTA_COMMITS =
            INLINE_COMPACT_NUM_DELTA_COMMITS.defaultValue();
    /** @deprecated Use {@link #INLINE_COMPACT_TIME_DELTA_SECONDS} and its methods instead */
    @Deprecated
    private static final String DEFAULT_INLINE_COMPACT_TIME_DELTA_SECONDS =
            INLINE_COMPACT_TIME_DELTA_SECONDS.defaultValue();
    /** @deprecated Use {@link #INLINE_COMPACT_TRIGGER_STRATEGY} and its methods instead */
    @Deprecated
    private static final String DEFAULT_INLINE_COMPACT_TRIGGER_STRATEGY =
            INLINE_COMPACT_TRIGGER_STRATEGY.defaultValue();
    /** @deprecated Use {@link #TARGET_PARTITIONS_PER_DAYBASED_COMPACTION} and its methods instead */
    @Deprecated
    public static final String TARGET_PARTITIONS_PER_DAYBASED_COMPACTION_PROP =
            TARGET_PARTITIONS_PER_DAYBASED_COMPACTION.key();
    /** @deprecated Use {@link #TARGET_PARTITIONS_PER_DAYBASED_COMPACTION} and its methods instead */
    @Deprecated
    public static final String DEFAULT_TARGET_PARTITIONS_PER_DAYBASED_COMPACTION =
            TARGET_PARTITIONS_PER_DAYBASED_COMPACTION.defaultValue();

    private HoodieCompactionConfig() {
        super();
    }

    public static HoodieCompactionConfig.Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {

        private final HoodieCompactionConfig compactionConfig = new HoodieCompactionConfig();

        public Builder fromFile(File propertiesFile) throws IOException {
            try (FileReader reader = new FileReader(propertiesFile)) {
                this.compactionConfig.getProps().load(reader);
                return this;
            }
        }

        public Builder fromProperties(Properties props) {
            this.compactionConfig.getProps().putAll(props);
            return this;
        }

        public Builder withInlineCompaction(Boolean inlineCompaction) {
            compactionConfig.setValue(INLINE_COMPACT, String.valueOf(inlineCompaction));
            return this;
        }

        public Builder withScheduleInlineCompaction(Boolean scheduleAsyncCompaction) {
            compactionConfig.setValue(SCHEDULE_INLINE_COMPACT, String.valueOf(scheduleAsyncCompaction));
            return this;
        }

        public Builder withInlineLogCompaction(Boolean inlineLogCompaction) {
            compactionConfig.setValue(INLINE_LOG_COMPACT, String.valueOf(inlineLogCompaction));
            return this;
        }

        public Builder withInlineCompactionTriggerStrategy(
                CompactionTriggerStrategy compactionTriggerStrategy) {
            compactionConfig.setValue(INLINE_COMPACT_TRIGGER_STRATEGY, compactionTriggerStrategy.name());
            return this;
        }

        public Builder compactionSmallFileSize(long smallFileLimitBytes) {
            compactionConfig.setValue(PARQUET_SMALL_FILE_LIMIT, String.valueOf(smallFileLimitBytes));
            return this;
        }

        public Builder compactionRecordSizeEstimateThreshold(double threshold) {
            compactionConfig.setValue(RECORD_SIZE_ESTIMATION_THRESHOLD, String.valueOf(threshold));
            return this;
        }

        public Builder insertSplitSize(int insertSplitSize) {
            compactionConfig.setValue(COPY_ON_WRITE_INSERT_SPLIT_SIZE, String.valueOf(insertSplitSize));
            return this;
        }

        public Builder autoTuneInsertSplits(boolean autoTuneInsertSplits) {
            compactionConfig.setValue(
                    COPY_ON_WRITE_AUTO_SPLIT_INSERTS, String.valueOf(autoTuneInsertSplits));
            return this;
        }

        public Builder approxRecordSize(int recordSizeEstimate) {
            compactionConfig.setValue(
                    COPY_ON_WRITE_RECORD_SIZE_ESTIMATE, String.valueOf(recordSizeEstimate));
            return this;
        }

        public Builder withCompactionStrategy(CompactionStrategy compactionStrategy) {
            compactionConfig.setValue(COMPACTION_STRATEGY, compactionStrategy.getClass().getName());
            return this;
        }

        public Builder withTargetIOPerCompactionInMB(long targetIOPerCompactionInMB) {
            compactionConfig.setValue(
                    TARGET_IO_PER_COMPACTION_IN_MB, String.valueOf(targetIOPerCompactionInMB));
            return this;
        }

        public Builder withMaxNumDeltaCommitsBeforeCompaction(int maxNumDeltaCommitsBeforeCompaction) {
            compactionConfig.setValue(
                    INLINE_COMPACT_NUM_DELTA_COMMITS, String.valueOf(maxNumDeltaCommitsBeforeCompaction));
            return this;
        }

        public Builder withMaxDeltaSecondsBeforeCompaction(int maxDeltaSecondsBeforeCompaction) {
            compactionConfig.setValue(
                    INLINE_COMPACT_TIME_DELTA_SECONDS, String.valueOf(maxDeltaSecondsBeforeCompaction));
            return this;
        }

        public Builder withCompactionLazyBlockReadEnabled(Boolean compactionLazyBlockReadEnabled) {
            compactionConfig.setValue(
                    HoodieReaderConfig.COMPACTION_LAZY_BLOCK_READ_ENABLE,
                    String.valueOf(compactionLazyBlockReadEnabled));
            return this;
        }

        public Builder withCompactionReverseLogReadEnabled(Boolean compactionReverseLogReadEnabled) {
            compactionConfig.setValue(
                    HoodieReaderConfig.COMPACTION_REVERSE_LOG_READ_ENABLE,
                    String.valueOf(compactionReverseLogReadEnabled));
            return this;
        }

        public Builder withTargetPartitionsPerDayBasedCompaction(int targetPartitionsPerCompaction) {
            compactionConfig.setValue(
                    TARGET_PARTITIONS_PER_DAYBASED_COMPACTION, String.valueOf(targetPartitionsPerCompaction));
            return this;
        }

        public Builder withLogFileSizeThresholdBasedCompaction(long logFileSizeThreshold) {
            compactionConfig.setValue(
                    COMPACTION_LOG_FILE_SIZE_THRESHOLD, String.valueOf(logFileSizeThreshold));
            return this;
        }

        public Builder withCompactionLogFileNumThreshold(int logFileNumThreshold) {
            compactionConfig.setValue(
                    COMPACTION_LOG_FILE_NUM_THRESHOLD, String.valueOf(logFileNumThreshold));
            return this;
        }

        public Builder withLogCompactionEnabled(boolean enableLogCompaction) {
            compactionConfig.setValue(ENABLE_LOG_COMPACTION, Boolean.toString(enableLogCompaction));
            return this;
        }

        public Builder withLogCompactionBlocksThreshold(int logCompactionBlocksThreshold) {
            compactionConfig.setValue(
                    LOG_COMPACTION_BLOCKS_THRESHOLD, String.valueOf(logCompactionBlocksThreshold));
            return this;
        }

        public Builder withEnableOptimizedLogBlocksScan(String enableOptimizedLogBlocksScan) {
            compactionConfig.setValue(ENABLE_OPTIMIZED_LOG_BLOCKS_SCAN, enableOptimizedLogBlocksScan);
            return this;
        }

        public HoodieCompactionConfig build() {
            compactionConfig.setDefaults(HoodieCompactionConfig.class.getName());
            compactionConfig.setDefaults(HoodieReaderConfig.class.getName());
            return compactionConfig;
        }
    }
}
