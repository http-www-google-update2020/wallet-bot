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

package org.apache.iotdb.db.query.reader.sequence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.iotdb.db.engine.querycontext.GlobalSortedSeriesDataSource;
import org.apache.iotdb.db.query.context.QueryContext;
import org.apache.iotdb.db.query.reader.IAggregateReader;
import org.apache.iotdb.db.query.reader.IBatchReader;
import org.apache.iotdb.db.query.reader.mem.MemChunkReader;
import org.apache.iotdb.tsfile.file.header.PageHeader;
import org.apache.iotdb.tsfile.read.common.BatchData;
import org.apache.iotdb.tsfile.read.filter.basic.Filter;

/**
 * <p>
 * A reader for sequentially inserts data，including a list of sealedTsFile, unSealedTsFile and data
 * in MemTable.
 * </p>
 */
public class SequenceDataReader implements IBatchReader, IAggregateReader {

  private List<IAggregateReader> seriesReaders;
  private boolean curReaderInitialized;
  private int nextSeriesReaderIndex;
  private IAggregateReader currentSeriesReader;

  /**
   * init with globalSortedSeriesDataSource, filter, context and isReverse.
   *
   * @param sources data source
   * @param filter null if no filter
   * @param context query context
   * @param isReverse true-traverse chunks from behind forward, false-traverse chunks from front to
   * back.
   */
  public SequenceDataReader(GlobalSortedSeriesDataSource sources, Filter filter,
      QueryContext context, boolean isReverse) throws IOException {
    seriesReaders = new ArrayList<>();

    curReaderInitialized = false;
    nextSeriesReaderIndex = 0;

    if (isReverse) {
      // add data in memTable
      if (sources.hasRawSeriesChunk()) {
        seriesReaders.add(new MemChunkReader(sources.getReadableChunk(), filter));
      }

      // add reader for unSealed TsFile
      if (sources.hasUnsealedTsFile()) {
        seriesReaders.add(new UnSealedTsFileReader(sources.getUnsealedTsFile(), filter, isReverse));
      }

      // add reader for sealed TsFiles
      if (sources.hasSealedTsFiles()) {
        seriesReaders.add(
            new SealedTsFilesReader(sources.getSeriesPath(), sources.getSealedTsFiles(), filter,
                context, isReverse));
      }
    } else {
      // add reader for sealed TsFiles
      if (sources.hasSealedTsFiles()) {
        seriesReaders.add(
            new SealedTsFilesReader(sources.getSeriesPath(), sources.getSealedTsFiles(), filter,
                context, isReverse));
      }

      // add reader for unSealed TsFile
      if (sources.hasUnsealedTsFile()) {
        seriesReaders.add(new UnSealedTsFileReader(sources.getUnsealedTsFile(), filter, isReverse));
      }

      // add data in memTable
      if (sources.hasRawSeriesChunk()) {
        seriesReaders.add(new MemChunkReader(sources.getReadableChunk(), filter));
      }
    }
  }

  /**
   * init with globalSortedSeriesDataSource, filter, context and isReverse.
   */
  public SequenceDataReader(GlobalSortedSeriesDataSource sources, Filter filter,
      QueryContext context) throws IOException {
    this(sources, filter, context, false);
  }

  @Override
  public boolean hasNext() throws IOException {

    if (curReaderInitialized && currentSeriesReader.hasNext()) {
      return true;
    } else {
      curReaderInitialized = false;
    }

    while (nextSeriesReaderIndex < seriesReaders.size()) {
      currentSeriesReader = seriesReaders.get(nextSeriesReaderIndex++);
      if (currentSeriesReader.hasNext()) {
        curReaderInitialized = true;
        return true;
      }
    }
    return false;
  }

  @Override
  public void close() throws IOException {
    for (IBatchReader seriesReader : seriesReaders) {
      seriesReader.close();
    }
  }

  @Override
  public BatchData nextBatch() throws IOException {
    return currentSeriesReader.nextBatch();
  }

  @Override
  public PageHeader nextPageHeader() throws IOException {
    return currentSeriesReader.nextPageHeader();
  }

  @Override
  public void skipPageData() throws IOException {
    currentSeriesReader.skipPageData();
  }
}
