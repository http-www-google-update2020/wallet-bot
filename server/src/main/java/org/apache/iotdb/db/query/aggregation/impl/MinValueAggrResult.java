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

package org.apache.iotdb.db.query.aggregation.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import org.apache.iotdb.db.query.aggregation.AggregateResult;
import org.apache.iotdb.db.query.aggregation.AggregationType;
import org.apache.iotdb.db.query.reader.series.IReaderByTimestamp;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.statistics.Statistics;
import org.apache.iotdb.tsfile.read.common.BatchData;

public class MinValueAggrResult extends AggregateResult {

  public MinValueAggrResult(TSDataType dataType) {
    super(dataType, AggregationType.MIN_VALUE);
    reset();
  }

  @Override
  public Object getResult() {
    return hasResult() ? getValue() : null;
  }

  @Override
  public void updateResultFromStatistics(Statistics statistics) {
    Comparable<Object> minVal = (Comparable<Object>) statistics.getMinValue();
    updateResult(minVal);
  }

  @Override
  public void updateResultFromPageData(BatchData dataInThisPage) {
    updateResultFromPageData(dataInThisPage, Long.MAX_VALUE);
  }

  @Override
  public void updateResultFromPageData(BatchData dataInThisPage, long bound) {
    while (dataInThisPage.hasCurrent() && dataInThisPage.currentTime() < bound) {
      updateResult((Comparable<Object>) dataInThisPage.currentValue());
      dataInThisPage.next();
    }
  }

  @Override
  public void updateResultUsingTimestamps(long[] timestamps, int length,
      IReaderByTimestamp dataReader) throws IOException {
    Comparable<Object> minVal = null;
    for (int i = 0; i < length; i++) {
      Object value = dataReader.getValueInTimestamp(timestamps[i]);
      if (value == null) {
        continue;
      }
      if (minVal == null || minVal.compareTo(value) > 0) {
        minVal = (Comparable<Object>) value;
      }
    }
    updateResult(minVal);
  }

  @Override
  public boolean isCalculatedAggregationResult() {
    return false;
  }

  @Override
  public void merge(AggregateResult another) {
    if (another.getResult() != null) {
      Object value = another.getResult();
      this.updateResult((Comparable<Object>) value);
    }
  }

  @Override
  protected void deserializeSpecificFields(ByteBuffer buffer) {
  }

  @Override
  protected void serializeSpecificFields(OutputStream outputStream) throws IOException {
  }

  private void updateResult(Comparable<Object> minVal) {
    if (minVal == null) {
      return;
    }
    if (!hasResult() || minVal.compareTo(getValue()) < 0) {
      setValue(minVal);
    }
  }

}
