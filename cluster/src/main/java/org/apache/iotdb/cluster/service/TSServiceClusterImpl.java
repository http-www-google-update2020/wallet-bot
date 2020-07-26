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
package org.apache.iotdb.cluster.service;

import java.io.IOException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.iotdb.cluster.config.ClusterConsistencyLevel;
import org.apache.iotdb.cluster.config.ClusterConstant;
import org.apache.iotdb.cluster.exception.ConsistencyLevelException;
import org.apache.iotdb.cluster.qp.executor.ClusterQueryProcessExecutor;
import org.apache.iotdb.cluster.qp.executor.NonQueryExecutor;
import org.apache.iotdb.cluster.qp.executor.QueryMetadataExecutor;
import org.apache.iotdb.cluster.query.manager.coordinatornode.ClusterRpcQueryManager;
import org.apache.iotdb.cluster.query.manager.coordinatornode.IClusterRpcQueryManager;
import org.apache.iotdb.db.auth.AuthException;
import org.apache.iotdb.db.conf.IoTDBConstant;
import org.apache.iotdb.db.exception.FileNodeManagerException;
import org.apache.iotdb.db.exception.PathErrorException;
import org.apache.iotdb.db.exception.ProcessorException;
import org.apache.iotdb.db.metadata.Metadata;
import org.apache.iotdb.db.qp.QueryProcessor;
import org.apache.iotdb.db.qp.physical.PhysicalPlan;
import org.apache.iotdb.db.qp.physical.crud.QueryPlan;
import org.apache.iotdb.db.query.context.QueryContext;
import org.apache.iotdb.db.query.control.QueryResourceManager;
import org.apache.iotdb.db.service.TSServiceImpl;
import org.apache.iotdb.service.rpc.thrift.TSCloseOperationReq;
import org.apache.iotdb.service.rpc.thrift.TSExecuteBatchStatementReq;
import org.apache.iotdb.service.rpc.thrift.TSExecuteBatchStatementResp;
import org.apache.iotdb.service.rpc.thrift.TSFetchResultsReq;
import org.apache.iotdb.service.rpc.thrift.TS_StatusCode;
import org.apache.iotdb.tsfile.exception.filter.QueryFilterOptimizationException;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.read.common.Path;
import org.apache.iotdb.tsfile.read.query.dataset.QueryDataSet;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Distributed version of PRC implementation
 */
public class TSServiceClusterImpl extends TSServiceImpl {

  private static final Logger LOGGER = LoggerFactory.getLogger(TSServiceClusterImpl.class);

  private QueryMetadataExecutor queryMetadataExecutor = new QueryMetadataExecutor();
  private ClusterQueryProcessExecutor queryDataExecutor = new ClusterQueryProcessExecutor(
      queryMetadataExecutor);
  private NonQueryExecutor nonQueryExecutor = new NonQueryExecutor();

  private IClusterRpcQueryManager queryManager = ClusterRpcQueryManager.getInstance();

  public TSServiceClusterImpl() throws IOException {
    super();
    processor = new QueryProcessor(queryDataExecutor);
  }


  @Override
  protected Set<String> getAllStorageGroups() throws InterruptedException {
    return queryMetadataExecutor.processStorageGroupQuery();
  }

  @Override
  protected List<List<String>> getTimeSeriesForPath(String path)
      throws PathErrorException, InterruptedException, ProcessorException {
    return queryMetadataExecutor.processTimeSeriesQuery(path);
  }

  @Override
  protected String getMetadataInString()
      throws InterruptedException, ProcessorException {
    return queryMetadataExecutor.processMetadataInStringQuery();
  }

  @Override
  protected Metadata getMetadata()
      throws InterruptedException, ProcessorException, PathErrorException {
    return queryMetadataExecutor.processMetadataQuery();
  }

  @Override
  protected TSDataType getSeriesType(String path)
      throws PathErrorException, InterruptedException, ProcessorException {
    return queryMetadataExecutor.processSeriesTypeQuery(path);
  }

  @Override
  protected List<String> getPaths(String path)
      throws PathErrorException, InterruptedException, ProcessorException {
    return queryMetadataExecutor.processPathsQuery(path);
  }

