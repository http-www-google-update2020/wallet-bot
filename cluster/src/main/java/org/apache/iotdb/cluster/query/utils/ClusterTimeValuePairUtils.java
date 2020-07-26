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

import org.apache.iotdb.cluster.query.common.ClusterNullableBatchData;
import org.apache.iotdb.db.utils.TimeValuePair;
import org.apache.iotdb.db.utils.TimeValuePairUtils;
import org.apache.iotdb.db.utils.TsPrimitiveType;
import org.apache.iotdb.tsfile.exception.write.UnSupportedDataTypeException;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.read.common.BatchData;
import org.apache.iotdb.tsfile.utils.Binary;

public class ClusterTimeValuePairUtils {

  private ClusterTimeValuePairUtils() {
  }

  /**
   * get given data's current (time,value) pair.
   *
   * @param data -batch data
   * @return -given data's (time,value) pair
   */
  public static TimeValuePair getCurrentTimeValuePair(BatchData data) {
    if (data instanceof ClusterNullableBatchData) {
      return ((ClusterNullableBatchData) data).getCurrentTimeValuePair();
    } else {
      return TimeValuePairUtils.getCurrentTimeValuePair(data);
    }
  }

  /**
   * Get (time,value) pair according to data type
   */
  public static TimeValuePair getTimeValuePair(long time, Object v, TSDataType dataType) {
    switch (dataType) {
      case INT32:
        return new TimeValuePair(time, new TsPrimitiveType.TsInt((int) v));
      case INT64:
        return new TimeValuePair(time, new TsPrimitiveType.TsLong((long) v));
      case FLOAT:
        return new TimeValuePair(time, new TsPrimitiveType.TsFloat((float) v));
      case DOUBLE:
        return new TimeValuePair(time, new TsPrimitiveType.TsDouble((double) v));
      case TEXT:
        return new TimeValuePair(time, new TsPrimitiveType.TsBinary((Binary) v));
      case BOOLEAN:
        return new TimeValuePair(time, new TsPrimitiveType.TsBoolean((boolean) v));
      default:
        throw new UnSupportedDataTypeException(String.valueOf(v));
    }
  }
}
