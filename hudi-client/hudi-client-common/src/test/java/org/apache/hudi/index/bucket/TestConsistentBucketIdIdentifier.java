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

package org.apache.hudi.index.bucket;

import static org.apache.hudi.common.model.HoodieConsistentHashingMetadata.HASH_VALUE_MASK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.hudi.common.fs.FSUtils;
import org.apache.hudi.common.model.ConsistentHashingNode;
import org.apache.hudi.common.model.HoodieConsistentHashingMetadata;
import org.apache.hudi.common.util.Option;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Unit test of consistent bucket identifier */
public class TestConsistentBucketIdIdentifier {

    private static Stream<Arguments> splitBucketParams() {
        Object[][] data =
                new Object[][] {
                    {HASH_VALUE_MASK, 0xf, (int) (((long) 0xf + HASH_VALUE_MASK) >> 1)},
                    {1, HASH_VALUE_MASK, 0},
                    {0, HASH_VALUE_MASK, -1},
                    {1, HASH_VALUE_MASK - 10, HASH_VALUE_MASK - 4},
                    {9, HASH_VALUE_MASK - 2, 3},
                    {0, HASH_VALUE_MASK - 1, HASH_VALUE_MASK}
                };
        return Stream.of(data).map(Arguments::of);
    }

    @Test
    public void testGetBucket() {
        List<ConsistentHashingNode> nodes =
                Arrays.asList(
                        new ConsistentHashingNode(100, "0"),
                        new ConsistentHashingNode(0x2fffffff, "1"),
                        new ConsistentHashingNode(0x4fffffff, "2"));
        HoodieConsistentHashingMetadata meta =
                new HoodieConsistentHashingMetadata((short) 0, "", "", 3, 0, nodes);
        ConsistentBucketIdentifier identifier = new ConsistentBucketIdentifier(meta);

        assertEquals(3, identifier.getNumBuckets());

        // Get bucket by hash keys
        assertEquals(nodes.get(2), identifier.getBucket(Arrays.asList("Hudi")));
        assertEquals(nodes.get(1), identifier.getBucket(Arrays.asList("bucket_index")));
        assertEquals(nodes.get(1), identifier.getBucket(Arrays.asList("consistent_hashing")));
        assertEquals(
                nodes.get(1), identifier.getBucket(Arrays.asList("bucket_index", "consistent_hashing")));
        int[] ref1 = {2, 2, 1, 1, 0, 1, 1, 1, 0, 1};
        int[] ref2 = {1, 0, 1, 0, 1, 1, 1, 0, 1, 2};
        for (int i = 0; i < 10; ++i) {
            assertEquals(nodes.get(ref1[i]), identifier.getBucket(Arrays.asList(Integer.toString(i))));
            assertEquals(
                    nodes.get(ref2[i]),
                    identifier.getBucket(Arrays.asList(Integer.toString(i), Integer.toString(i + 1))));
        }

        // Get bucket by hash value
        assertEquals(nodes.get(0), identifier.getBucket(0));
        assertEquals(nodes.get(0), identifier.getBucket(50));
        assertEquals(nodes.get(0), identifier.getBucket(100));
        assertEquals(nodes.get(1), identifier.getBucket(101));
        assertEquals(nodes.get(1), identifier.getBucket(0x1fffffff));
        assertEquals(nodes.get(1), identifier.getBucket(0x2fffffff));
        assertEquals(nodes.get(2), identifier.getBucket(0x40000000));
        assertEquals(nodes.get(2), identifier.getBucket(0x40000001));
        assertEquals(nodes.get(2), identifier.getBucket(0x4fffffff));
        assertEquals(nodes.get(0), identifier.getBucket(0x50000000));
        assertEquals(nodes.get(0), identifier.getBucket(HASH_VALUE_MASK));

        // Get bucket by file id
        assertEquals(nodes.get(0), identifier.getBucketByFileId(FSUtils.createNewFileId("0", 0)));
        assertEquals(nodes.get(1), identifier.getBucketByFileId(FSUtils.createNewFileId("1", 0)));
        assertEquals(nodes.get(2), identifier.getBucketByFileId(FSUtils.createNewFileId("2", 0)));
    }

