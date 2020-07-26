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
package org.apache.iotdb.cluster.rpc.raft.response.querymetric;

import java.util.Map;
import org.apache.iotdb.cluster.rpc.raft.response.BasicResponse;

public class QueryJobNumResponse extends BasicResponse {

  private Map<String, Integer> value;

  private QueryJobNumResponse(String groupId, boolean redirected, String leaderStr,
      String errorMsg) {
    super(groupId, redirected, leaderStr, errorMsg);
  }

  public static QueryJobNumResponse createSuccessResponse(String groupId, Map<String, Integer> value) {
    QueryJobNumResponse response = new QueryJobNumResponse(groupId, false, null,
        null);
    response.value = value;
    return response;
  }

  public static QueryJobNumResponse createErrorResponse(String groupId, String errorMsg) {
    return new QueryJobNumResponse(groupId, false, null, errorMsg);
  }

  public Map<String, Integer> getValue() {
    return value;
  }
}
