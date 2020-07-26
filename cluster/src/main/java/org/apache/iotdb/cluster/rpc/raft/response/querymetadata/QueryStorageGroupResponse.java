/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.cluster.rpc.raft.response.querymetadata;

import java.util.Set;
import org.apache.iotdb.cluster.rpc.raft.response.BasicResponse;

public class QueryStorageGroupResponse extends BasicResponse {

  private static final long serialVersionUID = 248840631619860233L;
  private Set<String> storageGroups;

  private QueryStorageGroupResponse(boolean success, String leaderStr, String errorMsg) {
    super(null, false, leaderStr, errorMsg);
    this.addResult(success);
  }

  public static QueryStorageGroupResponse createSuccessResponse(Set<String> storageGroups) {
    QueryStorageGroupResponse response = new QueryStorageGroupResponse(true, null, null);
    response.setStorageGroups(storageGroups);
    return response;
  }

  public static QueryStorageGroupResponse createErrorResponse(String errorMsg) {
    return new QueryStorageGroupResponse(false, null, errorMsg);
  }

  public Set<String> getStorageGroups() {
    return storageGroups;
  }

  public void setStorageGroups(Set<String> storageGroups) {
    this.storageGroups = storageGroups;
  }
}