    /**
      * @param v0 first node hash value
      * @param v1 second node hash value
      * @param mid mid node hash value generated by split the first bucket v0
      */
    @ParameterizedTest
    @MethodSource("splitBucketParams")
    public void testSplitBucket(int v0, int v1, int mid) {
        // Hash range mapping:: [0, 0xf], (0xf, MAX]
        List<ConsistentHashingNode> nodes =
                Arrays.asList(new ConsistentHashingNode(v0, "0"), new ConsistentHashingNode(v1, "1"));
        HoodieConsistentHashingMetadata meta =
                new HoodieConsistentHashingMetadata((short) 0, "", "", 4, 0, nodes);
        Option<List<ConsistentHashingNode>> res =
                new ConsistentBucketIdentifier(meta).splitBucket(nodes.get(0));
        if (mid < 0) {
            assertTrue(!res.isPresent());
            return;
        }

        List<ConsistentHashingNode> childNodes = res.get();
        assertEquals(2, childNodes.size());
        assertTrue(
                childNodes.stream().allMatch(c -> c.getTag() == ConsistentHashingNode.NodeTag.REPLACE));
        assertEquals(mid, childNodes.get(0).getValue());
        assertEquals(nodes.get(0).getValue(), childNodes.get(1).getValue());
    }

    @Test
    public void testMerge() {
        HoodieConsistentHashingMetadata meta = new HoodieConsistentHashingMetadata("partition", 8);
        List<ConsistentHashingNode> nodes = meta.getNodes();

        List<String> fileIds =
                IntStream.range(0, 3)
                        .mapToObj(i -> FSUtils.createNewFileId(nodes.get(i).getFileIdPrefix(), 0))
                        .collect(Collectors.toList());
        List<ConsistentHashingNode> childNodes =
                new ConsistentBucketIdentifier(meta).mergeBucket(fileIds);
        assertEquals(ConsistentHashingNode.NodeTag.DELETE, childNodes.get(0).getTag());
        assertEquals(ConsistentHashingNode.NodeTag.DELETE, childNodes.get(1).getTag());
        assertEquals(ConsistentHashingNode.NodeTag.REPLACE, childNodes.get(2).getTag());
        assertEquals(nodes.get(2).getValue(), childNodes.get(2).getValue());
        assertNotEquals(nodes.get(2).getFileIdPrefix(), childNodes.get(2).getFileIdPrefix());

        fileIds =
                Arrays.asList(nodes.get(7), nodes.get(0), nodes.get(1)).stream()
                        .map(ConsistentHashingNode::getFileIdPrefix)
                        .map(f -> FSUtils.createNewFileId(f, 0))
                        .collect(Collectors.toList());
        childNodes = new ConsistentBucketIdentifier(meta).mergeBucket(fileIds);
        assertEquals(ConsistentHashingNode.NodeTag.DELETE, childNodes.get(0).getTag());
        assertEquals(ConsistentHashingNode.NodeTag.DELETE, childNodes.get(1).getTag());
        assertEquals(ConsistentHashingNode.NodeTag.REPLACE, childNodes.get(2).getTag());
        assertEquals(nodes.get(1).getValue(), childNodes.get(2).getValue());
        assertNotEquals(nodes.get(1).getFileIdPrefix(), childNodes.get(2).getFileIdPrefix());
    }

    @Test
    public void testNonContinuousBucketMerge() {
        HoodieConsistentHashingMetadata meta = new HoodieConsistentHashingMetadata("partition", 8);
        List<ConsistentHashingNode> nodes = meta.getNodes();

        boolean exception = false;
        try {
            List<String> fileIds =
                    IntStream.range(0, 2)
                            .mapToObj(i -> FSUtils.createNewFileId(nodes.get(i * 2).getFileIdPrefix(), 0))
                            .collect(Collectors.toList());
            new ConsistentBucketIdentifier(meta).mergeBucket(fileIds);
        } catch (Exception e) {
            exception = true;
        }
        assertTrue(exception);
    }

