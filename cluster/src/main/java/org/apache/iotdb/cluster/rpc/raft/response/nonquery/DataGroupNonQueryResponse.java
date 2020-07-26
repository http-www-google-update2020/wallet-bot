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
package org.apache.iotdb.cluster.rpc.raft.response.nonquery;

import java.util.ArrayList;
import java.util.List;
import org.apache.iotdb.cluster.rpc.raft.response.BasicResponse;

/**
 * Handle response from data group leader
 */
public class DataGroupNonQueryResponse extends BasicResponse {

  private static final long serialVersionUID = -8288044965888956717L;

  private List<String> errorMsgList;

  private DataGroupNonQueryResponse(String groupId, boolean redirected, String leaderStr,
      String errorMsg) {
    super(groupId, redirected, leaderStr, errorMsg);
    errorMsgList = new ArrayList<>();
  }

  public static DataGroupNonQueryResponse createRedirectedResponse(String groupId, String leaderStr) {
    return new DataGroupNonQueryResponse(groupId, true, leaderStr, null);
  }

  public static DataGroupNonQueryResponse createEmptyResponse(String groupId) {
    return new DataGroupNonQueryResponse(groupId, false, null, null);
  }

  public static DataGroupNonQueryResponse createErrorResponse(String groupId, String errorMsg) {
    return new DataGroupNonQueryResponse(groupId, false, null, errorMsg);
  }

  public List<String> getErrorMsgList() {
    return errorMsgList;
  }

  public void addErrorMsg(String errorMsg) {
    this.errorMsgList.add(errorMsg);
  }
}
