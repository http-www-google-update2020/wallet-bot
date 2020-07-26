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
import com.alipay.sofa.jraft.entity.PeerId;
import java.io.IOException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.iotdb.cluster.config.ClusterConfig;
import org.apache.iotdb.cluster.entity.raft.DataPartitionRaftHolder;
import org.apache.iotdb.cluster.entity.raft.MetadataRaftHolder;
import org.apache.iotdb.cluster.entity.raft.RaftService;
import org.apache.iotdb.cluster.exception.RaftConnectionException;
import org.apache.iotdb.cluster.qp.task.BatchQPTask;
import org.apache.iotdb.cluster.qp.task.QPTask;
import org.apache.iotdb.cluster.qp.task.QPTask.TaskState;
import org.apache.iotdb.cluster.qp.task.SingleQPTask;
import org.apache.iotdb.cluster.rpc.raft.request.BasicRequest;
import org.apache.iotdb.cluster.rpc.raft.request.nonquery.DataGroupNonQueryRequest;
import org.apache.iotdb.cluster.rpc.raft.request.nonquery.MetaGroupNonQueryRequest;
import org.apache.iotdb.cluster.rpc.raft.response.BasicResponse;
import org.apache.iotdb.cluster.rpc.raft.response.nonquery.DataGroupNonQueryResponse;
import org.apache.iotdb.cluster.rpc.raft.response.nonquery.MetaGroupNonQueryResponse;
import org.apache.iotdb.cluster.service.TSServiceClusterImpl.BatchResult;
import org.apache.iotdb.cluster.utils.QPExecutorUtils;
import org.apache.iotdb.cluster.utils.RaftUtils;
import org.apache.iotdb.db.exception.PathErrorException;
import org.apache.iotdb.db.exception.ProcessorException;
import org.apache.iotdb.db.qp.logical.sys.MetadataOperator;
import org.apache.iotdb.db.qp.physical.PhysicalPlan;
import org.apache.iotdb.db.qp.physical.crud.DeletePlan;
import org.apache.iotdb.db.qp.physical.crud.InsertPlan;
import org.apache.iotdb.db.qp.physical.crud.UpdatePlan;
import org.apache.iotdb.db.qp.physical.sys.MetadataPlan;
import org.apache.iotdb.tsfile.read.common.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle distributed non-query logic
 */
public class NonQueryExecutor extends AbstractQPExecutor {

  private static final Logger LOGGER = LoggerFactory.getLogger(NonQueryExecutor.class);

  private static final String OPERATION_NOT_SUPPORTED = "Operation %s does not support";

  /**
   * When executing Metadata Plan, it's necessary to do empty-read in single non query request or do
   * the first empty-read in batch non query request
   */
  private boolean emptyTaskEnable = false;

  public NonQueryExecutor() {
    super();
  }

  /**
   * Execute single non query request.
   */
  public boolean processNonQuery(PhysicalPlan plan) throws ProcessorException {
    try {
      emptyTaskEnable = true;
      String groupId = getGroupIdFromPhysicalPlan(plan);
      return handleNonQueryRequest(groupId, plan);
    } catch (RaftConnectionException e) {
      LOGGER.error(e.getMessage());
      throw new ProcessorException("Raft connection occurs error.", e);
    } catch (InterruptedException | PathErrorException | IOException e) {
      throw new ProcessorException(e);
    }
  }

