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
package org.apache.iotdb.cluster.entity.raft;

import com.alipay.remoting.rpc.RpcServer;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.StateMachine;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.codahale.metrics.ConsoleReporter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.iotdb.cluster.config.ClusterConfig;
import org.apache.iotdb.cluster.config.ClusterDescriptor;
import org.apache.iotdb.cluster.entity.service.IService;
import org.apache.iotdb.db.utils.FilePathUtils;

public class RaftService implements IService {

  private ClusterConfig config = ClusterDescriptor.getInstance().getConfig();
  private List<PeerId> peerIdList;
  private Node node;
  private StateMachine fsm;
  private String groupId;
  private RaftGroupService raftGroupService;
  private boolean startRpcServer;

  public RaftService(String groupId, PeerId[] peerIds, PeerId serverId, RpcServer rpcServer, StateMachine fsm, boolean startRpcServer) {
    this.peerIdList = new ArrayList<>(peerIds.length);
    peerIdList.addAll(Arrays.asList(peerIds));
    this.fsm = fsm;
    this.groupId = groupId;
    this.startRpcServer = startRpcServer;
    raftGroupService = new RaftGroupService(groupId, serverId, null, rpcServer);
  }

  @Override
  public void init() {
    NodeOptions nodeOptions = new NodeOptions();
    nodeOptions.setDisableCli(false);
    nodeOptions.setFsm(this.fsm);
    nodeOptions.setLogUri(FilePathUtils.regularizePath(config.getRaftLogPath()) + groupId);
    nodeOptions.setRaftMetaUri(FilePathUtils.regularizePath(config.getRaftMetadataPath()) + groupId);
    nodeOptions.setSnapshotUri(FilePathUtils.regularizePath(config.getRaftSnapshotPath()) + groupId);
    nodeOptions.setElectionTimeoutMs(config.getElectionTimeoutMs());
    nodeOptions.setEnableMetrics(true);
    final Configuration initConf = new Configuration();
    initConf.setPeers(peerIdList);
    nodeOptions.setInitialConf(initConf);
    raftGroupService.setNodeOptions(nodeOptions);
  }

  @Override
  public void start() {
    this.node = raftGroupService.start(startRpcServer);

//    ConsoleReporter reporter = ConsoleReporter.forRegistry(node.getNodeMetrics().getMetricRegistry())
//        .convertRatesTo(TimeUnit.SECONDS)
//        .convertDurationsTo(TimeUnit.MILLISECONDS)
//        .build();
//    reporter.start(30, TimeUnit.SECONDS);
  }

  @Override
  public void stop() {
    raftGroupService.shutdown();
  }

  public List<PeerId> getPeerIdList() {
    return peerIdList;
  }

  public RaftGroupService getRaftGroupService() {
    return raftGroupService;
  }

  public Node getNode() {
    return node;
  }

  public void setNode(Node node) {
    this.node = node;
  }

  public StateMachine getFsm() {
    return fsm;
  }

  public String getGroupId() {
    return groupId;
  }
}
