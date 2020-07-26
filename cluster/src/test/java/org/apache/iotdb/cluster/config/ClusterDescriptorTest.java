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
package org.apache.iotdb.cluster.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.io.FileUtils;
import org.apache.iotdb.db.conf.IoTDBConstant;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ClusterDescriptorTest {

  private String path = "src" + File.separatorChar + "test" + File.separatorChar + "resources";
  private String absoultePath;

  private String testNodesNew = "192.168.130.1:8888,192.168.130.2:8888,192.168.130.3:8888";
  private String testReplicationNew = "5";
  private String testIPNew = "192.168.130.1";
  private String testPortNew = "123456";
  private String testRaftLogPathNew = "/tmp/log";
  private String testRaftSnapshotPathNew = "/tmp/snapshot";
  private String testRaftMetadataPathNew = "/tmp/metadata";
  private String testElectionTimeOutNew = "1234";
  private String testMaxCatchUpLogNumNew = "50000";
  private String testDelaySnapshotNew = "true";
  private String testDelayHoursNew = "1111";
  private String testTaskRedoCountNew = "6";
  private String testTaskTimeoutMSNew = "100000";
  private String testVNodesNew = "4";
  private String testClientNumNew = "400000";
  private String testQueueLenNew = "300000";
  private String testMetadataConsistencyNew = String.valueOf(ClusterConsistencyLevel.STRONG.ordinal());
  private String testDataConsistencyNew = String.valueOf(ClusterConsistencyLevel.STRONG.ordinal());
  private String testConcurrentQPTaskThreadNew = "6";
  private String testConcurrentRaftTaskThreadNew = "11";

  private String[] testNodesOld;
  private int testReplicationOld;
  private String testIPOld;
  private int testPortOld;
  private String testRaftLogPathOld;
  private String testRaftSnapshotPathOld;
  private String testRaftMetadataPathOld;
  private int testElectionTimeOutOld;
  private int testMaxCatchUpLogNumOld;
  private boolean testDelaySnapshotOld;
  private int testDelayHoursOld;
  private int testTaskRedoCountOld;
  private int testTaskTimeoutMSOld;
  private int testVNodesOld;
  private int testClientNumOld;
  private int testQueueLenOld;
  private int testMetadataConsistencyOld;
  private int testDataConsistencyOld;
  private int testConcurrentQPTaskThreadOld;
  private Map<String, String> testConfigMap = new HashMap<String, String>() {
    private static final long serialVersionUID = 7832408957178621132L;

    {
      put("nodes", testNodesNew);
      put("replication", testReplicationNew);
      put("ip", testIPNew);
      put("port", testPortNew);
      put("raft_log_path", testRaftLogPathNew);
      put("raft_snapshot_path", testRaftSnapshotPathNew);
      put("raft_metadata_path", testRaftMetadataPathNew);
      put("election_timeout_ms", testElectionTimeOutNew);
      put("max_catch_up_log_num", testMaxCatchUpLogNumNew);
      put("delay_snapshot", testDelaySnapshotNew);
      put("delay_hours", testDelayHoursNew);
      put("qp_task_redo_count", testTaskRedoCountNew);
      put("qp_task_timeout_ms", testTaskTimeoutMSNew);
      put("num_of_virtual_nodes", testVNodesNew);
      put("concurrent_inner_rpc_client_thread", testClientNumNew);
      put("max_queue_num_of_inner_rpc_client", testQueueLenNew);
      put("read_metadata_consistency_level", testMetadataConsistencyNew);
      put("read_data_consistency_level", testDataConsistencyNew);
      put("concurrent_qp_sub_task_thread", testConcurrentQPTaskThreadNew);
      put("concurrent_raft_task_thread", testConcurrentRaftTaskThreadNew);
    }
  };

  @Before
  public void setUp() throws Exception {
    deleteConfigFile();
    createTestConfigFile();
    storeOldConfig();
  }

  @After
  public void tearDown() throws Exception {
    restoreOldConfig();
    deleteConfigFile();
  }

  @Test
  public void test() throws IOException {
    String oldConfig = System.getProperty(IoTDBConstant.IOTDB_CONF);
    System.setProperty(IoTDBConstant.IOTDB_CONF, absoultePath);
    ClusterDescriptor.getInstance().loadProps();
    ClusterConfig config = ClusterDescriptor.getInstance().getConfig();
    StringBuilder builder = new StringBuilder();
    for (String node : config.getNodes()) {
      builder.append(node.trim());
      builder.append(',');
    }
    assertEquals(testNodesNew, builder.toString().substring(0, builder.length() - 1));
    assertEquals(testReplicationNew, config.getReplication() + "");
    assertEquals(testIPNew, config.getIp() + "");
    assertEquals(testPortNew, config.getPort() + "");
    assertEquals(testRaftLogPathNew, config.getRaftLogPath() + "");
    assertEquals(testRaftSnapshotPathNew, config.getRaftSnapshotPath() + "");
    assertEquals(testRaftMetadataPathNew, config.getRaftMetadataPath() + "");
    assertEquals(testElectionTimeOutNew, config.getElectionTimeoutMs()+"");
    assertEquals(testMaxCatchUpLogNumNew, config.getMaxCatchUpLogNum() + "");
    assertEquals(testDelaySnapshotNew, config.isDelaySnapshot() + "");
    assertEquals(testDelayHoursNew, config.getDelayHours() + "");
    assertEquals(testTaskRedoCountNew, config.getQpTaskRedoCount() + "");
    assertEquals(testTaskTimeoutMSNew, config.getQpTaskTimeout() + "");
    assertEquals(testVNodesNew, config.getNumOfVirtualNodes() + "");
    assertEquals(testClientNumNew, config.getConcurrentInnerRpcClientThread() + "");
    assertEquals(testQueueLenNew, config.getMaxQueueNumOfQPTask() + "");
    assertEquals(testMetadataConsistencyNew, config.getReadMetadataConsistencyLevel() + "");
    assertEquals(testDataConsistencyNew, config.getReadDataConsistencyLevel() + "");
    assertEquals(testConcurrentQPTaskThreadNew, config.getConcurrentQPSubTaskThread() + "");

    if (oldConfig == null) {
      System.clearProperty(IoTDBConstant.IOTDB_CONF);
    } else {
      System.setProperty(IoTDBConstant.IOTDB_CONF, oldConfig);
    }
    config.deleteAllPath();
  }

  private void deleteConfigFile() throws IOException {
    File f = new File(path + File.separatorChar + ClusterConfig.CONFIG_NAME);
    try {
      FileUtils.forceDelete(f);
    } catch (Exception e) {
      // TODO: handle exception
    }

  }

  private void createTestConfigFile() throws IOException {
    File f = new File(path + File.separatorChar + ClusterConfig.CONFIG_NAME);
    absoultePath = f.getParentFile().getAbsolutePath();
    if (f.createNewFile()) {
      FileWriter writer = new FileWriter(f, false);
      for (Entry<String, String> entry : testConfigMap.entrySet()) {
        writer.write(String.format("%s=%s%s", entry.getKey(),
            entry.getValue(), System.getProperty("line.separator")));
      }
      writer.close();
    } else {
      fail();
    }
  }

  private void storeOldConfig() {
    ClusterConfig config = ClusterDescriptor.getInstance().getConfig();
    testNodesOld = config.getNodes();
    testReplicationOld = config.getReplication();
    testIPOld = config.getIp();
    testPortOld = config.getPort();
    testRaftLogPathOld = config.getRaftLogPath();
    testRaftSnapshotPathOld = config.getRaftSnapshotPath();
    testRaftMetadataPathOld = config.getRaftMetadataPath();
    testElectionTimeOutOld = config.getElectionTimeoutMs();
    testMaxCatchUpLogNumOld = config.getMaxCatchUpLogNum();
    testDelaySnapshotOld = config.isDelaySnapshot();
    testDelayHoursOld = config.getDelayHours();
    testTaskRedoCountOld = config.getQpTaskRedoCount();
    testTaskTimeoutMSOld = config.getQpTaskTimeout();
    testVNodesOld = config.getNumOfVirtualNodes();
    testClientNumOld = config.getConcurrentInnerRpcClientThread();
    testQueueLenOld = config.getMaxQueueNumOfQPTask();
    testMetadataConsistencyOld = config.getReadMetadataConsistencyLevel();
    testDataConsistencyOld = config.getReadDataConsistencyLevel();
    testConcurrentQPTaskThreadOld = config.getConcurrentQPSubTaskThread();
  }

  private void restoreOldConfig() {
    ClusterConfig config = ClusterDescriptor.getInstance().getConfig();
    config.setNodes(testNodesOld);
    config.setReplication(testReplicationOld);
    config.setIp(testIPOld);
    config.setPort(testPortOld);
    config.setRaftLogPath(testRaftLogPathOld);
    config.setRaftMetadataPath(testRaftMetadataPathOld);
    config.setRaftSnapshotPath(testRaftSnapshotPathOld);
    config.setElectionTimeoutMs(testElectionTimeOutOld);
    config.setMaxCatchUpLogNum(testMaxCatchUpLogNumOld);
    config.setDelayHours(testDelayHoursOld);
    config.setDelaySnapshot(testDelaySnapshotOld);
    config.setQpTaskRedoCount(testTaskRedoCountOld);
    config.setQpTaskTimeout(testTaskTimeoutMSOld);
    config.setNumOfVirtualNodes(testVNodesOld);
    config.setConcurrentInnerRpcClientThread(testClientNumOld);
    config.setMaxQueueNumOfQPTask(testQueueLenOld);
    config.setReadMetadataConsistencyLevel(testMetadataConsistencyOld);
    config.setReadDataConsistencyLevel(testDataConsistencyOld);
    config.setConcurrentQPSubTaskThread(testConcurrentQPTaskThreadOld);
  }
}
