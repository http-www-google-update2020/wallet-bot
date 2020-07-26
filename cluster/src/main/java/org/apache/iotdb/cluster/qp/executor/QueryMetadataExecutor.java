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
package org.apache.iotdb.cluster.qp.executor;

import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.closure.ReadIndexClosure;
import com.alipay.sofa.jraft.entity.PeerId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.iotdb.cluster.config.ClusterConfig;
import org.apache.iotdb.cluster.config.ClusterConstant;
import org.apache.iotdb.cluster.entity.raft.MetadataRaftHolder;
import org.apache.iotdb.cluster.entity.raft.RaftService;
import org.apache.iotdb.cluster.exception.RaftConnectionException;
import org.apache.iotdb.cluster.qp.task.QPTask.TaskState;
import org.apache.iotdb.cluster.qp.task.SingleQPTask;
import org.apache.iotdb.cluster.rpc.raft.request.querymetadata.QueryMetadataInStringRequest;
import org.apache.iotdb.cluster.rpc.raft.request.querymetadata.QueryMetadataRequest;
import org.apache.iotdb.cluster.rpc.raft.request.querymetadata.QueryPathsRequest;
import org.apache.iotdb.cluster.rpc.raft.request.querymetadata.QuerySeriesTypeRequest;
import org.apache.iotdb.cluster.rpc.raft.request.querymetadata.QueryStorageGroupRequest;
import org.apache.iotdb.cluster.rpc.raft.request.querymetadata.QueryTimeSeriesRequest;
import org.apache.iotdb.cluster.rpc.raft.response.BasicResponse;
import org.apache.iotdb.cluster.rpc.raft.response.querymetadata.QueryMetadataInStringResponse;
import org.apache.iotdb.cluster.rpc.raft.response.querymetadata.QueryMetadataResponse;
import org.apache.iotdb.cluster.rpc.raft.response.querymetadata.QueryPathsResponse;
import org.apache.iotdb.cluster.rpc.raft.response.querymetadata.QuerySeriesTypeResponse;
import org.apache.iotdb.cluster.rpc.raft.response.querymetadata.QueryStorageGroupResponse;
import org.apache.iotdb.cluster.rpc.raft.response.querymetadata.QueryTimeSeriesResponse;
import org.apache.iotdb.cluster.utils.QPExecutorUtils;
import org.apache.iotdb.cluster.utils.RaftUtils;
import org.apache.iotdb.db.exception.PathErrorException;
import org.apache.iotdb.db.exception.ProcessorException;
import org.apache.iotdb.db.metadata.MManager;
import org.apache.iotdb.db.metadata.Metadata;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle < show timeseries <path> > logic
 */
public class QueryMetadataExecutor extends AbstractQPExecutor {

  private static final Logger LOGGER = LoggerFactory.getLogger(QueryMetadataExecutor.class);
  private static final String DOUB_SEPARATOR = "\\.";
  private static final char SINGLE_SEPARATOR = '.';
  private static final String RAFT_CONNECTION_ERROR = "Raft connection occurs error.";

  public QueryMetadataExecutor() {
    super();
  }

  public Set<String> processStorageGroupQuery() throws InterruptedException {
    return queryStorageGroupLocally();
  }

  /**
   * Handle show timeseries <path> statement
   */
  public List<List<String>> processTimeSeriesQuery(String path)
      throws InterruptedException, PathErrorException, ProcessorException {
    List<List<String>> res = new ArrayList<>();
    List<String> storageGroupList = mManager.getAllFileNamesByPath(path);
    if (storageGroupList.isEmpty()) {
      return new ArrayList<>();
    } else {
      Map<String, Set<String>> groupIdSGMap = QPExecutorUtils.classifySGByGroupId(storageGroupList);
      for (Entry<String, Set<String>> entry : groupIdSGMap.entrySet()) {
        List<String> paths = getSubQueryPaths(entry.getValue(), path);
        String groupId = entry.getKey();
        handleTimseriesQuery(groupId, paths, res);
      }
    }
    return res;
  }

