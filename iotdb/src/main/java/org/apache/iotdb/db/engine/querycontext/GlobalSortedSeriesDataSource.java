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
package org.apache.iotdb.db.engine.querycontext;

import java.util.List;
import org.apache.iotdb.db.engine.filenode.TsFileResource;
import org.apache.iotdb.tsfile.read.common.Path;

public class GlobalSortedSeriesDataSource {

  private Path seriesPath;

  // sealed tsfile
  private List<TsFileResource> sealedTsFiles;

  // unsealed tsfile
  private UnsealedTsFile unsealedTsFile;

  // seq mem-table
  private ReadOnlyMemChunk readableChunk;

  public GlobalSortedSeriesDataSource(Path seriesPath, List<TsFileResource> sealedTsFiles,
      UnsealedTsFile unsealedTsFile,
      ReadOnlyMemChunk readableChunk) {
    this.seriesPath = seriesPath;
    this.sealedTsFiles = sealedTsFiles;
    this.unsealedTsFile = unsealedTsFile;

    this.readableChunk = readableChunk;
  }

  public boolean hasSealedTsFiles() {
    return sealedTsFiles != null && !sealedTsFiles.isEmpty();
  }

  public List<TsFileResource> getSealedTsFiles() {
    return sealedTsFiles;
  }

  public void setSealedTsFiles(List<TsFileResource> sealedTsFiles) {
    this.sealedTsFiles = sealedTsFiles;
  }

  public boolean hasUnsealedTsFile() {
    return unsealedTsFile != null;
  }

  public UnsealedTsFile getUnsealedTsFile() {
    return unsealedTsFile;
  }

  public void setUnsealedTsFile(UnsealedTsFile unsealedTsFile) {
    this.unsealedTsFile = unsealedTsFile;
  }

  public boolean hasRawSeriesChunk() {
    return readableChunk != null;
  }

  public ReadOnlyMemChunk getReadableChunk() {
    return readableChunk;
  }

  public void setReadableChunk(ReadOnlyMemChunk readableChunk) {
    this.readableChunk = readableChunk;
  }

  public Path getSeriesPath() {
    return seriesPath;
  }

  public void setSeriesPath(Path seriesPath) {
    this.seriesPath = seriesPath;
  }

}
