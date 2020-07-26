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
package org.apache.iotdb.cluster.service.nodetool;

import io.airlift.airline.Command;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.iotdb.cluster.service.nodetool.NodeTool.NodeToolCmd;
import org.apache.iotdb.cluster.service.ClusterMonitorMBean;

@Command(name = "lag", description = "Print log lag for all groups of connected host")
public class Lag extends NodeToolCmd {

  @Override
  public void execute(ClusterMonitorMBean proxy)
  {
    Map<String, Map<String, Long>> groupMap = proxy.getReplicaLagMap();
    for (Entry<String, Map<String, Long>> entry : groupMap.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      System.out.println(entry.getKey() + ":");
      entry.getValue().forEach((node, lag) -> System.out.println("\t" + node + "\t->\t" + lag));
    }
  }
}