  /**
   * Get all query path in storage group relatively to query path
   */
  private List<String> getSubQueryPaths(Set<String> stoageGroupList, String queryPath) {
    List<String> paths = new ArrayList<>();
    for (String storageGroup : stoageGroupList) {
      if (storageGroup.length() >= queryPath.length()) {
        paths.add(storageGroup);
      } else {
        StringBuilder path = new StringBuilder();
        String[] storageGroupNodes = storageGroup.split(DOUB_SEPARATOR);
        String[] queryPathNodes = queryPath.split(DOUB_SEPARATOR);
        for (int i = 0; i < queryPathNodes.length; i++) {
          if (i >= storageGroupNodes.length) {
            path.append(queryPathNodes[i]).append(SINGLE_SEPARATOR);
          } else {
            path.append(storageGroupNodes[i]).append(SINGLE_SEPARATOR);
          }
        }
        paths.add(path.deleteCharAt(path.length() - 1).toString());
      }
    }
    return paths;
  }

  /**
   * Handle query timeseries in one data group
   *
   * @param groupId data group id
   */
  private void handleTimseriesQuery(String groupId, List<String> pathList, List<List<String>> res)
      throws ProcessorException, InterruptedException {
    QueryTimeSeriesRequest request = new QueryTimeSeriesRequest(groupId,
        getReadMetadataConsistencyLevel(), pathList);
    SingleQPTask task = new SingleQPTask(false, request);

    LOGGER.debug("Execute show timeseries {} statement for group {}.", pathList, groupId);
    PeerId holder;
    /** Check if the plan can be executed locally. **/
    if (QPExecutorUtils.canHandleQueryByGroupId(groupId)) {
      LOGGER.debug(
          "Execute show timeseries {} statement locally for group {} by sending request to local node.",
          pathList, groupId);
      holder = this.server.getServerId();
    } else {
      holder = RaftUtils.getPeerIDInOrder(groupId);
    }
    task.setTargetNode(holder);
    try {
      LOGGER.debug("Send show timeseries {} task for group {} to node {}.", pathList, groupId,
          holder);
      res.addAll(queryTimeSeries(task));
    } catch (RaftConnectionException e) {
      boolean success = false;
      while (!success) {
        PeerId nextNode = null;
        try {
          nextNode = RaftUtils.getPeerIDInOrder(groupId);
          if (holder.equals(nextNode)) {
            break;
          }
          LOGGER.debug(
              "Previous task fail, then send show timeseries {} task for group {} to node {}.",
              pathList, groupId, nextNode);
          task.resetTask();
          task.setTargetNode(holder);
          task.setTaskState(TaskState.INITIAL);
          res.addAll(queryTimeSeries(task));
          LOGGER
              .debug("Show timeseries {} task for group {} to node {} succeed.", pathList, groupId,
                  nextNode);
          success = true;
        } catch (RaftConnectionException e1) {
          LOGGER.debug("Show timeseries {} task for group {} to node {} fail.", pathList, groupId,
              nextNode);
        }
      }
      LOGGER.debug("The final result for show timeseries {} task is {}", pathList, success);
      if (!success) {
        throw new ProcessorException(RAFT_CONNECTION_ERROR, e);
      }
    }
  }

