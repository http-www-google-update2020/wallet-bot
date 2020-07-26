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
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.iotdb.cluster.config.ClusterDescriptor;
import org.apache.iotdb.cluster.rpc.thrift.Node;
import org.apache.iotdb.cluster.rpc.thrift.RaftService;
import org.apache.iotdb.cluster.server.RaftServer;
import org.apache.iotdb.cluster.utils.ClusterUtils;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TNonblockingSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Notice: Because a client will be returned to a pool immediately after a successful request, you
 * should not cache it anywhere else or there may be conflicts.
 */
public class AsyncMetaHeartbeatClient extends AsyncMetaClient {

  private static final Logger logger = LoggerFactory.getLogger(AsyncMetaHeartbeatClient.class);

  public AsyncMetaHeartbeatClient(TProtocolFactory protocolFactory,
      TAsyncClientManager clientManager, Node node, AsyncClientPool pool) throws IOException {
    super(protocolFactory, clientManager, new TNonblockingSocket(node.getIp(),
        node.getMetaPort() + ClusterUtils.DATA_HEARTBEAT_PORT_OFFSET
        , RaftServer.getConnectionTimeoutInMS()));
    this.node = node;
    this.pool = pool;
  }

  public static class FactoryAsync implements AsyncClientFactory {

    private static TAsyncClientManager[] managers;

    static {
      managers =
          new TAsyncClientManager[ClusterDescriptor.getInstance().getConfig()
              .getSelectorNumOfClientPool()];
      if (ClusterDescriptor.getInstance().getConfig().isUseAsyncServer()) {
        for (int i = 0; i < managers.length; i++) {
          try {
            managers[i] = new TAsyncClientManager();
          } catch (IOException e) {
            logger.error("Cannot create client manager for factory", e);
          }
        }
      }
    }

    private TProtocolFactory protocolFactory;
    private AtomicInteger clientCnt = new AtomicInteger();

    public FactoryAsync(TProtocolFactory protocolFactory) {
      this.protocolFactory = protocolFactory;
    }

    @Override
    public RaftService.AsyncClient getAsyncClient(Node node, AsyncClientPool pool)
        throws IOException {
      TAsyncClientManager manager = managers[clientCnt.incrementAndGet() % managers.length];
      manager = manager == null ? new TAsyncClientManager() : manager;
      return new AsyncMetaHeartbeatClient(protocolFactory, manager, node, pool);
    }
  }

  @Override
  public String toString() {
    return "AsyncMetaHeartbeatClient{" +
        "node=" + super.getNode() + "," +
        "metaHeartbeatPort=" + (super.getNode().getMetaPort()
        + ClusterUtils.META_HEARTBEAT_PORT_OFFSET) +
        '}';
  }

}
