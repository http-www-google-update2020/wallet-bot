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
package org.apache.iotdb.cluster.concurrent.pool;

import static org.junit.Assert.assertEquals;

import org.apache.iotdb.cluster.config.ClusterConfig;
import org.apache.iotdb.cluster.config.ClusterDescriptor;
import org.apache.iotdb.cluster.utils.EnvironmentUtils;
import org.apache.iotdb.db.exception.ProcessorException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class QPTaskThreadManagerTest {

  private QPTaskThreadManager qpTaskThreadManager = QPTaskThreadManager.getInstance();

  private ClusterConfig clusterConfig = ClusterDescriptor.getInstance().getConfig();

  private int blockTimeOut = 10;

  private volatile boolean mark = true;

  private Runnable testRunnable = () -> {
    while(mark){}
  };

  private Runnable changeMark = () -> {
    try {
      Thread.sleep(blockTimeOut);
      mark = false;
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  };

  @Before
  public void setUp() throws Exception {
    EnvironmentUtils.envSetUp();
  }

  @After
  public void tearDown() throws Exception {
    EnvironmentUtils.cleanEnv();
  }

  @Test
  public void testSubmitAndClose() throws InterruptedException {

    assertEquals(clusterConfig.getConcurrentQPSubTaskThread(), qpTaskThreadManager.getThreadPoolSize());

    int threadPoolSize = qpTaskThreadManager.getThreadPoolSize();
    // test thread num
    for (int i = 1; i <= threadPoolSize + 2; i++) {
      qpTaskThreadManager.submit(testRunnable);
      Thread.sleep(10);
      assertEquals(Math.min(i, threadPoolSize), qpTaskThreadManager.getActiveCnt());
    }

    // test close
    try {
      new Thread(changeMark).start();
      qpTaskThreadManager.close(true, blockTimeOut);
    } catch (ProcessorException e) {
      assertEquals("qp-task-thread-manager thread pool doesn't exit after 10 ms", e.getMessage());
    }
  }
}