  /**
   * Execute batch statement by physical plans and update results.
   *
   * @param physicalPlans List of physical plan
   * @param batchResult batch result
   */
  public void processBatch(PhysicalPlan[] physicalPlans, BatchResult batchResult)
      throws InterruptedException, ProcessorException {

    Status nullReadTaskStatus = Status.OK();
    RaftUtils.handleNullReadToMetaGroup(nullReadTaskStatus);
    if (!nullReadTaskStatus.isOk()) {
      throw new ProcessorException("Null read while processing batch failed");
    }
    emptyTaskEnable = false;

    /* 1. Classify physical plans by group id */
    Map<String, List<PhysicalPlan>> physicalPlansMap = new HashMap<>();
    Map<String, List<Integer>> planIndexMap = new HashMap<>();
    classifyPhysicalPlanByGroupId(physicalPlans, batchResult, physicalPlansMap, planIndexMap);

    /* 2. Construct Multiple Data Group Requests */
    Map<String, SingleQPTask> subTaskMap = new HashMap<>();
    constructMultipleRequests(physicalPlansMap, planIndexMap, subTaskMap, batchResult);

    /* 3. Execute Multiple Sub Tasks */
    BatchQPTask task = new BatchQPTask(subTaskMap.size(), batchResult, subTaskMap, planIndexMap);
    currentTask.set(task);
    task.executeBy(this);
    task.await();
  }

  /**
   * Classify batch physical plan by groupId
   */
  private void classifyPhysicalPlanByGroupId(PhysicalPlan[] physicalPlans, BatchResult batchResult,
      Map<String, List<PhysicalPlan>> physicalPlansMap, Map<String, List<Integer>> planIndexMap) {

    int[] result = batchResult.getResultArray();
    for (int i = 0; i < result.length; i++) {
      /** Check if the request has failed. If it has failed, ignore it. **/
      if (result[i] != Statement.EXECUTE_FAILED) {
        PhysicalPlan plan = physicalPlans[i];
        try {
          String groupId = getGroupIdFromPhysicalPlan(plan);
          if (groupId.equals(ClusterConfig.METADATA_GROUP_ID)) {

            // this is for set storage group statement and role/user management statement.
            LOGGER.debug("Execute metadata group task");
            boolean executeResult = handleNonQueryRequest(groupId, plan);
            emptyTaskEnable = true;
            result[i] = executeResult ? Statement.SUCCESS_NO_INFO
                : Statement.EXECUTE_FAILED;
            batchResult.setAllSuccessful(executeResult);
          } else {
            physicalPlansMap.computeIfAbsent(groupId, l -> new ArrayList<>()).add(plan);
            planIndexMap.computeIfAbsent(groupId, l -> new ArrayList<>()).add(i);
          }
        } catch (PathErrorException | ProcessorException | IOException | RaftConnectionException | InterruptedException e) {
          result[i] = Statement.EXECUTE_FAILED;
          batchResult.setAllSuccessful(false);
          batchResult.addBatchErrorMessage(i, e.getMessage());
          LOGGER.error(e.getMessage());
        }
      }
    }
  }

  /**
   * Construct multiple data group requests
   */
  private void constructMultipleRequests(Map<String, List<PhysicalPlan>> physicalPlansMap,
      Map<String, List<Integer>> planIndexMap, Map<String, SingleQPTask> subTaskMap,
      BatchResult batchResult) {
    int[] result = batchResult.getResultArray();
    for (Entry<String, List<PhysicalPlan>> entry : physicalPlansMap.entrySet()) {
      String groupId = entry.getKey();
      SingleQPTask singleQPTask;
      BasicRequest request;
      try {
        LOGGER.debug("DATA_GROUP_ID Send batch size() : {}", entry.getValue().size());
        request = new DataGroupNonQueryRequest(groupId, entry.getValue());
        singleQPTask = new SingleQPTask(false, request);
        subTaskMap.put(groupId, singleQPTask);
      } catch (IOException e) {
        batchResult.setAllSuccessful(false);
        for (int index : planIndexMap.get(groupId)) {
          batchResult.addBatchErrorMessage(index, e.getMessage());
        }
        for (int index : planIndexMap.get(groupId)) {
          result[index] = Statement.EXECUTE_FAILED;
        }
      }
    }
  }

