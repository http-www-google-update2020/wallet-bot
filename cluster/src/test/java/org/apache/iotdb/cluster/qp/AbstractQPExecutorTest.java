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
package org.apache.iotdb.cluster.qp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.iotdb.cluster.config.ClusterConfig;
import org.apache.iotdb.cluster.config.ClusterDescriptor;
import org.apache.iotdb.cluster.qp.executor.ClusterQueryProcessExecutor;
import org.apache.iotdb.cluster.qp.executor.QueryMetadataExecutor;
import org.apache.iotdb.cluster.service.TSServiceClusterImpl;
import org.apache.iotdb.cluster.utils.EnvironmentUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AbstractQPExecutorTest {

  private static final ClusterConfig CLUSTER_CONFIG = ClusterDescriptor.getInstance().getConfig();

  private TSServiceClusterImpl impl;

  private ClusterQueryProcessExecutor queryExecutor;

  private QueryMetadataExecutor queryMetadataExecutor;

  @Before
  public void setUp() throws Exception {
    EnvironmentUtils.envSetUp();
    impl = new TSServiceClusterImpl();
    queryMetadataExecutor = impl.getQueryMetadataExecutor();
    queryExecutor = impl.getQueryDataExecutor();
  }

  @After
  public void tearDown() throws Exception {
    impl.closeClusterService();
    EnvironmentUtils.cleanEnv();
  }

  @Test
  public void setReadMetadataConsistencyLevel() throws Exception {
    assertEquals(CLUSTER_CONFIG.getReadMetadataConsistencyLevel(),
        queryMetadataExecutor.getReadMetadataConsistencyLevel());
    boolean exec;
    exec = impl.execSetConsistencyLevel("set read metadata level to strong");
    assertTrue(exec);
    assertEquals(0, queryMetadataExecutor.getReadMetadataConsistencyLevel());

    exec = impl.execSetConsistencyLevel("show timeseries");
    assertEquals(0, queryMetadataExecutor.getReadMetadataConsistencyLevel());
    assertFalse(exec);

    exec = impl.execSetConsistencyLevel("set read metadata level to weak");
    assertTrue(exec);
    assertEquals(1, queryMetadataExecutor.getReadMetadataConsistencyLevel());

    exec = impl.execSetConsistencyLevel("set READ    metadata level  to STRONG");
    assertTrue(exec);
    assertEquals(0, queryMetadataExecutor.getReadMetadataConsistencyLevel());

    exec = impl.execSetConsistencyLevel("  SET read    metadata level  to WEAK");
    assertTrue(exec);
    assertEquals(1, queryMetadataExecutor.getReadMetadataConsistencyLevel());

    try {
      impl.execSetConsistencyLevel("set read metadata level to -2");
      fail();
    } catch (Exception e) {
      assertEquals("Consistency level -2 not support", e.getMessage());
    }
    assertEquals(1, queryMetadataExecutor.getReadMetadataConsistencyLevel());

    try {
      impl.execSetConsistencyLevel("set read metadata level  to final_consistency");
      fail();
    } catch (Exception e) {
      assertEquals("Consistency level final_consistency not support", e.getMessage());
    }
    assertEquals(1, queryMetadataExecutor.getReadMetadataConsistencyLevel());

    try {
      impl.execSetConsistencyLevel("set read metadata level to 90");
      fail();
    } catch (Exception e) {
      assertEquals("Consistency level 90 not support", e.getMessage());
    }
    assertEquals(1, queryMetadataExecutor.getReadMetadataConsistencyLevel());
  }

  @Test
  public void setReadDataConsistencyLevel() throws Exception {
    assertEquals(CLUSTER_CONFIG.getReadDataConsistencyLevel(),
        queryMetadataExecutor.getReadDataConsistencyLevel());
    boolean exec;
    exec = impl.execSetConsistencyLevel(" set read data level to strong");
    assertTrue(exec);
    assertEquals(0, queryExecutor.getReadDataConsistencyLevel());

    exec = impl.execSetConsistencyLevel("show timeseries");
    assertEquals(0, queryExecutor.getReadDataConsistencyLevel());
    assertFalse(exec);

    exec = impl.execSetConsistencyLevel("set read data level  to weak");
    assertTrue(exec);
    assertEquals(1, queryExecutor.getReadDataConsistencyLevel());

    exec = impl.execSetConsistencyLevel("set READ    data level  to STRONG");
    assertTrue(exec);
    assertEquals(0, queryExecutor.getReadDataConsistencyLevel());

    exec = impl.execSetConsistencyLevel("  SET read    data level  to WEAK");
    assertTrue(exec);
    assertEquals(1, queryExecutor.getReadDataConsistencyLevel());

    try {
      impl.execSetConsistencyLevel("set read data level  to -2");
      fail();
    } catch (Exception e) {
      assertEquals("Consistency level -2 not support", e.getMessage());
    }
    assertEquals(1, queryExecutor.getReadDataConsistencyLevel());

    try {
      impl.execSetConsistencyLevel("set read data level  to 1");
      fail();
    } catch (Exception e) {
      assertEquals("Consistency level 1 not support", e.getMessage());
    }
    assertEquals(1, queryExecutor.getReadDataConsistencyLevel());

    try {
      impl.execSetConsistencyLevel("set read data level  to final_consistency");
      fail();
    } catch (Exception e) {
      assertEquals("Consistency level final_consistency not support", e.getMessage());
    }
    assertEquals(1, queryExecutor.getReadDataConsistencyLevel());

    try {
      impl.execSetConsistencyLevel("set read data level  to 90");
      fail();
    } catch (Exception e) {
      assertEquals("Consistency level 90 not support", e.getMessage());
    }
    assertEquals(1, queryExecutor.getReadDataConsistencyLevel());
  }
}