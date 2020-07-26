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

package org.apache.iotdb.db.engine.bufferwrite;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.iotdb.db.conf.directories.Directories;
import org.apache.iotdb.db.engine.MetadataManagerHelper;
import org.apache.iotdb.db.engine.querycontext.ReadOnlyMemChunk;
import org.apache.iotdb.db.engine.version.SysTimeVersionController;
import org.apache.iotdb.db.exception.BufferWriteProcessorException;
import org.apache.iotdb.db.utils.EnvironmentUtils;
import org.apache.iotdb.db.utils.FileSchemaUtils;
import org.apache.iotdb.db.utils.TimeValuePair;
import org.apache.iotdb.tsfile.exception.write.WriteProcessException;
import org.apache.iotdb.tsfile.file.metadata.ChunkMetaData;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.utils.Pair;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BufferWriteProcessorNewTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(BufferWriteProcessorNewTest.class);
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
  Map<String, Action> parameters = new HashMap<>();
  private String processorName = "root.vehicle.d0";
  private String measurementId = "s0";
  private TSDataType dataType = TSDataType.INT32;
  private Map<String, String> props = Collections.emptyMap();
  private BufferWriteProcessor bufferwrite;
  private String filename = "tsfile";

  @Before
  public void setUp() throws Exception {
    parameters.put(FileNodeConstants.BUFFERWRITE_FLUSH_ACTION, bfflushaction);
    parameters.put(FileNodeConstants.BUFFERWRITE_CLOSE_ACTION, bfcloseaction);
    parameters.put(FileNodeConstants.FILENODE_PROCESSOR_FLUSH_ACTION, fnflushaction);
    MetadataManagerHelper.initMetadata();
    EnvironmentUtils.envSetUp();
  }

  @After
  public void tearDown() throws Exception {
    bufferwrite.close();
    EnvironmentUtils.cleanEnv();
  }

  @Test
  public void testWriteAndFlush()
      throws BufferWriteProcessorException, WriteProcessException, IOException, InterruptedException {
    bufferwrite = new BufferWriteProcessor(Directories.getInstance().getFolderForTest(),
        processorName, filename,
        parameters, SysTimeVersionController.INSTANCE,
        FileSchemaUtils.constructFileSchema(processorName));
    assertEquals(processorName + File.separator + filename, bufferwrite.getFileRelativePath());
    assertTrue(bufferwrite.isNewProcessor());
    bufferwrite.setNewProcessor(false);
    assertFalse(bufferwrite.isNewProcessor());
    Pair<ReadOnlyMemChunk, List<ChunkMetaData>> pair = bufferwrite
        .queryBufferWriteData(processorName,
            measurementId, dataType, props);
    ReadOnlyMemChunk left = pair.left;
    List<ChunkMetaData> right = pair.right;
    assertTrue(left.isEmpty());
    assertEquals(0, right.size());
    for (int i = 1; i <= 100; i++) {
      bufferwrite.write(processorName, measurementId, i, dataType, String.valueOf(i));
    }
    // query data in memory
    pair = bufferwrite.queryBufferWriteData(processorName, measurementId, dataType, props);
    left = pair.left;
    right = pair.right;
    assertFalse(left.isEmpty());
    int num = 1;
    Iterator<TimeValuePair> iterator = left.getIterator();
    for (; num <= 100; num++) {
      iterator.hasNext();
      TimeValuePair timeValuePair = iterator.next();
      assertEquals(num, timeValuePair.getTimestamp());
      assertEquals(num, timeValuePair.getValue().getInt());
    }
    assertFalse(bufferwrite.isFlush());
    long lastFlushTime = bufferwrite.getLastFlushTime();
    // flush asynchronously
    bufferwrite.flush();
    assertTrue(bufferwrite.getLastFlushTime() != lastFlushTime);
    assertTrue(bufferwrite.canBeClosed());
    // waiting for the end of flush.
    try {
      bufferwrite.getFlushFuture().get(10, TimeUnit.SECONDS);
    } catch (Exception e) {
      //because UT uses a mock flush operation, 10 seconds should be enough.
      LOGGER.error(e.getMessage(), e);
      Assert.fail("mock flush spends more than 10 seconds... "
          + "Please modify the value or change a better test environment");
    }
    pair = bufferwrite.queryBufferWriteData(processorName, measurementId, dataType, props);
    left = pair.left;
    right = pair.right;
    assertTrue(left.isEmpty());
    assertEquals(1, right.size());
    assertEquals(measurementId, right.get(0).getMeasurementUid());
    assertEquals(dataType, right.get(0).getTsDataType());

    // test recovery
    BufferWriteProcessor bufferWriteProcessor = new BufferWriteProcessor(
        Directories.getInstance().getFolderForTest(), processorName, filename, parameters,
        SysTimeVersionController.INSTANCE,
        FileSchemaUtils.constructFileSchema(processorName));
    pair = bufferWriteProcessor.queryBufferWriteData(processorName, measurementId, dataType, props);
    left = pair.left;
    right = pair.right;
    assertTrue(left.isEmpty());
    assertEquals(1, right.size());
    assertEquals(measurementId, right.get(0).getMeasurementUid());
    assertEquals(dataType, right.get(0).getTsDataType());
    bufferWriteProcessor.close();
  }
}