  /**
   * Get group id from physical plan
   */
  private String getGroupIdFromPhysicalPlan(PhysicalPlan plan)
      throws PathErrorException, ProcessorException {
    String storageGroup;
    String groupId;
    switch (plan.getOperatorType()) {
      case DELETE:
        storageGroup = getStorageGroupFromDeletePlan((DeletePlan) plan);
        groupId = router.getGroupIdBySG(storageGroup);
        break;
      case UPDATE:
        Path path = ((UpdatePlan) plan).getPath();
        storageGroup = QPExecutorUtils.getStroageGroupByDevice(path.getDevice());
        groupId = router.getGroupIdBySG(storageGroup);
        break;
      case INSERT:
        storageGroup = QPExecutorUtils.getStroageGroupByDevice(((InsertPlan) plan).getDeviceId());
        groupId = router.getGroupIdBySG(storageGroup);
        break;
      case CREATE_ROLE:
      case DELETE_ROLE:
      case CREATE_USER:
      case REVOKE_USER_ROLE:
      case REVOKE_ROLE_PRIVILEGE:
      case REVOKE_USER_PRIVILEGE:
      case GRANT_ROLE_PRIVILEGE:
      case GRANT_USER_PRIVILEGE:
      case GRANT_USER_ROLE:
      case MODIFY_PASSWORD:
      case DELETE_USER:
      case LIST_ROLE:
      case LIST_USER:
      case LIST_ROLE_PRIVILEGE:
      case LIST_ROLE_USERS:
      case LIST_USER_PRIVILEGE:
      case LIST_USER_ROLES:
        groupId = ClusterConfig.METADATA_GROUP_ID;
        break;
      case LOADDATA:
        throw new UnsupportedOperationException(
            String.format(OPERATION_NOT_SUPPORTED, plan.getOperatorType()));
      case DELETE_TIMESERIES:
      case CREATE_TIMESERIES:
      case SET_STORAGE_GROUP:
      case METADATA:
        if (emptyTaskEnable) {
          Status nullReadTaskStatus = Status.OK();
          RaftUtils.handleNullReadToMetaGroup(nullReadTaskStatus);
          if (!nullReadTaskStatus.isOk()) {
            throw new ProcessorException("Null read to metadata group failed");
          }
          emptyTaskEnable = false;
        }
        groupId = getGroupIdFromMetadataPlan((MetadataPlan) plan);
        break;
      case PROPERTY:
        throw new UnsupportedOperationException(
            String.format(OPERATION_NOT_SUPPORTED, plan.getOperatorType()));
      default:
        throw new UnsupportedOperationException(
            String.format(OPERATION_NOT_SUPPORTED, plan.getOperatorType()));
    }
    return groupId;
  }

  /**
   * Get storage group from delete plan
   */
  public String getStorageGroupFromDeletePlan(DeletePlan deletePlan)
      throws PathErrorException, ProcessorException {
    List<Path> paths = deletePlan.getPaths();
    Set<String> sgSet = new HashSet<>();
    for (Path path : paths) {
      List<String> storageGroupList = mManager.getAllFileNamesByPath(path.getFullPath());
      sgSet.addAll(storageGroupList);
      if (sgSet.size() > 1) {
        throw new ProcessorException(
            "Delete function in distributed iotdb only supports single storage group");
      }
    }
    List<String> sgList = new ArrayList<>(sgSet);
    return sgList.get(0);
  }

  /**
   * Get group id from metadata plan
   */
  public String getGroupIdFromMetadataPlan(MetadataPlan metadataPlan)
      throws ProcessorException, PathErrorException {
    MetadataOperator.NamespaceType namespaceType = metadataPlan.getNamespaceType();
    Path path = metadataPlan.getPath();
    String groupId;
    switch (namespaceType) {
      case ADD_PATH:
      case DELETE_PATH:
        String deviceId = path.getDevice();
        String storageGroup = QPExecutorUtils.getStroageGroupByDevice(deviceId);
        groupId = router.getGroupIdBySG(storageGroup);
        break;
      case SET_FILE_LEVEL:
        boolean fileLevelExist = mManager.checkStorageLevelOfMTree(path.getFullPath());
        if (fileLevelExist) {
          throw new ProcessorException(
              String.format("File level %s already exists.", path.getFullPath()));
        } else {
          groupId = ClusterConfig.METADATA_GROUP_ID;
        }
        break;
      default:
        throw new ProcessorException("unknown namespace type:" + namespaceType);
    }
    return groupId;
  }

