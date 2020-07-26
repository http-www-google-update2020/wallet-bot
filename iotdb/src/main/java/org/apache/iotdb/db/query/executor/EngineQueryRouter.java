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

package org.apache.iotdb.db.query.executor;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.iotdb.db.exception.FileNodeManagerException;
import org.apache.iotdb.db.exception.PathErrorException;
import org.apache.iotdb.db.exception.ProcessorException;
import org.apache.iotdb.db.query.context.QueryContext;
import org.apache.iotdb.db.query.dataset.groupby.GroupByWithOnlyTimeFilterDataSet;
import org.apache.iotdb.db.query.dataset.groupby.GroupByWithValueFilterDataSet;
import org.apache.iotdb.db.query.fill.IFill;
import org.apache.iotdb.tsfile.exception.filter.QueryFilterOptimizationException;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.read.common.Path;
import org.apache.iotdb.tsfile.read.expression.ExpressionType;
import org.apache.iotdb.tsfile.read.expression.IExpression;
import org.apache.iotdb.tsfile.read.expression.QueryExpression;
import org.apache.iotdb.tsfile.read.expression.impl.BinaryExpression;
import org.apache.iotdb.tsfile.read.expression.impl.GlobalTimeExpression;
import org.apache.iotdb.tsfile.read.expression.util.ExpressionOptimizer;
import org.apache.iotdb.tsfile.read.filter.TimeFilter;
import org.apache.iotdb.tsfile.read.query.dataset.QueryDataSet;
import org.apache.iotdb.tsfile.utils.Pair;

/**
 * Query entrance class of IoTDB query process. All query clause will be transformed to physical
 * plan, physical plan will be executed by EngineQueryRouter.
 */
public class EngineQueryRouter extends AbstractQueryRouter {

  @Override
  public QueryDataSet query(QueryExpression queryExpression, QueryContext context)
      throws FileNodeManagerException {

    if (queryExpression.hasQueryFilter()) {
      try {
        IExpression optimizedExpression = ExpressionOptimizer.getInstance()
            .optimize(queryExpression.getExpression(), queryExpression.getSelectedSeries());
        queryExpression.setExpression(optimizedExpression);

        if (optimizedExpression.getType() == ExpressionType.GLOBAL_TIME) {
          EngineExecutorWithoutTimeGenerator engineExecutor =
              new EngineExecutorWithoutTimeGenerator(queryExpression);
          return engineExecutor.execute(context);
        } else {
          EngineExecutorWithTimeGenerator engineExecutor = new EngineExecutorWithTimeGenerator(
              queryExpression);
          return engineExecutor.execute(context);
        }

      } catch (QueryFilterOptimizationException e) {
        throw new FileNodeManagerException(e);
      }
    } else {
      EngineExecutorWithoutTimeGenerator engineExecutor = new EngineExecutorWithoutTimeGenerator(
          queryExpression);
      return engineExecutor.execute(context);
    }
  }

  @Override
  public QueryDataSet aggregate(List<Path> selectedSeries, List<String> aggres,
      IExpression expression, QueryContext context) throws QueryFilterOptimizationException,
      FileNodeManagerException, IOException, PathErrorException, ProcessorException {

    if (expression != null) {
      IExpression optimizedExpression = ExpressionOptimizer.getInstance()
          .optimize(expression, selectedSeries);
      AggregateEngineExecutor engineExecutor = new AggregateEngineExecutor(
          selectedSeries, aggres, optimizedExpression);
      if (optimizedExpression.getType() == ExpressionType.GLOBAL_TIME) {
        return engineExecutor.executeWithoutTimeGenerator(context);
      } else {
        return engineExecutor.executeWithTimeGenerator(context);
      }
    } else {
      AggregateEngineExecutor engineExecutor = new AggregateEngineExecutor(
          selectedSeries, aggres, null);
      return engineExecutor.executeWithoutTimeGenerator(context);
    }
  }

  @Override
  public QueryDataSet groupBy(List<Path> selectedSeries, List<String> aggres,
      IExpression expression, long unit, long origin, List<Pair<Long, Long>> intervals,
      QueryContext context)
      throws ProcessorException, QueryFilterOptimizationException, FileNodeManagerException,
      PathErrorException, IOException {

    long nextJobId = context.getJobId();

    //check the legitimacy of intervals
    checkIntervals(intervals);

    // merge intervals
    List<Pair<Long, Long>> mergedIntervalList = mergeInterval(intervals);

    // construct groupBy intervals filter
    BinaryExpression intervalFilter = null;
    for (Pair<Long, Long> pair : mergedIntervalList) {
      BinaryExpression pairFilter = BinaryExpression
          .and(new GlobalTimeExpression(TimeFilter.gtEq(pair.left)),
              new GlobalTimeExpression(TimeFilter.ltEq(pair.right)));
      if (intervalFilter != null) {
        intervalFilter = BinaryExpression.or(intervalFilter, pairFilter);
      } else {
        intervalFilter = pairFilter;
      }
    }

    // merge interval filter and filtering conditions after where statements
    if (expression == null) {
      expression = intervalFilter;
    } else {
      expression = BinaryExpression.and(expression, intervalFilter);
    }

    IExpression optimizedExpression = ExpressionOptimizer.getInstance()
        .optimize(expression, selectedSeries);
    if (optimizedExpression.getType() == ExpressionType.GLOBAL_TIME) {
      GroupByWithOnlyTimeFilterDataSet groupByEngine = new GroupByWithOnlyTimeFilterDataSet(
          nextJobId, selectedSeries, unit, origin, mergedIntervalList);
      groupByEngine.initGroupBy(context, aggres, optimizedExpression);
      return groupByEngine;
    } else {
      GroupByWithValueFilterDataSet groupByEngine = new GroupByWithValueFilterDataSet(
          nextJobId,
          selectedSeries, unit, origin, mergedIntervalList);
      groupByEngine.initGroupBy(context, aggres, optimizedExpression);
      return groupByEngine;
    }
  }

  @Override
  public QueryDataSet fill(List<Path> fillPaths, long queryTime, Map<TSDataType, IFill> fillType,
      QueryContext context)
      throws FileNodeManagerException, PathErrorException, IOException {

    long nextJobId = context.getJobId();

    FillEngineExecutor fillEngineExecutor = new FillEngineExecutor(nextJobId, fillPaths, queryTime,
        fillType);
    return fillEngineExecutor.execute(context);
  }
}
