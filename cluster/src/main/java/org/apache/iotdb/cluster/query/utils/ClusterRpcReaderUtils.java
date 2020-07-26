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
package org.apache.iotdb.cluster.query.utils;

import com.alipay.sofa.jraft.entity.PeerId;
import java.util.List;
import org.apache.iotdb.cluster.config.ClusterDescriptor;
import org.apache.iotdb.cluster.entity.Server;
import org.apache.iotdb.cluster.exception.RaftConnectionException;
import org.apache.iotdb.cluster.qp.task.DataQueryTask;
import org.apache.iotdb.cluster.qp.task.QPTask.TaskState;
import org.apache.iotdb.cluster.query.manager.coordinatornode.ClusterRpcSingleQueryManager;
import org.apache.iotdb.cluster.rpc.raft.impl.RaftNodeAsClientManager;
import org.apache.iotdb.cluster.rpc.raft.request.BasicRequest;
import org.apache.iotdb.cluster.rpc.raft.response.BasicResponse;
import org.apache.iotdb.cluster.utils.RaftUtils;
import org.apache.iotdb.cluster.utils.hash.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utils for cluster reader which needs to acquire data from remote query node.
 */
public class ClusterRpcReaderUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClusterRpcReaderUtils.class);

  /**
   * Count limit to redo a task
   */
  private static final int TASK_MAX_RETRY = ClusterDescriptor.getInstance().getConfig()
      .getQpTaskRedoCount();

  private ClusterRpcReaderUtils() {
  }

  /**
   * Create cluster series reader
   */
  public static BasicResponse createClusterSeriesReader(String groupId, BasicRequest request,
      ClusterRpcSingleQueryManager manager)
      throws RaftConnectionException {

    List<PeerId> peerIdList = RaftUtils
        .getPeerIDList(groupId, Server.getInstance(), Router.getInstance());
    int randomPeerIndex = RaftUtils.getRandomInt(peerIdList.size());
    BasicResponse response;
    for (int i = 0; i < peerIdList.size(); i++) {
      PeerId peerId = peerIdList.get((i + randomPeerIndex) % peerIdList.size());
      try {
        response = handleQueryRequest(request, peerId, 0);
        manager.setQueryNode(groupId, peerId);
        LOGGER.debug("Init series reader in Node<{}> of group<{}> success.", peerId, groupId);
        return response;
      } catch (RaftConnectionException e) {
        LOGGER.debug("Can not init series reader in Node<{}> of group<{}>", peerId, groupId, e);
      }
    }
    throw new RaftConnectionException(
        String.format("Can not init series reader in all nodes of group<%s>, please check cluster status.", groupId));
  }

  /**
   * Send query request to remote node and return response
   *
   * @param request query request
   * @param peerId target remote query node
   * @param taskRetryNum retry num of the request
   * @return Response from remote query node
   */
  public static BasicResponse handleQueryRequest(BasicRequest request, PeerId peerId,
      int taskRetryNum)
      throws RaftConnectionException {
    if (taskRetryNum > TASK_MAX_RETRY) {
      throw new RaftConnectionException(
          String.format("Query request retries reach the upper bound %s",
              TASK_MAX_RETRY));
    }
    DataQueryTask dataQueryTask = new DataQueryTask(true, request);
    dataQueryTask.setTargetNode(peerId);
    RaftNodeAsClientManager.getInstance().produceQPTask(dataQueryTask);
    try {
      dataQueryTask.await();
    } catch (InterruptedException e) {
      throw new RaftConnectionException(
          String.format("Can not connect to remote node {%s} for query", peerId));
    }
    if (dataQueryTask.getTaskState() == TaskState.RAFT_CONNECTION_EXCEPTION) {
      throw new RaftConnectionException(
          String.format("Can not connect to remote node {%s} for query", peerId));
    } else if (dataQueryTask.getTaskState() == TaskState.FINISH) {
      return dataQueryTask.getResponse();
    } else {
      return handleQueryRequest(request, peerId, taskRetryNum + 1);
    }
  }
}
