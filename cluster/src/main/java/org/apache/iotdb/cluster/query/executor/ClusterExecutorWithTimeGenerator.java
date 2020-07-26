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
package org.apache.iotdb.cluster.query.executor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.iotdb.cluster.query.dataset.ClusterDataSetWithTimeGenerator;
import org.apache.iotdb.cluster.query.factory.ClusterSeriesReaderFactory;
import org.apache.iotdb.cluster.query.manager.coordinatornode.ClusterRpcSingleQueryManager;
import org.apache.iotdb.cluster.query.manager.coordinatornode.FilterSeriesGroupEntity;
import org.apache.iotdb.cluster.query.timegenerator.ClusterTimeGenerator;
import org.apache.iotdb.db.exception.FileNodeManagerException;
import org.apache.iotdb.db.exception.PathErrorException;
import org.apache.iotdb.db.query.context.QueryContext;
import org.apache.iotdb.db.query.control.QueryResourceManager;
import org.apache.iotdb.db.query.reader.merge.EngineReaderByTimeStamp;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.read.common.Path;
import org.apache.iotdb.tsfile.read.expression.QueryExpression;
import org.apache.iotdb.tsfile.read.query.dataset.QueryDataSet;

public class ClusterExecutorWithTimeGenerator {

  /**
   * query expression
   */
  private QueryExpression queryExpression;

  /**
   * Manger for all remote query series reader resource in the query
   */
  private ClusterRpcSingleQueryManager queryManager;

  /**
   * Constructor of ClusterExecutorWithTimeGenerator
   */
  public ClusterExecutorWithTimeGenerator(QueryExpression queryExpression,
      ClusterRpcSingleQueryManager queryManager) {
    this.queryExpression = queryExpression;
    this.queryManager = queryManager;
  }

  /**
   * Execute query with value filter.
   *
   * @return QueryDataSet object
   */
  public QueryDataSet execute(QueryContext context) throws FileNodeManagerException {

    /** add query token for query series which can handle locally **/
    List<Path> localQuerySeries = new ArrayList<>(queryExpression.getSelectedSeries());
    Set<Path> remoteQuerySeries = new HashSet<>();
    queryManager.getSelectSeriesGroupEntityMap().values().forEach(
        selectSeriesGroupEntity -> selectSeriesGroupEntity.getSelectPaths()
            .forEach(path -> remoteQuerySeries.add(path)));
    localQuerySeries.removeAll(remoteQuerySeries);
    QueryResourceManager.getInstance()
        .beginQueryOfGivenQueryPaths(context.getJobId(), localQuerySeries);

    /** add query token for filter series which can handle locally **/
    Set<String> deviceIdSet = new HashSet<>();
    for (FilterSeriesGroupEntity filterSeriesGroupEntity : queryManager
        .getFilterSeriesGroupEntityMap().values()) {
      List<Path> remoteFilterSeries = filterSeriesGroupEntity.getFilterPaths();
      remoteFilterSeries.forEach(seriesPath -> deviceIdSet.add(seriesPath.getDevice()));
    }
    QueryResourceManager.getInstance()
        .beginQueryOfGivenExpression(context.getJobId(), queryExpression.getExpression(),
            deviceIdSet);

    ClusterTimeGenerator timestampGenerator;
    List<EngineReaderByTimeStamp> readersOfSelectedSeries;
    /** Get data type of select paths **/
    List<TSDataType> dataTypes = new ArrayList<>();
    try {
      timestampGenerator = new ClusterTimeGenerator(queryExpression.getExpression(), context,
          queryManager);
      readersOfSelectedSeries = ClusterSeriesReaderFactory
          .createReadersByTimestampOfSelectedPaths(queryExpression.getSelectedSeries(), context,
              queryManager, dataTypes);
    } catch (IOException | PathErrorException ex) {
      throw new FileNodeManagerException(ex);
    }

    EngineReaderByTimeStamp[] readersOfSelectedSeriesArray = new EngineReaderByTimeStamp[readersOfSelectedSeries
        .size()];
    int index = 0;
    for (EngineReaderByTimeStamp reader : readersOfSelectedSeries) {
      readersOfSelectedSeriesArray[index] = reader;
      index++;
    }

    return new ClusterDataSetWithTimeGenerator(queryExpression.getSelectedSeries(), dataTypes,
        timestampGenerator,
        readersOfSelectedSeriesArray, queryManager);
  }
}
