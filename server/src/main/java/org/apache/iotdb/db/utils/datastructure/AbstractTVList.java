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

import java.io.IOException;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.read.TimeValuePair;
import org.apache.iotdb.tsfile.read.reader.IPointReader;
import org.apache.iotdb.tsfile.utils.Binary;

public abstract class AbstractTVList {

  private static final String ERR_DATATYPE_NOT_CONSISTENT = "DataType not consistent";

  protected static final int SMALL_ARRAY_LENGTH = 32;

  protected int size;

  protected long[][] sortedTimestamps;
  protected boolean sorted = true;

  /**
   * this field is effective only in the Tvlist in a RealOnlyMemChunk.
   */
  protected long timeOffset = Long.MIN_VALUE;

  protected long pivotTime;

  protected long minTime;

  public int size() {
    return size;
  }

  public abstract long getTime(int index);

  public void putLong(long time, long value) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public void putInt(long time, int value) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public void putFloat(long time, float value) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public void putDouble(long time, double value) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public void putBinary(long time, Binary value) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public void putBoolean(long time, boolean value) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public void putLongs(long[] time, long[] value) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public void putInts(long[] time, int[] value) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public void putFloats(long[] time, float[] value) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public void putDoubles(long[] time, double[] value) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public void putBinaries(long[] time, Binary[] value) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public void putBooleans(long[] time, boolean[] value) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public void putLongs(long[] time, long[] value, int start, int end) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public void putInts(long[] time, int[] value, int start, int end) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public void putFloats(long[] time, float[] value, int start, int end) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public void putDoubles(long[] time, double[] value, int start, int end) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public void putBinaries(long[] time, Binary[] value, int start, int end) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public void putBooleans(long[] time, boolean[] value, int start, int end) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public long getLong(int index) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public int getInt(int index) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public float getFloat(int index) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public double getDouble(int index) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public Binary getBinary(int index) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public boolean getBoolean(int index) {
    throw new UnsupportedOperationException(ERR_DATATYPE_NOT_CONSISTENT);
  }

  public abstract void sort();

  public long getMinTime() {
    return minTime;
  }

  protected abstract void set(int src, int dest);

  protected abstract void setForSort(int src, int dest);

  protected abstract void setFromSorted(int src, int dest);

  protected abstract void setToSorted(int src, int dest);

  protected abstract void reverseRange(int lo, int hi);

  protected abstract Object expandValues();

  @Override
  public abstract AbstractTVList clone();

  protected abstract void releaseLastValueArray();

  protected abstract void releaseLastTimeArray();

  public abstract int delete(long upperBound);

  protected abstract void cloneAs(AbstractTVList abstractCloneList);

  public void clear() {
    size = 0;
    timeOffset = Long.MIN_VALUE;
    sorted = true;
    minTime = Long.MIN_VALUE;
    clearTime();
    clearSortedTime();

    clearValue();
    clearSortedValue();
  }

  protected abstract void clearTime();

  protected abstract void clearSortedTime();

  protected abstract void clearValue();

  protected abstract void clearSortedValue();

  protected abstract void checkExpansion();

  protected abstract Object cloneTime(Object object);

  protected void sort(int lo, int hi) {
    if (sorted) {
      return;
    }
    if (lo == hi) {
      return;
    }
    if (hi - lo <= SMALL_ARRAY_LENGTH) {
      int initRunLen = countRunAndMakeAscending(lo, hi);
      binarySort(lo, hi, lo + initRunLen);
      return;
    }
    int mid = (lo + hi) >>> 1;
    sort(lo, mid);
    sort(mid, hi);
    merge(lo, mid, hi);
  }

  protected int countRunAndMakeAscending(int lo, int hi) {
    assert lo < hi;
    int runHi = lo + 1;
    if (runHi == hi) {
      return 1;
    }

    // Find end of run, and reverse range if descending
    if (getTimeForSort(runHi++) < getTimeForSort(lo)) { // Descending
      while (runHi < hi && getTimeForSort(runHi) < getTimeForSort(runHi - 1)) {
        runHi++;
      }
      reverseRange(lo, runHi);
    } else {                              // Ascending
      while (runHi < hi && getTimeForSort(runHi) >= getTimeForSort(runHi - 1)) {
        runHi++;
      }
    }

    return runHi - lo;
  }

  protected abstract long getTimeForSort(int index);

  protected abstract void setForSort(int index, long timestamp, Object value);

  /**
   * this field is effective only in the Tvlist in a RealOnlyMemChunk.
   */
  public long getTimeOffset() {
    return timeOffset;
  }

  public void setTimeOffset(long timeOffset) {
    this.timeOffset = timeOffset;
  }

  protected abstract int compare(int idx1, int idx2);

  protected abstract void saveAsPivot(int pos);

  protected abstract void setPivotTo(int pos);

