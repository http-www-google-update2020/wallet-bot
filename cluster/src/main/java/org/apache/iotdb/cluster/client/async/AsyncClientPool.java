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

package org.apache.iotdb.cluster.client.async;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.iotdb.cluster.config.ClusterDescriptor;
import org.apache.iotdb.cluster.rpc.thrift.Node;
import org.apache.iotdb.cluster.rpc.thrift.RaftService.AsyncClient;
import org.apache.iotdb.cluster.utils.ClusterNode;
import org.apache.thrift.async.TAsyncMethodCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncClientPool {

  private static final Logger logger = LoggerFactory.getLogger(AsyncClientPool.class);
  private static final long WAIT_CLIENT_TIMEOUT_MS = 5 * 1000L;
  private int maxConnectionForEachNode;
  private Map<ClusterNode, Deque<AsyncClient>> clientCaches = new ConcurrentHashMap<>();
  private Map<ClusterNode, Integer> nodeClientNumMap = new ConcurrentHashMap<>();
  private AsyncClientFactory asyncClientFactory;

  public AsyncClientPool(AsyncClientFactory asyncClientFactory) {
    this.asyncClientFactory = asyncClientFactory;
    this.maxConnectionForEachNode =
        ClusterDescriptor.getInstance().getConfig().getMaxClientPerNodePerMember();
  }

  /**
   * Get a client of the given node from the cache if one is available, or create a new one.
   *
   * @param node
   * @return
   * @throws IOException
   */
  public AsyncClient getClient(Node node) throws IOException {
    ClusterNode clusterNode = new ClusterNode(node);
    AsyncClient client;
    synchronized (this) {
      //As clientCaches is ConcurrentHashMap, computeIfAbsent is thread safety.
      Deque<AsyncClient> clientStack = clientCaches.computeIfAbsent(clusterNode,
          n -> new ArrayDeque<>());
      if (clientStack.isEmpty()) {
        int nodeClientNum = nodeClientNumMap.getOrDefault(clusterNode, 0);
        if (nodeClientNum >= maxConnectionForEachNode) {
          client = waitForClient(clientStack, clusterNode, nodeClientNum);
        } else {
          nodeClientNumMap.put(clusterNode, nodeClientNum + 1);
          client = asyncClientFactory.getAsyncClient(clusterNode, this);
        }
      } else {
        client = clientStack.pop();
      }
    }
    return client;
  }

  @SuppressWarnings("java:S2273") // synchronized outside
  private AsyncClient waitForClient(Deque<AsyncClient> clientStack, ClusterNode node, int nodeClientNum)
      throws IOException {
    // wait for an available client
    long waitStart = System.currentTimeMillis();
    while (clientStack.isEmpty()) {
      try {
        this.wait(WAIT_CLIENT_TIMEOUT_MS);
        if (clientStack.isEmpty()
            && System.currentTimeMillis() - waitStart >= WAIT_CLIENT_TIMEOUT_MS) {
          logger.warn("Cannot get an available client after {}ms, create a new one",
              WAIT_CLIENT_TIMEOUT_MS);
          nodeClientNumMap.put(node, nodeClientNum + 1);
          return asyncClientFactory.getAsyncClient(node, this);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.warn("Interrupted when waiting for an available client of {}", node);
        return null;
      }
    }
    return clientStack.pop();
  }

  /**
   * Return a client of a node to the pool. Closed client should not be returned.
   *
   * @param node
   * @param client
   */
  public void putClient(Node node, AsyncClient client) {
    ClusterNode clusterNode = new ClusterNode(node);
    TAsyncMethodCall call;
    if (client instanceof AsyncDataClient) {
      call = ((AsyncDataClient) client).getCurrMethod();
    } else {
      call = ((AsyncMetaClient) client).getCurrMethod();
    }
    if (call != null) {
      logger.warn("A using client {} is put back while running {}", client.hashCode(), call);
    }
    synchronized (this) {
      //As clientCaches is ConcurrentHashMap, computeIfAbsent is thread safety.
      Deque<AsyncClient> clientStack = clientCaches.computeIfAbsent(clusterNode, n -> new ArrayDeque<>());
      clientStack.push(client);
      this.notifyAll();
    }
  }

  public void recreateClient(Node node) {
    ClusterNode clusterNode = new ClusterNode(node);
    synchronized (this) {
      Deque<AsyncClient> clientStack = clientCaches.computeIfAbsent(clusterNode, n -> new ArrayDeque<>());
      try {
        AsyncClient asyncClient = asyncClientFactory.getAsyncClient(node, this);
        clientStack.push(asyncClient);
      } catch (IOException e) {
        logger.error("Cannot create a new client for {}", node, e);
        nodeClientNumMap.computeIfPresent(clusterNode, (n, cnt) -> cnt - 1);
      }
      this.notifyAll();
    }
  }
}
