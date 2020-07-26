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

package org.apache.iotdb.db.engine.modification;

import static junit.framework.TestCase.assertTrue;
import static org.apache.iotdb.db.utils.EnvironmentUtils.TEST_QUERY_CONTEXT;
import static org.apache.iotdb.db.utils.EnvironmentUtils.TEST_QUERY_JOB_ID;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.conf.directories.Directories;
import org.apache.iotdb.db.engine.filenode.FileNodeManager;
import org.apache.iotdb.db.engine.modification.io.LocalTextModificationAccessor;
import org.apache.iotdb.db.engine.querycontext.QueryDataSource;
import org.apache.iotdb.db.exception.FileNodeManagerException;
import org.apache.iotdb.db.exception.MetadataArgsErrorException;
import org.apache.iotdb.db.exception.PathErrorException;
import org.apache.iotdb.db.exception.StartupException;
import org.apache.iotdb.db.metadata.MManager;
import org.apache.iotdb.db.query.control.QueryResourceManager;
import org.apache.iotdb.db.utils.EnvironmentUtils;
import org.apache.iotdb.db.utils.TimeValuePair;
import org.apache.iotdb.tsfile.common.conf.TSFileConfig;
import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.read.common.Path;
import org.apache.iotdb.tsfile.read.expression.impl.SingleSeriesExpression;
import org.apache.iotdb.tsfile.write.record.TSRecord;
import org.apache.iotdb.tsfile.write.record.datapoint.DoubleDataPoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DeletionFileNodeTest {

  private String processorName = "root.test";

  private static String[] measurements = new String[10];
  private String dataType = TSDataType.DOUBLE.toString();
  private String encoding = TSEncoding.PLAIN.toString();

  static {
    for (int i = 0; i < 10; i++) {
      measurements[i] = "m" + i;
    }
  }

  @Before
  public void setup() throws MetadataArgsErrorException,
      PathErrorException, IOException, FileNodeManagerException, StartupException {
    EnvironmentUtils.envSetUp();

    MManager.getInstance().setStorageLevelToMTree(processorName);
    for (int i = 0; i < 10; i++) {
      MManager.getInstance().addPathToMTree(processorName + "." + measurements[i], dataType,
          encoding);
      FileNodeManager.getInstance()
          .addTimeSeries(new Path(processorName, measurements[i]), TSDataType.valueOf(dataType),
              TSEncoding.valueOf(encoding), CompressionType.valueOf(TSFileConfig.compressor),
              Collections.emptyMap());
    }
  }

  @After
  public void teardown() throws IOException, FileNodeManagerException {
    EnvironmentUtils.cleanEnv();
  }

  @Test
  public void testDeleteInBufferWriteCache() throws
      FileNodeManagerException {

    for (int i = 1; i <= 100; i++) {
      TSRecord record = new TSRecord(i, processorName);
      for (int j = 0; j < 10; j++) {
        record.addTuple(new DoubleDataPoint(measurements[j], i * 1.0));
      }
      FileNodeManager.getInstance().insert(record, false);
    }

    FileNodeManager.getInstance().delete(processorName, measurements[3], 50);
    FileNodeManager.getInstance().delete(processorName, measurements[4], 50);
    FileNodeManager.getInstance().delete(processorName, measurements[5], 30);
    FileNodeManager.getInstance().delete(processorName, measurements[5], 50);

    SingleSeriesExpression expression = new SingleSeriesExpression(new Path(processorName,
        measurements[5]), null);
    QueryResourceManager.getInstance().beginQueryOfGivenExpression(TEST_QUERY_JOB_ID, expression);
    QueryDataSource dataSource = QueryResourceManager.getInstance()
        .getQueryDataSource(expression.getSeriesPath(), TEST_QUERY_CONTEXT);

    Iterator<TimeValuePair> timeValuePairs =
        dataSource.getSeqDataSource().getReadableChunk().getIterator();
    int count = 0;
    while (timeValuePairs.hasNext()) {
      timeValuePairs.next();
      count++;
    }
    assertEquals(50, count);
    QueryResourceManager.getInstance().endQueryForGivenJob(TEST_QUERY_JOB_ID);
  }

  @Test
  public void testDeleteInBufferWriteFile() throws FileNodeManagerException, IOException {
    for (int i = 1; i <= 100; i++) {
      TSRecord record = new TSRecord(i, processorName);
      for (int j = 0; j < 10; j++) {
        record.addTuple(new DoubleDataPoint(measurements[j], i * 1.0));
      }
      FileNodeManager.getInstance().insert(record, false);
    }
    FileNodeManager.getInstance().closeAll();

    FileNodeManager.getInstance().delete(processorName, measurements[5], 50);
    FileNodeManager.getInstance().delete(processorName, measurements[4], 40);
    FileNodeManager.getInstance().delete(processorName, measurements[3], 30);

    Modification[] realModifications = new Modification[]{
        new Deletion(processorName + "." + measurements[5], 102, 50),
        new Deletion(processorName + "." + measurements[4], 103, 40),
        new Deletion(processorName + "." + measurements[3], 104, 30),
    };

    String fileNodePath = Directories.getInstance().getTsFileFolder(0) + File.separator
        + processorName;
    File fileNodeDir = new File(fileNodePath);
    File[] modFiles = fileNodeDir.listFiles((dir, name)
        -> name.endsWith(ModificationFile.FILE_SUFFIX));
    assertEquals(modFiles.length, 1);

    LocalTextModificationAccessor accessor =
        new LocalTextModificationAccessor(modFiles[0].getPath());
    try {
      Collection<Modification> modifications = accessor.read();
      assertEquals(modifications.size(), 3);
      int i = 0;
      for (Modification modification : modifications) {
        assertTrue(modification.equals(realModifications[i++]));
      }
    } finally {
      accessor.close();
    }
  }

  @Test
  public void testDeleteInOverflowCache() throws FileNodeManagerException {
    // insert into BufferWrite
    for (int i = 101; i <= 200; i++) {
      TSRecord record = new TSRecord(i, processorName);
      for (int j = 0; j < 10; j++) {
        record.addTuple(new DoubleDataPoint(measurements[j], i * 1.0));
      }
      FileNodeManager.getInstance().insert(record, false);
    }
    FileNodeManager.getInstance().closeAll();

    // insert into Overflow
    for (int i = 1; i <= 100; i++) {
      TSRecord record = new TSRecord(i, processorName);
      for (int j = 0; j < 10; j++) {
        record.addTuple(new DoubleDataPoint(measurements[j], i * 1.0));
      }
      FileNodeManager.getInstance().insert(record, false);
    }

    FileNodeManager.getInstance().delete(processorName, measurements[3], 50);
    FileNodeManager.getInstance().delete(processorName, measurements[4], 50);
    FileNodeManager.getInstance().delete(processorName, measurements[5], 30);
    FileNodeManager.getInstance().delete(processorName, measurements[5], 50);

    SingleSeriesExpression expression = new SingleSeriesExpression(new Path(processorName,
        measurements[5]), null);

    QueryResourceManager.getInstance().beginQueryOfGivenExpression(TEST_QUERY_JOB_ID, expression);
    QueryDataSource dataSource = QueryResourceManager.getInstance()
        .getQueryDataSource(expression.getSeriesPath(), TEST_QUERY_CONTEXT);

    Iterator<TimeValuePair> timeValuePairs =
        dataSource.getOverflowSeriesDataSource().getReadableMemChunk().getIterator();
    int count = 0;
    while (timeValuePairs.hasNext()) {
      timeValuePairs.next();
      count++;
    }
    assertEquals(50, count);

    QueryResourceManager.getInstance().endQueryForGivenJob(TEST_QUERY_JOB_ID);
  }

  @Test
  public void testDeleteInOverflowFile() throws FileNodeManagerException, IOException {
    // insert into BufferWrite
    for (int i = 101; i <= 200; i++) {
      TSRecord record = new TSRecord(i, processorName);
      for (int j = 0; j < 10; j++) {
        record.addTuple(new DoubleDataPoint(measurements[j], i * 1.0));
      }
      FileNodeManager.getInstance().insert(record, false);
    }
    FileNodeManager.getInstance().closeAll();

    // insert into Overflow
    for (int i = 1; i <= 100; i++) {
      TSRecord record = new TSRecord(i, processorName);
      for (int j = 0; j < 10; j++) {
        record.addTuple(new DoubleDataPoint(measurements[j], i * 1.0));
      }
      FileNodeManager.getInstance().insert(record, false);
    }
    FileNodeManager.getInstance().closeAll();

    FileNodeManager.getInstance().delete(processorName, measurements[5], 50);
    FileNodeManager.getInstance().delete(processorName, measurements[4], 40);
    FileNodeManager.getInstance().delete(processorName, measurements[3], 30);

    Modification[] realModifications = new Modification[]{
        new Deletion(processorName + "." + measurements[5], 103, 50),
        new Deletion(processorName + "." + measurements[4], 104, 40),
        new Deletion(processorName + "." + measurements[3], 105, 30),
    };

    String fileNodePath = IoTDBDescriptor.getInstance().getConfig().getOverflowDataDir() + File.separator
        + processorName + File.separator + "0" + File.separator;
    File fileNodeDir = new File(fileNodePath);
    File[] modFiles = fileNodeDir.listFiles((dir, name)
        -> name.endsWith(ModificationFile.FILE_SUFFIX));
    assertEquals(modFiles.length, 1);

    LocalTextModificationAccessor accessor =
        new LocalTextModificationAccessor(modFiles[0].getPath());
    Collection<Modification> modifications = accessor.read();
    assertEquals(modifications.size(), 3);
    int i = 0;
    for (Modification modification : modifications) {
      assertTrue(modification.equals(realModifications[i++]));
    }
  }
}
