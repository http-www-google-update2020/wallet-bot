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
package org.apache.iotdb.db.writelog.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.NoSuchElementException;
import java.util.zip.CRC32;
import org.apache.iotdb.db.qp.physical.PhysicalPlan;
import org.apache.iotdb.db.qp.physical.transfer.PhysicalPlanLogTransfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RAFLogReader implements ILogReader {

  private static final Logger logger = LoggerFactory.getLogger(RAFLogReader.class);
  public static final int LEAST_LOG_SIZE = 12; // size + checksum
  private RandomAccessFile logRaf;
  private String filepath;
  private int bufferSize = 4 * 1024 * 1024;
  private byte[] buffer = new byte[bufferSize];
  private CRC32 checkSummer = new CRC32();
  private PhysicalPlan planBuffer = null;

  public RAFLogReader() {
    // allowed to construct RAFLogReader without input.
  }

  public RAFLogReader(File logFile) throws FileNotFoundException {
    open(logFile);
  }

  @Override
  public boolean hasNext() throws IOException{
    if (planBuffer != null) {
      return true;
    }

    if (logRaf.getFilePointer() + LEAST_LOG_SIZE > logRaf.length()) {
      return false;
    }

    int logSize = logRaf.readInt();
    if (logSize > bufferSize) {
      bufferSize = logSize;
      buffer = new byte[bufferSize];
    }
    final long checkSum = logRaf.readLong();
    logRaf.read(buffer, 0, logSize);
    checkSummer.reset();
    checkSummer.update(buffer, 0, logSize);
    if (checkSummer.getValue() != checkSum) {
      throw new IOException("The check sum is incorrect!");
    }
    planBuffer = PhysicalPlanLogTransfer.logToOperator(buffer);
    return true;
  }

  @Override
  public PhysicalPlan next() throws IOException {
    if (!hasNext()){
      throw new NoSuchElementException();
    }

    PhysicalPlan ret = planBuffer;
    planBuffer = null;
    return ret;
  }

  @Override
  public void close() {
    if (logRaf != null) {
      try {
        logRaf.close();
      } catch (IOException e) {
        logger.error("Cannot close log file {}", filepath, e);
      }
    }
  }

  @Override
  public void open(File logFile) throws FileNotFoundException {
    logRaf = new RandomAccessFile(logFile, "r");
    this.filepath = logFile.getPath();
  }
}
