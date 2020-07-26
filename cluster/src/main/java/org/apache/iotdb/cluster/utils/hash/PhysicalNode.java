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
package org.apache.iotdb.cluster.utils.hash;

import com.alipay.sofa.jraft.util.OnlyForTest;

public class PhysicalNode {

  private String ip;

  private int port;

  /**
   * Group id of data group which first node is this PhysicalNode.
   */
  private String groupId;

  public PhysicalNode(String ip, int port) {
    this.ip = ip;
    this.port = port;
  }

  String getKey() {
    return String.format("%s:%d", ip, port);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((ip == null) ? 0 : ip.hashCode());
    result = prime * result + port;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    PhysicalNode other = (PhysicalNode) obj;
    if (this.port != other.port) {
      return false;
    }
    if (this.ip == null) {
      return other.ip == null ;
    }
    return this.ip.equals(other.ip);
  }

  @Override
  public String toString() {
    return getKey();
  }

  public String getIp() {
    return ip;
  }

  public int getPort() {
    return port;
  }

  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(String groupId) {
    this.groupId = groupId;
  }

  @OnlyForTest
  public void setIp(String ip) {
    this.ip = ip;
  }

  @OnlyForTest
  public void setPort(int port) {
    this.port = port;
  }
}
