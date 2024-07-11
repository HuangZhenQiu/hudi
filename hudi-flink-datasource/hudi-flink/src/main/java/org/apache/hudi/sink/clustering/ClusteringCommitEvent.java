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

package org.apache.hudi.sink.clustering;

import java.io.Serializable;
import java.util.List;
import org.apache.hudi.client.WriteStatus;

/** Represents a commit event from the clustering task {@link ClusteringOperator}. */
public class ClusteringCommitEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The clustering commit instant time. */
    private String instant;
    /** The file IDs. */
    private String fileIds;
    /** The write statuses. */
    private List<WriteStatus> writeStatuses;
    /** The clustering task identifier. */
    private int taskID;

    public ClusteringCommitEvent() {}

    public ClusteringCommitEvent(String instant, String fileIds, int taskID) {
        this(instant, fileIds, null, taskID);
    }

    public ClusteringCommitEvent(
            String instant, String filedIds, List<WriteStatus> writeStatuses, int taskID) {
        this.instant = instant;
        this.fileIds = filedIds;
        this.writeStatuses = writeStatuses;
        this.taskID = taskID;
    }

    public void setInstant(String instant) {
        this.instant = instant;
    }

    public void setWriteStatuses(List<WriteStatus> writeStatuses) {
        this.writeStatuses = writeStatuses;
    }

    public void setTaskID(int taskID) {
        this.taskID = taskID;
    }

    public String getInstant() {
        return instant;
    }

    public String getFileIds() {
        return fileIds;
    }

    public List<WriteStatus> getWriteStatuses() {
        return writeStatuses;
    }

    public int getTaskID() {
        return taskID;
    }

    public boolean isFailed() {
        return this.writeStatuses == null;
    }
}
