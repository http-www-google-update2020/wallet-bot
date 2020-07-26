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

package org.apache.iotdb.cluster.partition;

import java.util.Map;
import java.util.Set;
import org.apache.iotdb.cluster.rpc.thrift.Node;

public class NodeAdditionResult {

  /**
   * A new data group headed by the new node.
   */
  private PartitionGroup newGroup;

  /**
   * What slots will the old data groups transfer to the new one.
   */
  private Map<Node, Set<Integer>> lostSlots;

  public PartitionGroup getNewGroup() {
    return newGroup;
  }

  public void setNewGroup(PartitionGroup newGroup) {
    this.newGroup = newGroup;
  }

  public Map<Node, Set<Integer>> getLostSlots() {
    return lostSlots;
  }

  public void setLostSlots(
      Map<Node, Set<Integer>> lostSlots) {
    this.lostSlots = lostSlots;
  }
}