  @Override
  public TSExecuteBatchStatementResp executeBatchStatement(TSExecuteBatchStatementReq req)
      throws TException {
    try {
      if (!checkLogin()) {
        LOGGER.info(INFO_NOT_LOGIN, IoTDBConstant.GLOBAL_DB_NAME);
        return getTSBathExecuteStatementResp(TS_StatusCode.ERROR_STATUS, ERROR_NOT_LOGIN, null);
      }
      List<String> statements = req.getStatements();
      PhysicalPlan[] physicalPlans = new PhysicalPlan[statements.size()];
      int[] result = new int[statements.size()];
      StringBuilder batchErrorMessage = new StringBuilder();
      boolean isAllSuccessful = true;

      /* find all valid physical plans */
      for (int i = 0; i < statements.size(); i++) {
        try {
          PhysicalPlan plan = processor
              .parseSQLToPhysicalPlan(statements.get(i), zoneIds.get());
          plan.setProposer(username.get());

          /* if meet a query, handle all requests before the query request. */
          if (plan.isQuery()) {
            int[] resultTemp = new int[i];
            PhysicalPlan[] physicalPlansTemp = new PhysicalPlan[i];
            System.arraycopy(result, 0, resultTemp, 0, i);
            System.arraycopy(physicalPlans, 0, physicalPlansTemp, 0, i);
            result = resultTemp;
            physicalPlans = physicalPlansTemp;
            BatchResult batchResult = new BatchResult(isAllSuccessful, batchErrorMessage, result);
            nonQueryExecutor.processBatch(physicalPlans, batchResult);
            batchErrorMessage.append(String
                .format(ERROR_MESSAGE_FORMAT_IN_BATCH, i,
                    "statement is query :" + statements.get(i)));
            return getTSBathExecuteStatementResp(TS_StatusCode.ERROR_STATUS,
                statements.get(i), Arrays.stream(result).boxed().collect(
                    Collectors.toList()));
          }

          // check permissions
          List<Path> paths = plan.getPaths();
          if (!checkAuthorization(paths, plan)) {
            String errMessage = String.format("No permissions for this operation %s",
                plan.getOperatorType());
            result[i] = Statement.EXECUTE_FAILED;
            isAllSuccessful = false;
            batchErrorMessage.append(String.format(ERROR_MESSAGE_FORMAT_IN_BATCH, i, errMessage));
          } else {
            physicalPlans[i] = plan;
          }
        } catch (AuthException e) {
          LOGGER.error("meet error while checking authorization.", e);
          String errMessage = String.format("Uninitialized authorizer" + " beacuse %s",
              e.getMessage());
          result[i] = Statement.EXECUTE_FAILED;
          isAllSuccessful = false;
          batchErrorMessage.append(String.format(ERROR_MESSAGE_FORMAT_IN_BATCH, i, errMessage));
        } catch (Exception e) {
          String errMessage = String.format("Fail to generate physcial plan" + "%s beacuse %s",
              statements.get(i), e.getMessage());
          result[i] = Statement.EXECUTE_FAILED;
          isAllSuccessful = false;
          batchErrorMessage.append(String.format(ERROR_MESSAGE_FORMAT_IN_BATCH, i, errMessage));
        }
      }

      BatchResult batchResult = new BatchResult(isAllSuccessful, batchErrorMessage, result);
      nonQueryExecutor.processBatch(physicalPlans, batchResult);
      batchErrorMessage.append(batchResult.batchErrorMessage);
      isAllSuccessful = batchResult.isAllSuccessful;

      if (isAllSuccessful) {
        return getTSBathExecuteStatementResp(TS_StatusCode.SUCCESS_STATUS,
            "Execute batch statements successfully", Arrays.stream(result).boxed().collect(
                Collectors.toList()));
      } else {
        return getTSBathExecuteStatementResp(TS_StatusCode.ERROR_STATUS,
            batchErrorMessage.toString(),
            Arrays.stream(result).boxed().collect(
                Collectors.toList()));
      }
    } catch (Exception e) {
      LOGGER.error("{}: error occurs when executing statements", IoTDBConstant.GLOBAL_DB_NAME, e);
      return getTSBathExecuteStatementResp(TS_StatusCode.ERROR_STATUS, e.getMessage(), null);
    }
  }

  /**
   * Present batch results.
   */
  public static class BatchResult {

    private boolean isAllSuccessful;
    private StringBuilder batchErrorMessage;
    private int[] resultArray;

    public BatchResult(boolean isAllSuccessful, StringBuilder batchErrorMessage,
        int[] resultArray) {
      this.isAllSuccessful = isAllSuccessful;
      this.batchErrorMessage = batchErrorMessage;
      this.resultArray = resultArray;
    }

    public boolean isAllSuccessful() {
      return isAllSuccessful;
    }

    public void setAllSuccessful(boolean allSuccessful) {
      isAllSuccessful = allSuccessful;
    }

    public StringBuilder getBatchErrorMessage() {
      return batchErrorMessage;
    }