  public String processMetadataInStringQuery()
      throws InterruptedException, ProcessorException {
    Set<String> groupIdSet = router.getAllGroupId();

    List<String> metadataList = new ArrayList<>(groupIdSet.size());
    List<SingleQPTask> taskList = new ArrayList<>();
    for (String groupId : groupIdSet) {
      QueryMetadataInStringRequest request = new QueryMetadataInStringRequest(groupId,
          getReadMetadataConsistencyLevel());
      SingleQPTask task = new SingleQPTask(false, request);
      taskList.add(task);

      LOGGER.debug("Execute show metadata in string statement for group {}.", groupId);
      PeerId holder;
      /** Check if the plan can be executed locally. **/
      if (QPExecutorUtils.canHandleQueryByGroupId(groupId)) {
        LOGGER.debug(
            "Execute show metadata in string statement locally for group {} by sending request to local node.",
            groupId);
        holder = this.server.getServerId();
      } else {
        holder = RaftUtils.getPeerIDInOrder(groupId);
      }
      task.setTargetNode(holder);
      try {
        LOGGER.debug("Send show metadata in string task for group {} to node {}.", groupId, holder);
        asyncSendNonQuerySingleTask(task, 0);
      } catch (RaftConnectionException e) {
        boolean success = false;
        while (!success) {
          PeerId nextNode = null;
          try {
            nextNode = RaftUtils.getPeerIDInOrder(groupId);
            if (holder.equals(nextNode)) {
              break;
            }
            LOGGER.debug(
                "Previous task fail, then send show metadata in string task for group {} to node {}.",
                groupId, nextNode);
            task.resetTask();
            task.setTargetNode(nextNode);
            task.setTaskState(TaskState.INITIAL);
            asyncSendNonQuerySingleTask(task, 0);
            LOGGER.debug("Show metadata in string task for group {} to node {} succeed.", groupId,
                nextNode);
            success = true;
          } catch (RaftConnectionException e1) {
            LOGGER.debug("Show metadata in string task for group {} to node {} fail.", groupId,
                nextNode);
          }
        }
        LOGGER.debug("The final result for show metadata in string task is {}", success);
        if (!success) {
          throw new ProcessorException(RAFT_CONNECTION_ERROR, e);
        }
      }
    }
    for (int i = 0; i < taskList.size(); i++) {
      SingleQPTask task = taskList.get(i);
      task.await();
      BasicResponse response = task.getResponse();
      if (response == null || !response.isSuccess()) {
        String errorMessage = "response is null";
        if (response != null && response.getErrorMsg() != null) {
          errorMessage = response.getErrorMsg();
        }
        throw new ProcessorException(
            "Execute show metadata in string statement fail because " + errorMessage);
      }
      metadataList.add(((QueryMetadataInStringResponse) response).getMetadata());
    }
    return combineMetadataInStringList(metadataList);
  }

  public Metadata processMetadataQuery()
      throws InterruptedException, ProcessorException {
    Set<String> groupIdSet = router.getAllGroupId();

    Metadata[] metadatas = new Metadata[groupIdSet.size()];
    List<SingleQPTask> taskList = new ArrayList<>();
    for (String groupId : groupIdSet) {
      QueryMetadataRequest request = new QueryMetadataRequest(groupId,
          getReadMetadataConsistencyLevel());
      SingleQPTask task = new SingleQPTask(false, request);
      taskList.add(task);

      LOGGER.debug("Execute query metadata statement for group {}.", groupId);
      PeerId holder;
      /** Check if the plan can be executed locally. **/
      if (QPExecutorUtils.canHandleQueryByGroupId(groupId)) {
        LOGGER.debug(
            "Execute query metadata statement locally for group {} by sending request to local node.",
            groupId);
        holder = this.server.getServerId();
      } else {
        holder = RaftUtils.getPeerIDInOrder(groupId);
      }
      task.setTargetNode(holder);
      try {
        LOGGER.debug("Send query metadata task for group {} to node {}.", groupId, holder);
        asyncSendNonQuerySingleTask(task, 0);
      } catch (RaftConnectionException e) {
        boolean success = false;
        while (!success) {
          PeerId nextNode = null;
          try {
            nextNode = RaftUtils.getPeerIDInOrder(groupId);
            if (holder.equals(nextNode)) {
              break;
            }
            LOGGER
                .debug("Previous task fail, then send query metadata task for group {} to node {}.",
                    groupId, nextNode);
            task.resetTask();
            task.setTargetNode(nextNode);
            task.setTaskState(TaskState.INITIAL);
            asyncSendNonQuerySingleTask(task, 0);
            LOGGER.debug("Query metadata task for group {} to node {} succeed.", groupId, nextNode);
            success = true;
          } catch (RaftConnectionException e1) {
            LOGGER.debug("Query metadata task for group {} to node {} fail.", groupId, nextNode);
            continue;
          }
        }
        LOGGER.debug("The final result for query metadata task is {}", success);
        if (!success) {
          throw new ProcessorException(RAFT_CONNECTION_ERROR, e);
        }
      }
    }
    for (int i = 0; i < taskList.size(); i++) {
      SingleQPTask task = taskList.get(i);
      task.await();
      BasicResponse response = task.getResponse();
      if (response == null || !response.isSuccess()) {
        String errorMessage = "response is null";
        if (response != null && response.getErrorMsg() != null) {
          errorMessage = response.getErrorMsg();
        }
        throw new ProcessorException(
            "Execute query metadata statement fail because " + errorMessage);
      }
      metadatas[i] = ((QueryMetadataResponse) response).getMetadata();
    }
    return Metadata.combineMetadatas(metadatas);
  }

