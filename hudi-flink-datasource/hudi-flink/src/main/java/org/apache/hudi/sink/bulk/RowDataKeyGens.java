/*
  * Licensed to the Apache Software Foundation (ASF) under one
  * or more contributor license agreements.  See the NOTICE file
  * distributed with this work for additional information
  * regarding copyright ownership.  The ASF licenses this file
  * to you under the Apache License, Version 2.0 (the
  * "License"); you may not use this file except in compliance
  * with the License.  You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

package org.apache.hudi.sink.bulk;

import java.util.List;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.types.logical.RowType;
import org.apache.hudi.configuration.FlinkOptions;

/** Factory class for all kinds of {@link RowDataKeyGen}. */
public class RowDataKeyGens {

    /** Creates a {@link RowDataKeyGen} with given configuration. */
    public static RowDataKeyGen instance(
            Configuration conf, RowType rowType, int taskId, String instantTime) {
        String recordKeys = conf.getString(FlinkOptions.RECORD_KEY_FIELD);
        if (hasRecordKey(recordKeys, rowType.getFieldNames())) {
            return RowDataKeyGen.instance(conf, rowType);
        } else {
            return AutoRowDataKeyGen.instance(conf, rowType, taskId, instantTime);
        }
    }

    /** Checks whether user provides any record key. */
    private static boolean hasRecordKey(String recordKeys, List<String> fieldNames) {
        return recordKeys.split(",").length != 1 || fieldNames.contains(recordKeys);
    }
}