  /**
   * From TimSort.java
   */
  protected void binarySort(int lo, int hi, int start) {
    assert lo <= start && start <= hi;
    if (start == lo) {
      start++;
    }
    for (; start < hi; start++) {

      saveAsPivot(start);
      // Set left (and right) to the index where a[start] (pivot) belongs
      int left = lo;
      int right = start;
      assert left <= right;
      /*
       * Invariants:
       *   pivot >= all in [lo, left).
       *   pivot <  all in [right, start).
       */
      while (left < right) {
        int mid = (left + right) >>> 1;
        if (compare(start, mid) < 0) {
          right = mid;
        } else {
          left = mid + 1;
        }
      }
      assert left == right;

      /*
       * The invariants still hold: pivot >= all in [lo, left) and
       * pivot < all in [left, start), so pivot belongs at left.  Note
       * that if there are elements equal to pivot, left points to the
       * first slot after them -- that's why this sort is stable.
       * Slide elements over to make room for pivot.
       */
      int n = start - left;  // The number of elements to move
      for (int i = n; i >= 1; i--) {
        setForSort(left + i - 1, left + i);
      }
      setPivotTo(left);
    }
    for (int i = lo; i < hi; i++) {
      setToSorted(i, i);
    }
  }

  protected void merge(int lo, int mid, int hi) {
    // end of sorting buffer
    int tmpIdx = 0;

    // start of unmerged parts of each sequence
    int leftIdx = lo;
    int rightIdx = mid;

    // copy the minimum elements to sorting buffer until one sequence is exhausted
    int endSide = 0;
    while (endSide == 0) {
      if (compare(leftIdx, rightIdx) <= 0) {
        setToSorted(leftIdx, lo + tmpIdx);
        tmpIdx++;
        leftIdx++;
        if (leftIdx == mid) {
          endSide = 1;
        }
      } else {
        setToSorted(rightIdx, lo + tmpIdx);
        tmpIdx++;
        rightIdx++;
        if (rightIdx == hi) {
          endSide = 2;
        }
      }
    }

    // copy the remaining elements of another sequence
    int start;
    int end;
    if (endSide == 1) {
      start = rightIdx;
      end = hi;
    } else {
      start = leftIdx;
      end = mid;
    }
    for (; start < end; start++) {
      setToSorted(start, lo + tmpIdx);
      tmpIdx++;
    }

    // copy from sorting buffer to the original arrays so that they can be further sorted
    // potential speed up: change the place of sorting buffer and origin data between merge
    // iterations
    for (int i = lo; i < hi; i++) {
      setFromSorted(i, i);
    }
  }

  void updateMinTimeAndSorted(long[] time) {
    updateMinTimeAndSorted(time, 0, time.length);
  }

  void updateMinTimeAndSorted(long[] time, int start, int end) {
    int length = time.length;
    long inPutMinTime = Long.MAX_VALUE;
    boolean inputSorted = true;
    for (int i = start; i < end; i++) {
      inPutMinTime = inPutMinTime <= time[i] ? inPutMinTime : time[i];
      if (inputSorted && i < length - 1 && time[i] > time[i + 1]) {
        inputSorted = false;
      }
    }
    minTime = inPutMinTime < minTime ? inPutMinTime : minTime;
    sorted = sorted && inputSorted && (size == 0 || inPutMinTime >= getTime(size - 1));
  }

  /**
   * for log
   */
  public abstract TimeValuePair getTimeValuePair(int index);

  protected abstract TimeValuePair getTimeValuePair(int index, long time,
      Integer floatPrecision, TSEncoding encoding);

  public IPointReader getIterator() {
    return new Ite();
  }

  public IPointReader getIterator(int floatPrecision, TSEncoding encoding) {
    return new Ite(floatPrecision, encoding);
  }

  private class Ite implements IPointReader {

    private TimeValuePair cachedTimeValuePair;
    private boolean hasCachedPair;
    private int cur;
    private Integer floatPrecision;
    private TSEncoding encoding;

    public Ite() {
    }

    public Ite(int floatPrecision, TSEncoding encoding) {
      this.floatPrecision = floatPrecision;
      this.encoding = encoding;
    }

    @Override
    public boolean hasNextTimeValuePair() {
      if (hasCachedPair) {
        return true;
      }

      while (cur < size) {
        long time = getTime(cur);
        if (time < getTimeOffset() || (cur + 1 < size() && (time == getTime(cur + 1)))) {
          cur++;
          continue;
        }
        cachedTimeValuePair = getTimeValuePair(cur, time, floatPrecision, encoding);
        hasCachedPair = true;
        cur++;
        return true;
      }
      return hasCachedPair;
    }

    @Override
    public TimeValuePair nextTimeValuePair() throws IOException {
      if (hasCachedPair || hasNextTimeValuePair()) {
        hasCachedPair = false;
        return cachedTimeValuePair;
      } else {
        throw new IOException("no next time value pair");
      }
    }

    @Override
    public TimeValuePair currentTimeValuePair() {
      return cachedTimeValuePair;
    }

    @Override
    public void close() throws IOException {
      // Do nothing because of this is an in memory object
    }
  }
}