  public TSDataType processSeriesTypeQuery(String path)
      throws InterruptedException, ProcessorException, PathErrorException {
    TSDataType dataType = null;
    List<String> storageGroupList = mManager.getAllFileNamesByPath(path);
    if (storageGroupList.size() != 1) {
      throw new PathErrorException("path " + path + " is not valid.");
    } else {
      String groupId = router.getGroupIdBySG(storageGroupList.get(0));
      QuerySeriesTypeRequest request = new QuerySeriesTypeRequest(groupId,
          getReadMetadataConsistencyLevel(), path);
      SingleQPTask task = new SingleQPTask(false, request);

      LOGGER.debug("Execute get series type for {} statement for group {}.", path, groupId);
      PeerId holder;
      /** Check if the plan can be executed locally. **/
      if (QPExecutorUtils.canHandleQueryByGroupId(groupId)) {
        LOGGER.debug(
            "Execute get series type for {} statement locally for group {} by sending request to local node.",
            path, groupId);
        holder = this.server.getServerId();
      } else {
        holder = RaftUtils.getPeerIDInOrder(groupId);
      }
      task.setTargetNode(holder);
      try {
        LOGGER.debug("Send get series type for {} task for group {} to node {}.", path, groupId,
            holder);
        dataType = querySeriesType(task);
      } catch (RaftConnectionException e) {
        boolean success = false;
        while (!success) {
          PeerId nextNode = null;
          try {
            nextNode = RaftUtils.getPeerIDInOrder(groupId);
            if (holder.equals(nextNode)) {
              break;
            }
            LOGGER.debug(
                "Previous task fail, then send get series type for {} task for group {} to node {}.",
                path, groupId, nextNode);
            task.resetTask();
            task.setTargetNode(nextNode);
            task.setTaskState(TaskState.INITIAL);
            dataType = querySeriesType(task);
            LOGGER.debug("Get series type for {} task for group {} to node {} succeed.", path,
                groupId, nextNode);
            success = true;
          } catch (RaftConnectionException e1) {
            LOGGER.debug("Get series type for {} task for group {} to node {} fail.", path, groupId,
                nextNode);
            continue;
          }
        }
        LOGGER.debug("The final result for get series type for {} task is {}", path, success);
        if (!success) {
          throw new ProcessorException(RAFT_CONNECTION_ERROR, e);
        }
      }
    }
    return dataType;
  }

  /**
   * Handle show timeseries <path> statement
   */
  public List<String> processPathsQuery(String path)
      throws InterruptedException, PathErrorException, ProcessorException {
    List<String> res = new ArrayList<>();
    List<String> storageGroupList = mManager.getAllFileNamesByPath(path);
    if (storageGroupList.isEmpty()) {
      return new ArrayList<>();
    } else {
      Map<String, Set<String>> groupIdSGMap = QPExecutorUtils.classifySGByGroupId(storageGroupList);
      for (Entry<String, Set<String>> entry : groupIdSGMap.entrySet()) {
        List<String> paths = getSubQueryPaths(entry.getValue(), path);
        String groupId = entry.getKey();
        handlePathsQuery(groupId, paths, res);
      }
    }
    return res;
  }

