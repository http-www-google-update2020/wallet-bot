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
package org.apache.iotdb.cluster.config;

public enum ClusterConsistencyLevel {
  /**
   * Strong consistency level
   */
  STRONG("strong"),

  /**
   * Weak consistency level
   */
  WEAK("weak");

  private String levelName;

  public static final int UNSUPPORT_LEVEL = -1;

  ClusterConsistencyLevel(String levelName) {
    this.levelName = levelName;
  }

  public static int getLevel(String levelName) {
    for(ClusterConsistencyLevel consistencyLevel: values()){
      if(consistencyLevel.levelName.equals(levelName)){
        return consistencyLevel.ordinal();
      }
    }
    return UNSUPPORT_LEVEL;
  }
}
