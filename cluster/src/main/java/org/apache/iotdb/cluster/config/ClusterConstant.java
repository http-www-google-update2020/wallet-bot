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
package org.apache.iotdb.cluster.config;

public class ClusterConstant {

  // a failed election will restart in 0.2s~1s
  public static final long ELECTION_LEAST_TIME_OUT_MS = 2 * 100L;
  public static final long ELECTION_RANDOM_TIME_OUT_MS = 8 * 100L;
  public static final int SLOT_NUM = 10000;
  public static final int HASH_SALT = 2333;
  public static final int CHECK_ALIVE_TIME_OUT_MS = 1000;
  public static final int PULL_SNAPSHOT_RETRY_INTERVAL = 5 * 1000;

  private ClusterConstant() {
    // constant class
  }

  static final String CLUSTER_CONF = "CLUSTER_CONF";
}