  /**
   * Handle query timeseries in one data group
   *
   * @param groupId data group id
   */
  private void handlePathsQuery(String groupId, List<String> pathList, List<String> res)
      throws ProcessorException, InterruptedException {
    QueryPathsRequest request = new QueryPathsRequest(groupId,
        getReadMetadataConsistencyLevel(), pathList);
    SingleQPTask task = new SingleQPTask(false, request);

    LOGGER.debug("Execute get paths for {} statement for group {}.", pathList, groupId);
    PeerId holder;
    /** Check if the plan can be executed locally. **/
    if (QPExecutorUtils.canHandleQueryByGroupId(groupId)) {
      LOGGER.debug(
          "Execute get paths for {} statement locally for group {} by sending request to local node.",
          pathList, groupId);
      holder = this.server.getServerId();
    } else {
      holder = RaftUtils.getPeerIDInOrder(groupId);
    }
    task.setTargetNode(holder);
    try {
      LOGGER
          .debug("Send get paths for {} task for group {} to node {}.", pathList, groupId, holder);
      res.addAll(queryPaths(task));
    } catch (RaftConnectionException e) {
      boolean success = false;
      while (!success) {
        PeerId nextNode = null;
        try {
          nextNode = RaftUtils.getPeerIDInOrder(groupId);
          if (holder.equals(nextNode)) {
            break;
          }
          LOGGER
              .debug("Previous task fail, then send get paths for {} task for group {} to node {}.",
                  pathList, groupId, nextNode);
          task.setTargetNode(nextNode);
          task.resetTask();
          task.setTaskState(TaskState.INITIAL);
          res.addAll(queryPaths(task));
          LOGGER.debug("Get paths for {} task for group {} to node {} succeed.", pathList, groupId,
              nextNode);
          success = true;
        } catch (RaftConnectionException e1) {
          LOGGER.debug("Get paths for {} task for group {} to node {} fail.", pathList, groupId,
              nextNode);
        }
      }
      LOGGER.debug("The final result for get paths for {} task is {}", pathList, success);
      if (!success) {
        throw new ProcessorException(RAFT_CONNECTION_ERROR, e);
      }
    }
  }

  private List<List<String>> queryTimeSeries(SingleQPTask task)
      throws InterruptedException, RaftConnectionException {
    BasicResponse response = syncHandleNonQuerySingleTaskGetRes(task, 0);
    return response == null ? new ArrayList<>()
        : ((QueryTimeSeriesResponse) response).getTimeSeries();
  }

  private TSDataType querySeriesType(SingleQPTask task)
      throws InterruptedException, RaftConnectionException {
    BasicResponse response = syncHandleNonQuerySingleTaskGetRes(task, 0);
    return response == null ? null
        : ((QuerySeriesTypeResponse) response).getDataType();
  }

  /**
   * Handle "show storage group" statement locally
   *
   * @return Set of storage group name
   */
  private Set<String> queryStorageGroupLocally() throws InterruptedException {
    final byte[] reqContext = RaftUtils.createRaftRequestContext();
    QueryStorageGroupRequest request = new QueryStorageGroupRequest(
        ClusterConfig.METADATA_GROUP_ID, getReadMetadataConsistencyLevel());
    SingleQPTask task = new SingleQPTask(false, request);
    MetadataRaftHolder metadataHolder = (MetadataRaftHolder) server.getMetadataHolder();
    if (getReadMetadataConsistencyLevel() == ClusterConstant.WEAK_CONSISTENCY_LEVEL) {
      QueryStorageGroupResponse response;
      response = QueryStorageGroupResponse
          .createSuccessResponse(metadataHolder.getFsm().getAllStorageGroups());
      task.receive(response);
    } else {
      ((RaftService) metadataHolder.getService()).getNode()
          .readIndex(reqContext, new ReadIndexClosure() {

            @Override
            public void run(Status status, long index, byte[] reqCtx) {
              QueryStorageGroupResponse response;
              if (status.isOk()) {
                response = QueryStorageGroupResponse
                    .createSuccessResponse(metadataHolder.getFsm().getAllStorageGroups());
              } else {
                response = QueryStorageGroupResponse.createErrorResponse(status.getErrorMsg());
              }
              task.receive(response);
            }
          });
    }
    task.await();
    return ((QueryStorageGroupResponse) task.getResponse()).getStorageGroups();
  }

  private List<String> queryPaths(SingleQPTask task)
      throws InterruptedException, RaftConnectionException {
    BasicResponse response = syncHandleNonQuerySingleTaskGetRes(task, 0);
    return response == null ? new ArrayList<>()
        : ((QueryPathsResponse) response).getPaths();
  }

  /**
   * Combine multiple metadata in String format into single String
   *
   * @return single String of all metadata
   */
  private String combineMetadataInStringList(List<String> metadataList) {
    return MManager.combineMetadataInStrings(metadataList.toArray(new String[metadataList.size()]));
  }
}