  /**
   * Handle non query single request by group id and physical plan
   */
  private boolean handleNonQueryRequest(String groupId, PhysicalPlan plan)
      throws IOException, RaftConnectionException, InterruptedException {
    List<PhysicalPlan> plans = Collections.singletonList(plan);
    BasicRequest request;
    if (groupId.equals(ClusterConfig.METADATA_GROUP_ID)) {
      request = new MetaGroupNonQueryRequest(groupId, plans);
    } else {
      request = new DataGroupNonQueryRequest(groupId, plans);
    }
    SingleQPTask qpTask = new SingleQPTask(true, request);
    currentTask.set(qpTask);

    /** Check if the plan can be executed locally. **/
    if (QPExecutorUtils.canHandleNonQueryByGroupId(groupId)) {
      return handleNonQueryRequestLocally(groupId, qpTask);
    } else {
      PeerId leader = RaftUtils.getLocalLeaderPeerID(groupId);
      boolean res = false;
      qpTask.setTargetNode(leader);
      try {
         res = syncHandleNonQueryTask(qpTask);
      } catch (RaftConnectionException ex) {
        boolean success = false;
        PeerId nextNode = RaftUtils.getPeerIDInOrder(groupId);
        PeerId firstNode = nextNode;
        boolean first = true;
        while (!success) {
          try {
            if (!first) {
              nextNode = RaftUtils.getPeerIDInOrder(groupId);
              if (firstNode.equals(nextNode)) {
                break;
              }
            }
            first = false;
            LOGGER.debug("Previous task fail, then send non-query task for group {} to node {}.", groupId, nextNode);
            qpTask.resetTask();
            qpTask.setTargetNode(nextNode);
            qpTask.setTaskState(TaskState.INITIAL);
            currentTask.set(qpTask);
            res = syncHandleNonQueryTask(qpTask);
            LOGGER.debug("Non-query task for group {} to node {} succeed.", groupId, nextNode);
            success = true;
            RaftUtils.updateRaftGroupLeader(groupId, nextNode);
          } catch (RaftConnectionException e1) {
            LOGGER.debug("Non-query task for group {} to node {} fail.", groupId, nextNode);
          }
        }
        LOGGER.debug("The final result for non-query task is {}", success);
        if (!success) {
          throw ex;
        }
      }
      return res;
    }
  }

  /**
   * Handle data group request locally.
   */
  public boolean handleNonQueryRequestLocally(String groupId, QPTask qpTask)
      throws InterruptedException {
    BasicResponse response;
    RaftService service;
    if (groupId.equals(ClusterConfig.METADATA_GROUP_ID)) {
      response = MetaGroupNonQueryResponse.createEmptyResponse(groupId);
      MetadataRaftHolder metadataRaftHolder = RaftUtils.getMetadataRaftHolder();
      service = (RaftService) metadataRaftHolder.getService();
    } else {
      response = DataGroupNonQueryResponse.createEmptyResponse(groupId);
      DataPartitionRaftHolder dataRaftHolder = RaftUtils.getDataPartitonRaftHolder(groupId);
      service = (RaftService) dataRaftHolder.getService();
    }

    /** Apply qpTask to Raft Node **/
    return RaftUtils.executeRaftTaskForLocalProcessor(service, qpTask, response);
  }

  /**
   * Async handle task by QPTask and leader id.
   *
   * @param task request QPTask
   * @return request result
   */
  public boolean syncHandleNonQueryTask(SingleQPTask task)
      throws RaftConnectionException, InterruptedException {
    BasicResponse response = syncHandleNonQuerySingleTaskGetRes(task, 0);
    return response != null && response.isSuccess();
  }

}
