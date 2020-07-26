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
package org.apache.iotdb.db.metadata;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.iotdb.db.exception.PathErrorException;
import org.apache.iotdb.db.utils.EnvironmentUtils;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.write.schema.MeasurementSchema;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MetadataTest {

  @Before
  public void setUp() throws Exception {
  }

  @After
  public void tearDown() throws Exception {
    EnvironmentUtils.cleanEnv();
  }

  @Test
  public void testCombineMetadatas() {
    MManager manager = MManager.getInstance();

    try {
      manager.setStorageLevelToMTree("root.t.d1");
      manager.addPathToMTree("root.t.d1.s0", "INT32", "RLE");
      manager.addPathToMTree("root.t.d1.s1", "DOUBLE", "RLE");
      manager.setStorageLevelToMTree("root.t.d2");
      manager.addPathToMTree("root.t.d2.s1", "DOUBLE", "RLE");
      Metadata metadata1 = manager.getMetadata();

      manager.clear();

      manager.setStorageLevelToMTree("root.t.d3");
      manager.addPathToMTree("root.t.d3.s1", "DOUBLE", "RLE");
      manager.addPathToMTree("root.t.d3.s2", "TEXT", "RLE");
      manager.setStorageLevelToMTree("root.t1.d1");
      manager.addPathToMTree("root.t1.d1.s1", "DOUBLE", "RLE");
      manager.addPathToMTree("root.t1.d1.s2", "TEXT", "RLE");
      Metadata metadata2 = manager.getMetadata();

      manager.clear();

      manager.setStorageLevelToMTree("root.t.d1");
      manager.addPathToMTree("root.t.d1.s0", "INT32", "RLE");
      manager.addPathToMTree("root.t.d1.s1", "DOUBLE", "RLE");
      manager.setStorageLevelToMTree("root.t.d2");
      manager.addPathToMTree("root.t.d2.s1", "DOUBLE", "RLE");
      manager.setStorageLevelToMTree("root.t.d3");
      manager.addPathToMTree("root.t.d3.s1", "DOUBLE", "RLE");
      manager.addPathToMTree("root.t.d3.s2", "TEXT", "RLE");
      manager.setStorageLevelToMTree("root.t1.d1");
      manager.addPathToMTree("root.t1.d1.s1", "DOUBLE", "RLE");
      manager.addPathToMTree("root.t1.d1.s2", "TEXT", "RLE");
      Metadata metadata = manager.getMetadata();

      Metadata combineMetadata = Metadata.combineMetadatas(new Metadata[]{metadata1, metadata2});
      assertTrue(metadata.equals(combineMetadata));
    } catch (PathErrorException | IOException e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }
}