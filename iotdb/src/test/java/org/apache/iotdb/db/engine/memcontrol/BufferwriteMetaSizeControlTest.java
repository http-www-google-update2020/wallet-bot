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
package org.apache.iotdb.db.engine.memcontrol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.apache.iotdb.db.conf.IoTDBConfig;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.conf.directories.Directories;
import org.apache.iotdb.db.engine.MetadataManagerHelper;
import org.apache.iotdb.db.engine.PathUtils;
import org.apache.iotdb.db.engine.bufferwrite.Action;
import org.apache.iotdb.db.engine.bufferwrite.ActionException;
import org.apache.iotdb.db.engine.bufferwrite.BufferWriteProcessor;
import org.apache.iotdb.db.engine.bufferwrite.FileNodeConstants;
import org.apache.iotdb.db.engine.version.SysTimeVersionController;
import org.apache.iotdb.db.exception.BufferWriteProcessorException;
import org.apache.iotdb.db.utils.EnvironmentUtils;
import org.apache.iotdb.db.utils.FileSchemaUtils;
import org.apache.iotdb.db.utils.MemUtils;
import org.apache.iotdb.tsfile.common.conf.TSFileConfig;
import org.apache.iotdb.tsfile.common.conf.TSFileDescriptor;
import org.apache.iotdb.tsfile.exception.write.WriteProcessException;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BufferwriteMetaSizeControlTest {

  Action bfflushaction = new Action() {

    @Override
    public void act() throws ActionException {

    }
  };

  Action bfcloseaction = new Action() {

    @Override
    public void act() throws ActionException {
    }
  };

  Action fnflushaction = new Action() {

    @Override
    public void act() throws ActionException {

    }
  };

  BufferWriteProcessor processor = null;
  String nsp = "root.vehicle.d0";
  String nsp2 = "root.vehicle.d1";

  private boolean cachePageData = false;
  private int groupSizeInByte;
  private int pageCheckSizeThreshold;
  private int pageSizeInByte;
  private int maxStringLength;
  private long metaSizeThreshold;
  private long memMonitorInterval;
  private TSFileConfig TsFileConf = TSFileDescriptor.getInstance().getConfig();
  private IoTDBConfig dbConfig = IoTDBDescriptor.getInstance().getConfig();

  private boolean skip = !false;

  @Before
  public void setUp() throws Exception {
    // origin value
    groupSizeInByte = TsFileConf.groupSizeInByte;
    pageCheckSizeThreshold = TsFileConf.pageCheckSizeThreshold;
    pageSizeInByte = TsFileConf.pageSizeInByte;
    maxStringLength = TsFileConf.maxStringLength;
    metaSizeThreshold = dbConfig.getBufferwriteFileSizeThreshold();
    memMonitorInterval = dbConfig.getMemMonitorInterval();
    // new value
    TsFileConf.groupSizeInByte = 200000;
    TsFileConf.pageCheckSizeThreshold = 3;
    TsFileConf.pageSizeInByte = 10000;
    TsFileConf.maxStringLength = 2;
    dbConfig.setBufferwriteMetaSizeThreshold(1024 * 1024);
    BasicMemController.getInstance().setCheckInterval(600 * 1000);
    // init metadata
    MetadataManagerHelper.initMetadata();
  }

  @After
  public void tearDown() throws Exception {
    // recovery value
    TsFileConf.groupSizeInByte = groupSizeInByte;
    TsFileConf.pageCheckSizeThreshold = pageCheckSizeThreshold;
    TsFileConf.pageSizeInByte = pageSizeInByte;
    TsFileConf.maxStringLength = maxStringLength;
    dbConfig.setBufferwriteMetaSizeThreshold(metaSizeThreshold);
    BasicMemController.getInstance().setCheckInterval(memMonitorInterval);
    // clean environment
    EnvironmentUtils.cleanEnv();
  }

  @Test
  public void test() throws BufferWriteProcessorException, WriteProcessException {
    if (skip) {
      return;
    }
    String filename = "bufferwritetest";
    new File(filename).delete();

    Map<String, Action> parameters = new HashMap<>();
    parameters.put(FileNodeConstants.BUFFERWRITE_FLUSH_ACTION, bfflushaction);
    parameters.put(FileNodeConstants.BUFFERWRITE_CLOSE_ACTION, bfcloseaction);
    parameters.put(FileNodeConstants.FILENODE_PROCESSOR_FLUSH_ACTION, fnflushaction);

    try {
      processor = new BufferWriteProcessor(Directories.getInstance().getFolderForTest(), nsp,
          filename,
          parameters, SysTimeVersionController.INSTANCE, FileSchemaUtils.constructFileSchema(nsp));
    } catch (BufferWriteProcessorException e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
    File nspdir = PathUtils.getBufferWriteDir(nsp);
    assertEquals(true, nspdir.isDirectory());
    for (int i = 0; i < 1000000; i++) {
      processor.write(nsp, "s1", i * i, TSDataType.INT64, i + "");
      processor.write(nsp2, "s1", i * i, TSDataType.INT64, i + "");
      if (i % 100000 == 0) {
        System.out.println(i + "," + MemUtils.bytesCntToStr(processor.getMetaSize()));
      }
    }
    // wait to flush end
    try {
      Thread.sleep(3000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    assertTrue(processor.getMetaSize() < dbConfig.getBufferwriteFileSizeThreshold());
    processor.close();
    fail("Method unimplemented");

  }
}
