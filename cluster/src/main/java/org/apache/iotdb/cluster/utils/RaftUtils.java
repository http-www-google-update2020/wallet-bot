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
package org.apache.iotdb.cluster.utils;

import com.alipay.remoting.AsyncContext;
import com.alipay.remoting.exception.CodecException;
import com.alipay.remoting.serialization.SerializerManager;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.closure.ReadIndexClosure;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.Task;
import com.alipay.sofa.jraft.util.Bits;
import com.alipay.sofa.jraft.util.OnlyForTest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.iotdb.cluster.config.ClusterConfig;
import org.apache.iotdb.cluster.config.ClusterDescriptor;
import org.apache.iotdb.cluster.entity.Server;
import org.apache.iotdb.cluster.entity.raft.DataPartitionRaftHolder;
import org.apache.iotdb.cluster.entity.raft.MetadataRaftHolder;
import org.apache.iotdb.cluster.entity.raft.MetadataStateManchine;
import org.apache.iotdb.cluster.entity.raft.RaftService;
import org.apache.iotdb.cluster.exception.RaftConnectionException;
import org.apache.iotdb.cluster.qp.task.QPTask;
import org.apache.iotdb.cluster.qp.task.QPTask.TaskState;
import org.apache.iotdb.cluster.qp.task.SingleQPTask;
import org.apache.iotdb.cluster.rpc.raft.closure.ResponseClosure;
import org.apache.iotdb.cluster.rpc.raft.impl.RaftNodeAsClientManager;
import org.apache.iotdb.cluster.rpc.raft.request.BasicNonQueryRequest;
import org.apache.iotdb.cluster.rpc.raft.request.BasicRequest;
import org.apache.iotdb.cluster.rpc.raft.response.BasicResponse;
import org.apache.iotdb.cluster.rpc.raft.response.nonquery.DataGroupNonQueryResponse;
import org.apache.iotdb.cluster.rpc.raft.response.nonquery.MetaGroupNonQueryResponse;
import org.apache.iotdb.cluster.utils.hash.PhysicalNode;
import org.apache.iotdb.cluster.utils.hash.Router;
import org.apache.iotdb.cluster.utils.hash.VirtualNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RaftUtils {

  private static final ClusterConfig CLUSTER_CONFIG = ClusterDescriptor.getInstance().getConfig();

  private static final Logger LOGGER = LoggerFactory.getLogger(RaftUtils.class);
  private static final Server server = Server.getInstance();
  private static final Router router = Router.getInstance();
  private static final AtomicInteger requestId = new AtomicInteger(0);
  private static final ClusterConfig config = ClusterDescriptor.getInstance().getConfig();
  /**
   * Raft as client manager.
   */
  private static final RaftNodeAsClientManager CLIENT_MANAGER = RaftNodeAsClientManager
      .getInstance();

  /**
   * The cache will be update in two case: 1. When @onLeaderStart() method of state machine is
   * called, the cache will be update. 2. When @getLocalLeaderPeerID() in this class is called and
   * cache don't have the key, it's will get random peer and update. 3. When @redirected of
   * BasicRequest is true, the task will be retry and the cache will update.
   */
  private static final ConcurrentHashMap<String, PeerId> groupLeaderCache = new ConcurrentHashMap<>();

  private static ThreadLocal<Map<String, Integer>> nodeIndexMap = ThreadLocal.withInitial(() -> {
    Map<String, Integer> map = new HashMap<>();
    router.getAllGroupId().forEach(groupId -> {
      PhysicalNode[] physicalNodes = router.getNodesByGroupId(groupId);
      map.put(groupId, getRandomInt(physicalNodes.length));
    });
    return map;
  });

  private RaftUtils() {
  }

  /**
   * Get peer ID in order
   *
   * @return node id
   */
  public static PeerId getPeerIDInOrder(String groupId) {
    int index;
    PeerId peerId;
    int len;
    if (groupId.equals(ClusterConfig.METADATA_GROUP_ID)) {
      RaftService service = (RaftService) server.getMetadataHolder().getService();
      List<PeerId> peerIdList = service.getPeerIdList();
      len = peerIdList.size();
      index = nodeIndexMap.get().getOrDefault(groupId, getRandomInt(peerIdList.size()));
      peerId = peerIdList.get(index);
    } else {
      PhysicalNode[] physicalNodes = router.getNodesByGroupId(groupId);
      len = physicalNodes.length;
      index = nodeIndexMap.get().getOrDefault(groupId, getRandomInt(physicalNodes.length));
      peerId = getPeerIDFrom(physicalNodes[index]);
    }
    nodeIndexMap.get().put(groupId, (index + 1) % len);

    return peerId;
  }

  /**
   * Get peer id to send request. If groupLeaderCache has the group id, then return leader id of the
   * group.Otherwise, random get a peer of the group.
   *
   * @return leader id
   */
  public static PeerId getLocalLeaderPeerID(String groupId) {
    if (!groupLeaderCache.containsKey(groupId)) {
      PeerId randomPeerId = getRandomPeerID(groupId);
      groupLeaderCache.put(groupId, randomPeerId);
    }
    PeerId leader = groupLeaderCache.get(groupId);
    LOGGER.debug("Get local cached leader {} of group {}.", leader, groupId);
    return leader;
  }

  /**
   * Get random peer id
   */
  public static PeerId getRandomPeerID(String groupId) {
    return getRandomPeerID(groupId, server, router);
  }

  /**
   * Get random peer id
   */
  public static PeerId getRandomPeerID(String groupId, Server server, Router router) {
    List<PeerId> peerIdList = getPeerIDList(groupId, server, router);
    return peerIdList.get(getRandomInt(peerIdList.size()));
  }

  /**
   * Get peer id list by groupid
   */
  public static List<PeerId> getPeerIDList(String groupId, Server server, Router router) {
    List<PeerId> peerIdList = new ArrayList<>();
    if (groupId.equals(ClusterConfig.METADATA_GROUP_ID)) {
      RaftService service = (RaftService) server.getMetadataHolder().getService();
      peerIdList.addAll(service.getPeerIdList());
    } else {
      PhysicalNode[] physicalNodes = router.getNodesByGroupId(groupId);
      for (PhysicalNode node : physicalNodes) {
        peerIdList.add(getPeerIDFrom(node));
      }
    }
    return peerIdList;
  }

  /**
   * Get random int from [0, bound).
   */
  public static int getRandomInt(int bound) {
    return ThreadLocalRandom.current().nextInt(bound);
  }

  public static PeerId getPeerIDFrom(PhysicalNode node) {
    return new PeerId(node.getIp(), node.getPort());
  }

  public static PhysicalNode getPhysicalNodeFrom(PeerId peer) {
    return new PhysicalNode(peer.getIp(), peer.getPort());
  }

  /**
   * @param nodes each node string is in the format of "ip:port:idx",
   */
  public static PeerId[] convertStringArrayToPeerIdArray(String[] nodes) {
    PeerId[] peerIds = new PeerId[nodes.length];
    for (int i = 0; i < nodes.length; i++) {
      peerIds[i] = PeerId.parsePeer(nodes[i]);
    }
    return peerIds;
  }

  public static PhysicalNode[] getPhysicalNodeArrayFrom(PeerId[] peerIds) {
    PhysicalNode[] physicalNodes = new PhysicalNode[peerIds.length];
    for (int i = 0; i < peerIds.length; i++) {
      physicalNodes[i] = getPhysicalNodeFrom(peerIds[i]);
    }
    return physicalNodes;
  }

  public static PeerId[] getPeerIdArrayFrom(PhysicalNode[] physicalNodes) {
    PeerId[] peerIds = new PeerId[physicalNodes.length];
    for (int i = 0; i < physicalNodes.length; i++) {
      peerIds[i] = getPeerIDFrom(physicalNodes[i]);
    }
    return peerIds;
  }

  /**
   * Update raft group leader
   *
   * @param groupId group id
   * @param peerId leader id
   */
  public static void updateRaftGroupLeader(String groupId, PeerId peerId) {
    groupLeaderCache.put(groupId, peerId);
    LOGGER.info("group leader cache:{}", groupLeaderCache);
  }

  /**
   * Remove cached raft group leader if occurs exception in the process of executing qp task.
   *
   * @param groupId data group id
   */
  public static void removeCachedRaftGroupLeader(String groupId) {
    groupLeaderCache.remove(groupId);
  }

  @OnlyForTest
  public static void clearRaftGroupLeader() {
    groupLeaderCache.clear();
  }

  /**
   * Execute raft task for local processor
   *
   * @param service raft service
   */
  public static boolean executeRaftTaskForLocalProcessor(RaftService service, QPTask qpTask,
      BasicResponse response) throws InterruptedException {
    BasicRequest request = qpTask.getRequest();

    Task task = new Task();
    ResponseClosure closure = new ResponseClosure(response, status -> {
      response.addResult(status.isOk());
      if (!status.isOk()) {
        response.setErrorMsg(status.getErrorMsg());
      }
      qpTask.receive(response);
    });
    task.setDone(closure);
    try {
      task.setData(ByteBuffer
          .wrap(SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(request)));
    } catch (CodecException e) {
      return false;
    }
    service.getNode().apply(task);
    qpTask.await();
    return qpTask.getResponse() != null && qpTask.getResponse().isSuccess();
  }


  /**
   * Execute raft task for rpc processor
   *
   * @param service raft service
   */
  public static void executeRaftTaskForRpcProcessor(RaftService service, AsyncContext asyncContext,
      BasicNonQueryRequest request, BasicResponse response) {
    final Task task = new Task();
    ResponseClosure closure = new ResponseClosure(response, status -> {
      response.addResult(status.isOk());
      if (!status.isOk()) {
        response.setErrorMsg(status.getErrorMsg());
      }
      asyncContext.sendResponse(response);
    });
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          String.format("Processor batch size() : %d", request.getPhysicalPlanBytes().size()));
    }
    task.setDone(closure);
    try {
      task.setData(ByteBuffer
          .wrap(SerializerManager.getSerializer(SerializerManager.Hessian2)
              .serialize(request)));
      service.getNode().apply(task);
    } catch (final CodecException e) {
      response.setErrorMsg(e.getMessage());
      response.addResult(false);
      asyncContext.sendResponse(response);
    }
  }

  /**
   * Get read index request id
   */
  public static int getReadIndexRequestId() {
    return requestId.incrementAndGet();
  }

  /**
   * Get data partition raft holder by group id
   */
  public static DataPartitionRaftHolder getDataPartitonRaftHolder(String groupId) {
    return (DataPartitionRaftHolder) server.getDataPartitionHolderMap().get(groupId);
  }

  /**
   * Get metadata raft holder
   */
  public static MetadataRaftHolder getMetadataRaftHolder() {
    return (MetadataRaftHolder) server.getMetadataHolder();
  }

  /**
   * Create a new raft request context by request id
   */
  public static byte[] createRaftRequestContext() {
    final byte[] reqContext = new byte[4];
    Bits.putInt(reqContext, 0, RaftUtils.getReadIndexRequestId());
    return reqContext;
  }

  /**
   * Handle null-read process in metadata group if the request is to set path.
   */
  public static void handleNullReadToMetaGroup(Status status) {
    SingleQPTask nullReadTask = new SingleQPTask(false, null);
    handleNullReadToMetaGroup(status, server, nullReadTask);
  }

  public static void handleNullReadToMetaGroup(Status status, Server server,
      SingleQPTask nullReadTask) {
    try {
      LOGGER.debug("Handle null-read in meta group for metadata request.");
      final byte[] reqContext = RaftUtils.createRaftRequestContext();
      MetadataRaftHolder metadataRaftHolder = (MetadataRaftHolder) server.getMetadataHolder();
      ((RaftService) metadataRaftHolder.getService()).getNode()
          .readIndex(reqContext, new ReadIndexClosure() {
            @Override
            public void run(Status status, long index, byte[] reqCtx) {
              BasicResponse response = MetaGroupNonQueryResponse
                  .createEmptyResponse(ClusterConfig.METADATA_GROUP_ID);
              if (!status.isOk()) {
                status.setCode(-1);
                status.setErrorMsg(status.getErrorMsg());
              }
              nullReadTask.receive(response);
            }
          });
      nullReadTask.await();
    } catch (InterruptedException e) {
      LOGGER.warn("Exception {} occurs while handling null read to metadata group.", e);
      status.setCode(-1);
      status.setErrorMsg(e.getMessage());
    }
  }

  /**
   * Handle null-read process in data group while reading process
   */
  public static void handleNullReadToDataGroup(Status status, String groupId) {
    SingleQPTask nullReadTask = new SingleQPTask(false, null);
    handleNullReadToDataGroup(status, server, nullReadTask, groupId);
  }

  private static void handleNullReadToDataGroup(Status status, Server server,
      SingleQPTask nullReadTask, String groupId) {
    try {
      LOGGER.debug("Handle null-read in data group for reading.");
      final byte[] reqContext = RaftUtils.createRaftRequestContext();
      DataPartitionRaftHolder dataPartitionRaftHolder = (DataPartitionRaftHolder) server
          .getDataPartitionHolder(groupId);
      ((RaftService) dataPartitionRaftHolder.getService()).getNode()
          .readIndex(reqContext, new ReadIndexClosure() {
            @Override
            public void run(Status status, long index, byte[] reqCtx) {
              BasicResponse response = DataGroupNonQueryResponse
                  .createEmptyResponse(groupId);
              if (!status.isOk()) {
                status.setCode(-1);
                status.setErrorMsg(status.getErrorMsg());
              }
              nullReadTask.receive(response);
            }
          });
      nullReadTask.await();
    } catch (InterruptedException e) {
      status.setCode(-1);
      status.setErrorMsg(e.getMessage());
    }
  }

  public static Status createErrorStatus(String errorMsg) {
    Status status = new Status();
    status.setErrorMsg(errorMsg);
    status.setCode(-1);
    return status;
  }

  public static Map<String, PeerId> getGroupLeaderCache() {
    return groupLeaderCache;
  }

  public static Map<Integer, String> getPhysicalRing() {
    SortedMap<Integer, PhysicalNode> hashNodeMap = router.getPhysicalRing();
    Map<Integer, String> res = new LinkedHashMap<>();
    hashNodeMap.forEach((key, value) -> res.put(key, value.getIp()));
    return res;
  }

  public static Map<Integer, String> getVirtualRing() {
    SortedMap<Integer, VirtualNode> hashNodeMap = router.getVirtualRing();
    Map<Integer, String> res = new LinkedHashMap<>();
    hashNodeMap.forEach((key, value) -> res.put(key, value.getPhysicalNode().getIp()));
    return res;
  }

  /**
   * Get data partitions that input node belongs to.
   *
   * @param ip node ip
   * @return key: node ips of one data partition, value: storage group paths that belong to this
   * data partition
   */
  public static Map<String[], String[]> getDataPartitionOfNode(String ip) {
    return getDataPartitionOfNode(ip, config.getPort());
  }

  public static Map<String[], String[]> getDataPartitionOfNode(String ip, int port) {
    return getDataPartitionOfNode(ip, port, server, router);
  }

  public static Map<String[], String[]> getDataPartitionOfNode(String ip, int port, Server server,
      Router router) {
    PhysicalNode[][] groups = router.getGroupsNodes(ip, port);
    if (groups == null) {
      return null;
    }

    Map<String, List<String>> groupSGMap = new LinkedHashMap<>();
    for (int i = 0; i < groups.length; i++) {
      groupSGMap.put(generateStringKey(groups[i]), new ArrayList<>());
    }
    Set<String> allSGList = ((MetadataStateManchine) ((RaftService) server.getMetadataHolder()
        .getService()).getFsm()).getAllStorageGroups();
    for (String sg : allSGList) {
      String key = generateStringKey(router.routeGroup(sg));
      if (groupSGMap.containsKey(key)) {
        groupSGMap.get(key).add(sg);
      }
    }

    String[][] groupIps = new String[groups.length][];
    for (int i = 0; i < groups.length; i++) {
      groupIps[i] = new String[groups[i].length];
      for (int j = 0; j < groups[i].length; j++) {
        groupIps[i][j] = groups[i][j].getIp();
      }
    }

    Map<String[], String[]> res = new HashMap<>();
    int index = 0;
    for (Entry<String, List<String>> entry : groupSGMap.entrySet()) {
      res.put(groupIps[index], entry.getValue().toArray(new String[entry.getValue().size()]));
      index++;
    }
    return res;
  }

  private static String generateStringKey(PhysicalNode[] nodes) {
    if (nodes == null || nodes.length == 0) {
      return "";
    }
    Arrays.sort(nodes, Comparator.comparing(PhysicalNode::toString));
    StringBuilder builder = new StringBuilder();
    builder.append(nodes[0]);
    for (int i = 1; i < nodes.length; i++) {
      builder.append('#').append(nodes[i]);
    }
    return builder.toString();
  }

}
