/*
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

package org.apache.iotdb.cluster.query.groupby;

import org.apache.iotdb.cluster.query.reader.ClusterTimeGenerator;
import org.apache.iotdb.cluster.server.member.MetaGroupMember;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.exception.StorageEngineException;
import org.apache.iotdb.db.exception.query.QueryProcessException;
import org.apache.iotdb.db.qp.physical.crud.GroupByTimePlan;
import org.apache.iotdb.db.qp.physical.crud.RawDataQueryPlan;
import org.apache.iotdb.db.query.context.QueryContext;
import org.apache.iotdb.db.query.dataset.groupby.GroupByWithValueFilterDataSet;
import org.apache.iotdb.db.query.filter.TsFileFilter;
import org.apache.iotdb.db.query.reader.series.IReaderByTimestamp;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.read.common.Path;
import org.apache.iotdb.tsfile.read.expression.IExpression;
import org.apache.iotdb.tsfile.read.query.timegenerator.TimeGenerator;

public class ClusterGroupByVFilterDataSet extends GroupByWithValueFilterDataSet {

  private MetaGroupMember metaGroupMember;

  public ClusterGroupByVFilterDataSet(QueryContext context,
      GroupByTimePlan groupByPlan, MetaGroupMember metaGroupMember)
      throws StorageEngineException, QueryProcessException {
    this.paths = groupByPlan.getDeduplicatedPaths();
    this.dataTypes = groupByPlan.getDeduplicatedDataTypes();

    this.queryId = context.getQueryId();
    this.interval = groupByPlan.getInterval();
    this.slidingStep = groupByPlan.getSlidingStep();
    this.startTime = groupByPlan.getStartTime();
    this.endTime = groupByPlan.getEndTime();
    this.leftCRightO = groupByPlan.isLeftCRightO();
    // init group by time partition
    this.hasCachedTimeInterval = false;
    this.curStartTime = this.startTime - slidingStep;
    this.curEndTime = -1;

    this.timeStampFetchSize = IoTDBDescriptor.getInstance().getConfig().getBatchSize();
    this.metaGroupMember = metaGroupMember;
    initGroupBy(context, groupByPlan);
  }

  @Override
  protected TimeGenerator getTimeGenerator(IExpression expression, QueryContext context,
      RawDataQueryPlan rawDataQueryPlan)
      throws StorageEngineException {
    return new ClusterTimeGenerator(expression, context, metaGroupMember, rawDataQueryPlan);
  }

  @Override
  protected IReaderByTimestamp getReaderByTime(Path path, RawDataQueryPlan dataQueryPlan,
      TSDataType dataType,
      QueryContext context,
      TsFileFilter fileFilter) throws StorageEngineException, QueryProcessException {
    return metaGroupMember.getReaderByTimestamp(path,
        dataQueryPlan.getAllMeasurementsInDevice(path.getDevice()), dataType, context);
  }
}
