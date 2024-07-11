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

import org.apache.hudi.common.config.ConfigProperty;
import org.apache.hudi.common.config.HoodieConfig;
import org.apache.hudi.common.model.WriteOperationType;

/** Configs/params used for internal purposes. */
public class HoodieInternalConfig extends HoodieConfig {

    private static final long serialVersionUID = 0L;

    public static final String BULKINSERT_ARE_PARTITIONER_RECORDS_SORTED =
            "hoodie.bulkinsert.are.partitioner.records.sorted";
    public static final Boolean DEFAULT_BULKINSERT_ARE_PARTITIONER_RECORDS_SORTED = false;

    public static final ConfigProperty<String> BULKINSERT_INPUT_DATA_SCHEMA_DDL =
            ConfigProperty.key("hoodie.bulkinsert.schema.ddl")
                    .noDefaultValue()
                    .markAdvanced()
                    .withDocumentation("Schema set for row writer/bulk insert.");

    public static final ConfigProperty<String> BULKINSERT_OVERWRITE_OPERATION_TYPE =
            ConfigProperty.key("hoodie.bulkinsert.overwrite.operation.type")
                    .noDefaultValue()
                    .markAdvanced()
                    .withValidValues(
                            WriteOperationType.INSERT_OVERWRITE_TABLE.value(),
                            WriteOperationType.INSERT_OVERWRITE.value())
                    .withDocumentation(
                            "For SQL operations, if enables bulk_insert operation, "
                                    + "this configure will take effect to decide overwrite whole table or partitions specified");

    public static final ConfigProperty<String> STATIC_OVERWRITE_PARTITION_PATHS =
            ConfigProperty.key("hoodie.static.overwrite.partition.paths")
                    .defaultValue("")
                    .markAdvanced()
                    .withDocumentation(
                            "Inner configure to pass static partition paths to executors for SQL operations.");

    /**
      * Returns if partition records are sorted or not.
      *
      * @param propertyValue value for property BULKINSERT_ARE_PARTITIONER_RECORDS_SORTED.
      * @return the property value.
      */
    public static Boolean getBulkInsertIsPartitionRecordsSorted(String propertyValue) {
        return propertyValue != null
                ? Boolean.parseBoolean(propertyValue)
                : DEFAULT_BULKINSERT_ARE_PARTITIONER_RECORDS_SORTED;
    }
}