    @Test
    public void testChildrenNodesInitialization() {
        HoodieConsistentHashingMetadata metadata = new HoodieConsistentHashingMetadata("partition", 8);
        List<ConsistentHashingNode> childrenNodes = new ArrayList<>();
        childrenNodes.add(
                new ConsistentHashingNode(
                        metadata.getNodes().get(0).getValue(), "d1", ConsistentHashingNode.NodeTag.DELETE));
        childrenNodes.add(new ConsistentHashingNode(1024, "a1", ConsistentHashingNode.NodeTag.REPLACE));
        childrenNodes.add(
                new ConsistentHashingNode(
                        metadata.getNodes().get(1).getValue(), "a2", ConsistentHashingNode.NodeTag.REPLACE));
        metadata.setChildrenNodes(childrenNodes);

        ConsistentBucketIdentifier identifier = new ConsistentBucketIdentifier(metadata);
        List<ConsistentHashingNode> nodes = new ArrayList<>(identifier.getNodes());
        assertEquals(1024, nodes.get(0).getValue());
        assertEquals("a1", nodes.get(0).getFileIdPrefix());
        assertEquals(metadata.getNodes().get(1).getValue(), nodes.get(1).getValue());
        assertEquals("a2", nodes.get(1).getFileIdPrefix());
    }

    @Test
    public void testInvalidChildrenNodesInitialization() {
        HoodieConsistentHashingMetadata metadata = new HoodieConsistentHashingMetadata("partition", 8);
        List<ConsistentHashingNode> childrenNodes = new ArrayList<>();
        ConsistentBucketIdentifier identifier = new ConsistentBucketIdentifier(metadata);
        childrenNodes = new ArrayList<>();
        childrenNodes.add(
                new ConsistentHashingNode(
                        metadata.getNodes().get(0).getValue(), "d1", ConsistentHashingNode.NodeTag.NORMAL));
        metadata.setChildrenNodes(childrenNodes);
        boolean isException = false;
        try {
            identifier = new ConsistentBucketIdentifier(metadata);
        } catch (Exception e) {
            isException = true;
        }
        assertEquals(true, isException);
    }

    @Test
    public void testGeneratePartitionToFileIdPfxIdxMap() {
        Map<String, ConsistentBucketIdentifier> partitionToIdentifier = new HashMap<>();

        for (int i = 0; i < 8; i++) {
            String partitionPath = "partition" + i;
            HoodieConsistentHashingMetadata metadata =
                    new HoodieConsistentHashingMetadata(partitionPath, 8);
            ConsistentBucketIdentifier identifier = new ConsistentBucketIdentifier(metadata);
            partitionToIdentifier.put(partitionPath, identifier);
        }

        Map<String, Map<String, Integer>> partitionToFileIdPfxIdxMap =
                ConsistentBucketIndexUtils.generatePartitionToFileIdPfxIdxMap(partitionToIdentifier);

        for (int i = 0; i < 8; i++) {
            String partitionPath = "partition" + i;
            HoodieConsistentHashingMetadata metadata =
                    new HoodieConsistentHashingMetadata(partitionPath, 8);
            ConsistentBucketIdentifier identifier = partitionToIdentifier.get(partitionPath);
            Map<String, Integer> fileIdPfxToIdx = partitionToFileIdPfxIdxMap.get(partitionPath);

            assertEquals(8, fileIdPfxToIdx.size());

            int startIdx = 0;
            int endIdx = 8 * 8;
            // Check all bucket numbers are in range
            assertTrue(fileIdPfxToIdx.values().stream().allMatch(id -> id >= startIdx && id < endIdx));

            // Check all fileIdPrefix generated are valid file id
            assertTrue(
                    fileIdPfxToIdx.keySet().stream()
                            .map(fileIdPrefix -> FSUtils.createNewFileId(fileIdPrefix, 0))
                            .allMatch(fileId -> identifier.getBucketByFileId(fileId) != null));
        }

        // Check We have 64 distinct buckets
        assertEquals(
                64,
                partitionToFileIdPfxIdxMap.values().stream()
                        .flatMap(fileIdPfxToIdx -> fileIdPfxToIdx.values().stream())
                        .distinct()
                        .count());
    }
}