    public void addBatchErrorMessage(int index, String batchErrorMessage) {
      this.batchErrorMessage
          .append(String.format(ERROR_MESSAGE_FORMAT_IN_BATCH, index, batchErrorMessage));
    }

    public int[] getResultArray() {
      return resultArray;
    }

    public void setResultArray(int[] resultArray) {
      this.resultArray = resultArray;
    }
  }

  @Override
  public boolean execSetConsistencyLevel(String statement) throws Exception {
    if (statement == null) {
      return false;
    }
    statement = statement.toLowerCase().trim();
    try {
      if (Pattern.matches(ClusterConstant.SET_READ_METADATA_CONSISTENCY_LEVEL_PATTERN, statement)) {
        int level = parseConsistencyLevel(statement);
        queryMetadataExecutor.setReadMetadataConsistencyLevel(level);
        return true;
      } else if (Pattern
          .matches(ClusterConstant.SET_READ_DATA_CONSISTENCY_LEVEL_PATTERN, statement)) {
        int level = parseConsistencyLevel(statement);
        queryDataExecutor.setReadDataConsistencyLevel(level);
        return true;
      } else {
        return false;
      }
    } catch (ConsistencyLevelException e) {
      throw new Exception(e.getMessage());
    }
  }

  private int parseConsistencyLevel(String statement) throws ConsistencyLevelException {
    String[] splits = statement.split("\\s+");
    String levelName = splits[splits.length - 1].toLowerCase();
    int level = ClusterConsistencyLevel.getLevel(levelName);
    if (level == ClusterConsistencyLevel.UNSUPPORT_LEVEL) {
      throw new ConsistencyLevelException(String.format("Consistency level %s not support", levelName));
    }
    return level;
  }

  @Override
  protected boolean executeNonQuery(PhysicalPlan plan) throws ProcessorException {
    return nonQueryExecutor.processNonQuery(plan);
  }

  @Override
  protected void checkFileLevelSet(List<Path> paths) throws PathErrorException {
    //It's unnecessary to do this check. It has benn checked in transforming query physical plan.
  }

  @Override
  protected void releaseQueryResource(TSCloseOperationReq req) throws Exception {
    Map<Long, QueryContext> contextMap = contextMapLocal.get();
    if (contextMap == null) {
      return;
    }
    if (req == null || req.queryId == -1) {
      // end query for all the query tokens created by current thread
      for (QueryContext context : contextMap.values()) {
        QueryResourceManager.getInstance().endQueryForGivenJob(context.getJobId());
        queryManager.releaseQueryResource(context.getJobId());
      }
      contextMapLocal.set(new HashMap<>());
    } else {
      long jobId = contextMap.remove(req.queryId).getJobId();
      QueryResourceManager.getInstance().endQueryForGivenJob(jobId);
      queryManager.releaseQueryResource(jobId);
    }
  }

  @Override
  protected QueryDataSet createNewDataSet(String statement, int fetchSize, TSFetchResultsReq req)
      throws PathErrorException, QueryFilterOptimizationException, FileNodeManagerException,
      ProcessorException, IOException {
    PhysicalPlan physicalPlan = queryStatus.get().get(statement);
    processor.getExecutor().setFetchSize(fetchSize);

    long jobId = QueryResourceManager.getInstance().assignJobId();
    QueryContext context = new QueryContext(jobId);
    initContextMap();
    contextMapLocal.get().put(req.queryId, context);

    queryManager.addSingleQuery(jobId, (QueryPlan) physicalPlan);
    QueryDataSet queryDataSet = processor.getExecutor().processQuery(physicalPlan,
        context);
    try {
      queryRet.get().put(statement, queryDataSet);
    }catch (Exception e){
      e.printStackTrace();
    }
    return queryDataSet;
  }

  @Override
  public void handleClientExit() throws TException {
    closeClusterService();
    closeOperation(null);
    closeSession(null);
  }

  /**
   * Close cluster service
   */
  public void closeClusterService() {
    nonQueryExecutor.shutdown();
    queryMetadataExecutor.shutdown();
  }

  public ClusterQueryProcessExecutor getQueryDataExecutor() {
    return queryDataExecutor;
  }

  public void setQueryDataExecutor(
      ClusterQueryProcessExecutor queryDataExecutor) {
    this.queryDataExecutor = queryDataExecutor;
  }

  public QueryMetadataExecutor getQueryMetadataExecutor() {
    return queryMetadataExecutor;
  }

  public void setNonQueryExecutor(NonQueryExecutor nonQueryExecutor) {
    this.nonQueryExecutor = nonQueryExecutor;
  }
}
