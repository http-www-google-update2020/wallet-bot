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
package org.apache.iotdb.db.utils.datastructure;

import static org.apache.iotdb.db.rescon.NVMPrimitiveArrayPool.ARRAY_SIZE;

import org.apache.iotdb.db.nvm.space.NVMDataSpace;
import org.apache.iotdb.db.rescon.PrimitiveArrayPool;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;

public class NVMBooleanTVList extends NVMTVList {

  // TODO
  private boolean[][] sortedValues;
  private boolean[][] tempValuesForSort;

  NVMBooleanTVList(String sgId, String deviceId, String measurementId) {
    super(sgId, deviceId, measurementId);
    dataType = TSDataType.BOOLEAN;
  }

  @Override
  public void putBoolean(long timestamp, boolean value) {
    checkExpansion();
    int arrayIndex = size / ARRAY_SIZE;
    int elementIndex = size % ARRAY_SIZE;
    minTime = minTime <= timestamp ? minTime : timestamp;
    timestamps.get(arrayIndex).setData(elementIndex, timestamp);
    values.get(arrayIndex).setData(elementIndex, value);
    size++;
    if (sorted && size > 1 && timestamp < getTime(size - 2)) {
      sorted = false;
    }
  }

  @Override
  public boolean getBoolean(int index) {
    return (boolean) getValue(index);
  }

  @Override
  public BooleanTVList clone() {
    BooleanTVList cloneList = new BooleanTVList();
    cloneAs(cloneList);
    for (NVMDataSpace valueSpace : values) {
      cloneList.addBatchValue((boolean[]) cloneValue(valueSpace));
    }
    return cloneList;
  }

  @Override
  protected void initSortedAndTempArrays() {
    if (sortedTimestamps == null || sortedTimestamps.length < size) {
      sortedTimestamps = (long[][]) PrimitiveArrayPool
          .getInstance().getDataListsByType(TSDataType.INT64, size);
      tempTimestampsForSort = (long[][]) PrimitiveArrayPool
          .getInstance().getDataListsByType(TSDataType.INT64, size);
    }
    if (sortedValues == null || sortedValues.length < size) {
      sortedValues = (boolean[][]) PrimitiveArrayPool
          .getInstance().getDataListsByType(dataType, size);
      tempValuesForSort = (boolean[][]) PrimitiveArrayPool
          .getInstance().getDataListsByType(dataType, size);
    }
  }

  @Override
  protected void copyTVToTempArrays() {
    int arrayIndex = 0;
    int elementIndex = 0;
    for (int i = 0; i < size; i++) {
      long time = (long) timestamps.get(arrayIndex).getData(elementIndex);
      boolean value = (boolean) values.get(arrayIndex).getData(elementIndex);
      tempTimestampsForSort[arrayIndex][elementIndex] = time;
      tempValuesForSort[arrayIndex][elementIndex] = value;

      elementIndex++;
      if (elementIndex == ARRAY_SIZE) {
        elementIndex = 0;
        arrayIndex++;
      }
    }
  }

  @Override
  protected void copyTVFromTempArrays() {
    int arrayIndex = 0;
    int elementIndex = 0;
    for (int i = 0; i < size; i++) {
      long time = tempTimestampsForSort[arrayIndex][elementIndex];
      boolean value = tempValuesForSort[arrayIndex][elementIndex];

      timestamps.get(arrayIndex).setData(elementIndex, time);
      values.get(arrayIndex).setData(elementIndex, value);

      elementIndex++;
      if (elementIndex == ARRAY_SIZE) {
        elementIndex = 0;
        arrayIndex++;
      }
    }
  }

  @Override
  protected void clearSortedValue() {
    if (sortedValues != null) {
      for (boolean[] dataArray : sortedValues) {
        PrimitiveArrayPool.getInstance().release(dataArray);
      }
      sortedValues = null;
    }

    if (tempValuesForSort != null) {
      for (boolean[] dataArray : tempValuesForSort) {
        PrimitiveArrayPool.getInstance().release(dataArray);
      }
      tempValuesForSort = null;
    }
  }

  @Override
  protected void setFromSorted(int src, int dest) {
    setForSort(dest, sortedTimestamps[src / ARRAY_SIZE][src % ARRAY_SIZE],
        sortedValues[src / ARRAY_SIZE][src % ARRAY_SIZE]);
  }

  @Override
  protected void set(int src, int dest) {
    long srcT = getTime(src);
    boolean srcV = getBoolean(src);
    set(dest, srcT, srcV);
  }

  @Override
  protected void setValueForSort(int arrayIndex, int elementIndex, Object value) {
    tempValuesForSort[arrayIndex][elementIndex] = (boolean) value;
  }

  @Override
  protected Object getValueForSort(int index) {
    if (index >= size) {
      throw new ArrayIndexOutOfBoundsException(index);
    }
    int arrayIndex = index / ARRAY_SIZE;
    int elementIndex = index % ARRAY_SIZE;
    return tempValuesForSort[arrayIndex][elementIndex];
  }

  @Override
  protected void setToSorted(int src, int dest) {
    sortedTimestamps[dest/ARRAY_SIZE][dest% ARRAY_SIZE] = getTimeForSort(src);
    sortedValues[dest/ARRAY_SIZE][dest%ARRAY_SIZE] = (boolean) getValueForSort(src);
  }
  
  @Override
  public void putBooleans(long[] time, boolean[] value) {
    int length = time.length;

    for (int i = 0; i < length; i++) {
      putBoolean(time[i], value[i]);
    }
  }

  @Override
  public void putBooleans(long[] time, boolean[] value, int start, int end) {
    for (int i = start; i < end; i++) {
      putBoolean(time[i], value[i]);
    }
  }
}
