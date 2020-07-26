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

package org.apache.iotdb.cluster.client.sync;

import org.apache.iotdb.cluster.rpc.thrift.Node;
import org.apache.iotdb.cluster.utils.ClusterNode;
import org.apache.iotdb.cluster.utils.ClusterUtils;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransportException;

/**
 * Notice: Because a client will be returned to a pool immediately after a successful request, you
 * should not cache it anywhere else or there may be conflicts.
 */
public class SyncMetaHeartbeatClient extends SyncMetaClient {

  public SyncMetaHeartbeatClient(TProtocolFactory protocolFactory, Node node, SyncClientPool pool)
      throws TTransportException {

    super(protocolFactory,
        new ClusterNode(node.getIp(), node.getMetaPort() + ClusterUtils.META_HEARTBEAT_PORT_OFFSET,
            node.getNodeIdentifier(),
            node.getDataPort()), pool);
  }


  public static class FactorySync implements SyncClientFactory {

    private TProtocolFactory protocolFactory;

    public FactorySync(TProtocolFactory protocolFactory) {
      this.protocolFactory = protocolFactory;
    }

    @Override
    public SyncMetaHeartbeatClient getSyncClient(Node node, SyncClientPool pool)
        throws TTransportException {
      return new SyncMetaHeartbeatClient(protocolFactory, node, pool);
    }
  }

  @Override
  public String toString() {
    return "SyncMetaHeartbeatClient{" +
        "node=" + super.getNode() + "," +
        "metaHeartbeatPort=" + (super.getNode().getMetaPort()
        + ClusterUtils.META_HEARTBEAT_PORT_OFFSET) +
        '}';
  }
}
