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
import com.alipay.sofa.jraft.core.NodeImpl;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.Task;
import com.alipay.sofa.jraft.util.Bits;
import com.alipay.sofa.jraft.util.OnlyForTest;
import com.codahale.metrics.Gauge;
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
import java.util.TreeMap;
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
import org.apache.iotdb.cluster.query.manager.coordinatornode.ClusterRpcQueryManager;
import org.apache.iotdb.cluster.rpc.raft.closure.ResponseClosure;
import org.apache.iotdb.cluster.rpc.raft.impl.RaftNodeAsClientManager;
import org.apache.iotdb.cluster.rpc.raft.request.BasicNonQueryRequest;
import org.apache.iotdb.cluster.rpc.raft.request.BasicRequest;
import org.apache.iotdb.cluster.rpc.raft.request.querymetric.QueryJobNumRequest;
import org.apache.iotdb.cluster.rpc.raft.request.querymetric.QueryLeaderRequest;
import org.apache.iotdb.cluster.rpc.raft.request.querymetric.QueryMetricRequest;
import org.apache.iotdb.cluster.rpc.raft.request.querymetric.QueryStatusRequest;
import org.apache.iotdb.cluster.rpc.raft.response.BasicResponse;
import org.apache.iotdb.cluster.rpc.raft.response.nonquery.DataGroupNonQueryResponse;
import org.apache.iotdb.cluster.rpc.raft.response.nonquery.MetaGroupNonQueryResponse;
import org.apache.iotdb.cluster.rpc.raft.response.querymetric.QueryJobNumResponse;
import org.apache.iotdb.cluster.rpc.raft.response.querymetric.QueryLeaderResponse;
import org.apache.iotdb.cluster.rpc.raft.response.querymetric.QueryMetricResponse;
import org.apache.iotdb.cluster.rpc.raft.response.querymetric.QueryStatusResponse;
import org.apache.iotdb.cluster.utils.hash.PhysicalNode;
import org.apache.iotdb.cluster.utils.hash.Router;
import org.apache.iotdb.cluster.utils.hash.VirtualNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RaftUtils {

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

    LOGGER.debug("Get node {} for group {}", peerId, groupId);

    return peerId;
  }

  public static void updatePeerIDOrder(PeerId peerId, String groupId) {
    int index = -1;
    int len;
    if (groupId.equals(ClusterConfig.METADATA_GROUP_ID)) {
      RaftService service = (RaftService) server.getMetadataHolder().getService();
      List<PeerId> peerIdList = service.getPeerIdList();
      len = peerIdList.size();
      index = peerIdList.indexOf(peerId);
    } else {
      PhysicalNode[] physicalNodes = router.getNodesByGroupId(groupId);
      len = physicalNodes.length;
      PhysicalNode node = getPhysicalNodeFrom(peerId);
      for (int i = 0; i < physicalNodes.length; i++) {
        if (physicalNodes[i].equals(node)) {
          index = i;
          break;
        }
      }
    }

    if (index == -1) {
      LOGGER.warn(
          "Fail to update order of node {} for group {}, because the group doesn't contain it.",
          peerId, groupId);
    } else {
      LOGGER.debug("Update order of node {} for group {}, current index is {}", peerId, groupId,
          index);
      nodeIndexMap.get().put(groupId, (index + 1) % len);
    }
  }

  /**
   * Get peer id to send request. If groupLeaderCache has the group id, then return leader id of the
   * group.Otherwise, random get a peer of the group.
   *
   * @return leader id
   */
  public static PeerId getLocalLeaderPeerID(String groupId) {
    PeerId leader;
    if (groupLeaderCache.containsKey(groupId)) {
      leader = groupLeaderCache.get(groupId);
    } else {
      leader = getRandomPeerID(groupId);
      groupLeaderCache.put(groupId, leader);
    }
    LOGGER.debug("Get local cached leader {} of group {}.", leader, groupId);
    return leader;
  }

  /**
   * Get peer id to send request. If groupLeaderCache has the group id, then return leader id of the
   * group.Otherwise, random get a peer of the group.
   *
   * @return leader id
   */
  public static PeerId getLeaderPeerIDFromRemoteNode(PeerId peerId, String groupId) {
    QueryLeaderRequest request = new QueryLeaderRequest(groupId);
    SingleQPTask task = new SingleQPTask(false, request);
    task.setTargetNode(peerId);
    LOGGER.debug("Execute get leader of group {} from node {}.", groupId, peerId);
    try {
      CLIENT_MANAGER.produceQPTask(task);

      task.await();
      PeerId leader = null;
      if (task.getTaskState() == TaskState.FINISH) {
        BasicResponse response = task.getResponse();
        leader = response == null ? null : ((QueryLeaderResponse) response).getLeader();
      }
      LOGGER.debug("Get leader {} of group {} from node {}.", leader, groupId, peerId);
      return leader;
    } catch (RaftConnectionException | InterruptedException e) {
      LOGGER.error("Fail to get leader of group {} from remote node {} because of {}.", groupId,
          peerId, e.getMessage());
      return null;
    }
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

  private static void handleNullReadToDataGroup(Status resultStatus, Server server,
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
                resultStatus.setCode(-1);
                resultStatus.setErrorMsg(status.getErrorMsg());
              }
              nullReadTask.receive(response);
            }
          });
      nullReadTask.await();
    } catch (InterruptedException e) {
      resultStatus.setCode(-1);
      resultStatus.setErrorMsg(e.getMessage());
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
   * Get all node information of the data group of input storage group. The first node is the
   * current leader
   *
   * @param sg storage group ID. If null, return metadata group info
   */
  public static PeerId[] getDataPartitionOfSG(String sg) {
    return getDataPartitionOfSG(sg, server, router);
  }

  public static PeerId[] getDataPartitionOfSG(String sg, Server server, Router router) {
    String groupId;
    PeerId[] nodes;
    if (sg == null) {
      groupId = ClusterConfig.METADATA_GROUP_ID;
      List<PeerId> peerIdList = ((RaftService) server.getMetadataHolder().getService())
          .getPeerIdList();
      nodes = peerIdList.toArray(new PeerId[peerIdList.size()]);
    } else {
      PhysicalNode[] group = router.routeGroup(sg);
      groupId = router.getGroupID(group);
      nodes = getPeerIdArrayFrom(group);
    }

    PeerId leader = null;
    for (PeerId node : nodes) {
      LOGGER.debug("Try to get leader of group {} from node {}.", groupId, node);
      leader = getLeaderPeerIDFromRemoteNode(node, groupId);
      LOGGER.debug("Get leader {} of group {} from node {}.", leader, groupId, node);
      if (leader != null) {
        break;
      }
    }

    if (leader == null) {
      LOGGER
          .debug("Fail to get leader of group {} from all remote nodes, get it locally.", groupId);
      leader = RaftUtils.getLocalLeaderPeerID(groupId);
      LOGGER.debug("Get leader {} of group {} locally.", leader, groupId);
    }

    for (int i = 0; i < nodes.length; i++) {
      if (leader.equals(nodes[i])) {
        PeerId t = nodes[i];
        nodes[i] = nodes[0];
        nodes[0] = t;
        break;
      }
    }
    return nodes;
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

  /**
   * Get replica lag for metadata group and each data partition
   *
   * @return key: groupId, value: ip -> replica lag
   */
  public static Map<String, Map<String, Long>> getReplicaLagMap() {
    return getReplicaMetricMap("log-lags");
  }

  public static Map<String, Map<String, Long>> getReplicaMetricMap(String metric) {
    Map<String, Map<String, Long>> metricMap = new HashMap<>();
    RaftService raftService = (RaftService) server.getMetadataHolder().getService();
    metricMap.put(raftService.getGroupId(), getReplicaMetricFromRaftService(raftService, metric));

    router.getAllGroupId()
        .forEach(groupId -> metricMap.put(groupId, getReplicaMetric(groupId, metric)));
    return metricMap;
  }

  public static Map<String, Long> getReplicaMetric(String groupId, String metric) {
    if (server.getDataPartitionHolderMap().containsKey(groupId)) {
      RaftService service = (RaftService) server.getDataPartitionHolder(groupId).getService();
      return getReplicaMetricFromRaftService(service, metric);
    } else {
      LOGGER.debug("Current host does not contain group {}, all groups are {}.", groupId,
          server.getDataPartitionHolderMap().keySet());
      return getReplicaMetricFromRemoteNode(groupId, metric);
    }
  }

  private static Map<String, Long> getReplicaMetricFromRaftService(RaftService service,
      String metric) {
    String groupId = service.getGroupId();
    LOGGER.debug("Get replica metric {} for group {}.", metric, service.getGroupId());
    NodeImpl node = (NodeImpl) service.getNode();
    Map<String, Long> lagMap;
    if (node.isLeader()) {
      LOGGER.debug("Get metric locally.");
      List<PeerId> nodes = service.getPeerIdList();
      Map<String, Gauge> metrics = service.getNode().getNodeMetrics().getMetricRegistry()
          .getGauges();

      lagMap = new HashMap<>();
      String keyFormat = "replicator-%s/%s.%s";
      for (int i = 0; i < nodes.size(); i++) {
        // leader doesn't have lag metric
        if (nodes.get(i).equals(node.getServerId())) {
          lagMap.put(nodes.get(i).getIp() + " (leader)", 0L);
          continue;
        }

        String key = String.format(keyFormat, groupId, nodes.get(i), metric);
        long value = -1;
        if (metrics.containsKey(key)) {
          value = (long) metrics.get(key).getValue();
        } else {
          LOGGER.warn("Metric map {} should contain key {}, but not.", metrics, key);
        }
        lagMap.put(nodes.get(i).getIp(), value);
      }
    } else {
      lagMap = getReplicaMetricFromRemoteNode(groupId, metric);
    }
    return lagMap;
  }

  private static Map<String, Long> getReplicaMetricFromRemoteNode(String groupId, String metric) {
    QueryMetricRequest request = new QueryMetricRequest(groupId, metric);
    SingleQPTask task = new SingleQPTask(false, request);

    LOGGER.debug("Execute get metric for {} statement for group {}.", metric, groupId);
    PeerId holder = RaftUtils.getLocalLeaderPeerID(groupId);
    LOGGER.debug("Get metric from node {}.", holder);
    task.setTargetNode(holder);
    try {
      CLIENT_MANAGER.produceQPTask(task);

      task.await();
      Map<String, Long> value = null;
      if (task.getTaskState() == TaskState.FINISH) {
        BasicResponse response = task.getResponse();
        value = response == null ? null : ((QueryMetricResponse) response).getValue();
      }
      return value;
    } catch (RaftConnectionException | InterruptedException e) {
      LOGGER.error("Fail to get replica metric from remote node because of {}.", e);
      return null;
    }
  }

  /**
   * Get query job number running on each data partition for all nodes
   *
   * @return outer key: ip, inner key: groupId, value: number of query jobs
   */
  public static Map<String, Map<String, Integer>> getQueryJobNumMapForCluster() {
    PeerId[] peerIds = RaftUtils.convertStringArrayToPeerIdArray(config.getNodes());
    Map<String, Map<String, Integer>> res = new HashMap<>();
    for (int i = 0; i < peerIds.length; i++) {
      PeerId peerId = peerIds[i];
      res.put(peerId.getIp(), getQueryJobNumMapFromRemoteNode(peerId));
    }

    return res;
  }

  public static Map<String, Integer> getLocalQueryJobNumMap() {
    return ClusterRpcQueryManager.getInstance().getAllReadUsage();
  }

  private static Map<String, Integer> getQueryJobNumMapFromRemoteNode(PeerId peerId) {
    QueryJobNumRequest request = new QueryJobNumRequest("");
    SingleQPTask task = new SingleQPTask(false, request);
    task.setTargetNode(peerId);
    LOGGER.debug("Execute get query job num map for node {}.", peerId);
    try {
      CLIENT_MANAGER.produceQPTask(task);

      task.await();
      Map<String, Integer> value = null;
      if (task.getTaskState() == TaskState.FINISH) {
        BasicResponse response = task.getResponse();
        value = response == null ? null : ((QueryJobNumResponse) response).getValue();
      }
      return value;
    } catch (RaftConnectionException | InterruptedException e) {
      LOGGER.error("Fail to get query job num map from remote node {} because of {}.", peerId, e);
      return null;
    }
  }

  /**
   * Get status of each node in cluster
   *
   * @return key: node ip, value: live or not
   */
  public static Map<String, Boolean> getStatusMapForCluster() {
    PeerId[] peerIds = RaftUtils.convertStringArrayToPeerIdArray(config.getNodes());
    SortedMap<String, Boolean> treeMap = new TreeMap<>(new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        int[] nums1 = convertIPToNums(o1);
        int[] nums2 = convertIPToNums(o2);
        for (int i = 0; i < Math.min(nums1.length, nums2.length); i++) {
          if (nums1[i] != nums2[i]) {
            return Integer.compare(nums1[i], nums2[i]);
          }
        }
        return 0;
      }

      private int[] convertIPToNums(String ip) {
        String[] ss = ip.split("\\.");
        int[] nums = new int[ss.length];
        for (int i = 0; i < nums.length; i++) {
          nums[i] = Integer.parseInt(ss[i]);
        }
        return nums;
      }
    });
    for (int i = 0; i < peerIds.length; i++) {
      PeerId peerId = peerIds[i];
      treeMap.put(peerId.getIp(), getStatusOfNode(peerId));
    }

    Map<String, Boolean> res = new LinkedHashMap<>();
    treeMap.forEach((ip, status) -> res.put(ip, status));
    return res;
  }

  private static boolean getStatusOfNode(PeerId peerId) {
    QueryStatusRequest request = new QueryStatusRequest("");
    SingleQPTask task = new SingleQPTask(false, request);
    task.setTargetNode(peerId);
    LOGGER.debug("Execute get status for node {}.", peerId);
    try {
      CLIENT_MANAGER.produceQPTask(task);

      task.await();
      boolean status = false;
      if (task.getTaskState() == TaskState.FINISH) {
        BasicResponse response = task.getResponse();
        status = response == null ? null : ((QueryStatusResponse) response).getStatus();
      }
      return status;
    } catch (RaftConnectionException | InterruptedException e) {
      LOGGER.error("Fail to get status from remote node {} because of {}.", peerId, e);
      return false;
    }
  }

  public static Set<String> getAllStorageGroupsLocally() {
    MetadataRaftHolder metadataRaftHolder = (MetadataRaftHolder) server.getMetadataHolder();
    return metadataRaftHolder.getFsm().getAllStorageGroups();
  }
}
