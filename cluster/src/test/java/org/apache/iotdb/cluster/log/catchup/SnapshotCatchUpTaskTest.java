/*
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

package org.apache.iotdb.cluster.log.catchup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import org.apache.iotdb.cluster.common.EnvironmentUtils;
import org.apache.iotdb.cluster.common.TestClient;
import org.apache.iotdb.cluster.common.TestLog;
import org.apache.iotdb.cluster.common.TestMetaGroupMember;
import org.apache.iotdb.cluster.common.TestSnapshot;
import org.apache.iotdb.cluster.common.TestUtils;
import org.apache.iotdb.cluster.config.ClusterDescriptor;
import org.apache.iotdb.cluster.exception.LeaderUnknownException;
import org.apache.iotdb.cluster.log.Log;
import org.apache.iotdb.cluster.log.Snapshot;
import org.apache.iotdb.cluster.rpc.thrift.AppendEntryRequest;
import org.apache.iotdb.cluster.rpc.thrift.Node;
import org.apache.iotdb.cluster.rpc.thrift.RaftService.AsyncClient;
import org.apache.iotdb.cluster.rpc.thrift.SendSnapshotRequest;
import org.apache.iotdb.cluster.server.NodeCharacter;
import org.apache.iotdb.cluster.server.Response;
import org.apache.iotdb.cluster.server.member.RaftMember;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SnapshotCatchUpTaskTest {

  private List<Log> receivedLogs = new ArrayList<>();
  private Snapshot receivedSnapshot;
  private Node header = new Node();
  private boolean testLeadershipFlag;
  private boolean prevUseAsyncServer;

  private RaftMember sender = new TestMetaGroupMember() {
    @Override
    public AsyncClient getAsyncClient(Node node) {
      return new TestClient() {
        @Override
        public void appendEntry(AppendEntryRequest request,
            AsyncMethodCallback<Long> resultHandler) {
          new Thread(() -> {
            TestLog testLog = new TestLog();
            testLog.deserialize(request.entry);
            receivedLogs.add(testLog);
            resultHandler.onComplete(Response.RESPONSE_AGREE);
          }).start();
        }

        @Override
        public void sendSnapshot(SendSnapshotRequest request, AsyncMethodCallback resultHandler) {
          new Thread(() -> {
            receivedSnapshot = new TestSnapshot();
            receivedSnapshot.deserialize(request.snapshotBytes);
            if (testLeadershipFlag) {
              sender.setCharacter(NodeCharacter.ELECTOR);
            }
            resultHandler.onComplete(null);
          }).start();
        }
      };
    }

    @Override
    public Node getHeader() {
      return header;
    }
  };

  @Before
  public void setUp() {
    prevUseAsyncServer = ClusterDescriptor.getInstance().getConfig().isUseAsyncServer();
    ClusterDescriptor.getInstance().getConfig().setUseAsyncServer(true);
    testLeadershipFlag = false;
    receivedSnapshot = null;
    receivedLogs.clear();
  }

  @After
  public void tearDown() throws Exception {
    sender.stop();
    EnvironmentUtils.cleanAllDir();
    ClusterDescriptor.getInstance().getConfig().setUseAsyncServer(prevUseAsyncServer);
  }

  @Test
  public void testCatchUp() throws InterruptedException, TException, LeaderUnknownException {
    List<Log> logList = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      Log log = new TestLog();
      log.setCurrLogIndex(i);
      log.setCurrLogTerm(i);
      logList.add(log);
    }
    Snapshot snapshot = new TestSnapshot(9989);
    Node receiver = new Node();
    sender.setCharacter(NodeCharacter.LEADER);
    SnapshotCatchUpTask task = new SnapshotCatchUpTask(logList, snapshot, receiver, sender);
    task.call();

    assertEquals(logList, receivedLogs);
    assertEquals(snapshot, receivedSnapshot);
  }

  @Test
  public void testLeadershipLost() {
    testLeadershipFlag = true;
    // the leadership will be lost after sending the snapshot
    List<Log> logList = TestUtils.prepareTestLogs(10);
    Snapshot snapshot = new TestSnapshot(9989);
    Node receiver = new Node();
    sender.setCharacter(NodeCharacter.LEADER);
    LogCatchUpTask task = new SnapshotCatchUpTask(logList, snapshot, receiver, sender);
    try {
      task.call();
      fail("Expected LeaderUnknownException");
    } catch (TException | InterruptedException e) {
      fail(e.getMessage());
    } catch (LeaderUnknownException e) {
      assertEquals("The leader is unknown in this group [Node(ip:192.168.0.0, metaPort:9003, "
          + "nodeIdentifier:0, dataPort:40010), Node(ip:192.168.0.1, metaPort:9003, "
          + "nodeIdentifier:1, dataPort:40010), Node(ip:192.168.0.2, metaPort:9003, "
          + "nodeIdentifier:2, dataPort:40010), Node(ip:192.168.0.3, metaPort:9003, "
          + "nodeIdentifier:3, dataPort:40010), Node(ip:192.168.0.4, metaPort:9003, "
          + "nodeIdentifier:4, dataPort:40010), Node(ip:192.168.0.5, metaPort:9003, "
          + "nodeIdentifier:5, dataPort:40010), Node(ip:192.168.0.6, metaPort:9003, "
          + "nodeIdentifier:6, dataPort:40010), Node(ip:192.168.0.7, metaPort:9003, "
          + "nodeIdentifier:7, dataPort:40010), Node(ip:192.168.0.8, metaPort:9003, "
          + "nodeIdentifier:8, dataPort:40010), Node(ip:192.168.0.9, metaPort:9003, "
          + "nodeIdentifier:9, dataPort:40010)]", e.getMessage());
    }

    assertEquals(snapshot, receivedSnapshot);
    assertTrue(receivedLogs.isEmpty());
  }

  @Test
  public void testNoLeadership() {
    // the leadership is lost from the beginning
    List<Log> logList = TestUtils.prepareTestLogs(10);
    Snapshot snapshot = new TestSnapshot(9989);
    Node receiver = new Node();
    sender.setCharacter(NodeCharacter.ELECTOR);
    LogCatchUpTask task = new SnapshotCatchUpTask(logList, snapshot, receiver, sender);
    try {
      task.call();
      fail("Expected LeaderUnknownException");
    } catch (TException | InterruptedException e) {
      fail(e.getMessage());
    } catch (LeaderUnknownException e) {
      assertEquals("The leader is unknown in this group [Node(ip:192.168.0.0, metaPort:9003, "
          + "nodeIdentifier:0, dataPort:40010), Node(ip:192.168.0.1, metaPort:9003, "
          + "nodeIdentifier:1, dataPort:40010), Node(ip:192.168.0.2, metaPort:9003, "
          + "nodeIdentifier:2, dataPort:40010), Node(ip:192.168.0.3, metaPort:9003, "
          + "nodeIdentifier:3, dataPort:40010), Node(ip:192.168.0.4, metaPort:9003, "
          + "nodeIdentifier:4, dataPort:40010), Node(ip:192.168.0.5, metaPort:9003, "
          + "nodeIdentifier:5, dataPort:40010), Node(ip:192.168.0.6, metaPort:9003, "
          + "nodeIdentifier:6, dataPort:40010), Node(ip:192.168.0.7, metaPort:9003, "
          + "nodeIdentifier:7, dataPort:40010), Node(ip:192.168.0.8, metaPort:9003, "
          + "nodeIdentifier:8, dataPort:40010), Node(ip:192.168.0.9, metaPort:9003, "
          + "nodeIdentifier:9, dataPort:40010)]", e.getMessage());
    }

    assertNull(receivedSnapshot);
    assertTrue(receivedLogs.isEmpty());
  }
}