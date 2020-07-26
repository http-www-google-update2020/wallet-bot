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
package org.apache.iotdb.db.query.timegenerator;

import static org.apache.iotdb.tsfile.read.expression.ExpressionType.AND;
import static org.apache.iotdb.tsfile.read.expression.ExpressionType.OR;

import java.io.IOException;
import org.apache.iotdb.db.engine.querycontext.QueryDataSource;
import org.apache.iotdb.db.exception.FileNodeManagerException;
import org.apache.iotdb.db.query.context.QueryContext;
import org.apache.iotdb.db.query.control.QueryResourceManager;
import org.apache.iotdb.db.query.factory.SeriesReaderFactory;
import org.apache.iotdb.db.query.reader.AllDataReader;
import org.apache.iotdb.db.query.reader.IReader;
import org.apache.iotdb.db.query.reader.merge.PriorityMergeReader;
import org.apache.iotdb.db.query.reader.sequence.SequenceDataReader;
import org.apache.iotdb.tsfile.exception.write.UnSupportedDataTypeException;
import org.apache.iotdb.tsfile.read.expression.IBinaryExpression;
import org.apache.iotdb.tsfile.read.expression.IExpression;
import org.apache.iotdb.tsfile.read.expression.impl.SingleSeriesExpression;
import org.apache.iotdb.tsfile.read.filter.basic.Filter;
import org.apache.iotdb.tsfile.read.query.timegenerator.node.AndNode;
import org.apache.iotdb.tsfile.read.query.timegenerator.node.Node;
import org.apache.iotdb.tsfile.read.query.timegenerator.node.OrNode;

/**
 * Construct node in expression tree while reading process.
 */
public abstract class AbstractNodeConstructor {

  /**
   * Construct expression node.
   *
   * @param expression expression
   * @return Node object
   * @throws IOException IOException
   * @throws FileNodeManagerException FileNodeManagerException
   */
  public abstract Node construct(IExpression expression, QueryContext context)
      throws FileNodeManagerException;

  /**
   * Construct not series type node.
   *
   * @param expression expression
   * @return Node object
   * @throws FileNodeManagerException FileNodeManagerException
   */
  protected Node constructNotSeriesNode(IExpression expression, QueryContext context)
      throws FileNodeManagerException {
    Node leftChild;
    Node rightChild;
    if (expression.getType() == OR) {
      leftChild = this.construct(((IBinaryExpression) expression).getLeft(), context);
      rightChild = this.construct(((IBinaryExpression) expression).getRight(), context);
      return new OrNode(leftChild, rightChild);
    } else if (expression.getType() == AND) {
      leftChild = this.construct(((IBinaryExpression) expression).getLeft(), context);
      rightChild = this.construct(((IBinaryExpression) expression).getRight(), context);
      return new AndNode(leftChild, rightChild);
    } else {
      throw new UnSupportedDataTypeException(
          "Unsupported QueryFilterType when construct OperatorNode: " + expression.getType());
    }
  }

  protected IReader generateSeriesReader(SingleSeriesExpression singleSeriesExpression,
      QueryContext context)
      throws IOException, FileNodeManagerException {

    QueryDataSource queryDataSource = QueryResourceManager.getInstance().getQueryDataSource(
        singleSeriesExpression.getSeriesPath(), context);

    Filter filter = singleSeriesExpression.getFilter();

    // reader for all sequence data
    SequenceDataReader tsFilesReader = new SequenceDataReader(queryDataSource.getSeqDataSource(),
        filter, context);

    // reader for all unSequence data
    PriorityMergeReader unSeqMergeReader = SeriesReaderFactory.getInstance()
        .createUnSeqMergeReader(queryDataSource.getOverflowSeriesDataSource(), filter);

    if (!tsFilesReader.hasNext()) {
      //only have unsequence data.
      return unSeqMergeReader;
    } else {
      //merge sequence data with unsequence data.
      return new AllDataReader(tsFilesReader, unSeqMergeReader);
    }
  }
}
