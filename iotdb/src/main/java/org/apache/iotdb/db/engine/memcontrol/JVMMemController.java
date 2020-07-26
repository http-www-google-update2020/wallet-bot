/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.engine.memcontrol;

import org.apache.iotdb.db.conf.IoTDBConfig;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.utils.MemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JVMMemController extends BasicMemController {

  private static Logger LOGGER = LoggerFactory.getLogger(JVMMemController.class);

  private JVMMemController(IoTDBConfig config) {
    super(config);
  }

  public static JVMMemController getInstance() {
    return InstanceHolder.INSTANCE;
  }

  @Override
  public long getTotalUsage() {
    // memory used by non-data objects, this is used to estimate the memory used by data
    long nonDataUsage = 0;
    return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() - nonDataUsage;
  }

  @Override
  public UsageLevel getCurrLevel() {
    long memUsage = getTotalUsage();
    if (memUsage < warningThreshold) {
      return UsageLevel.SAFE;
    } else if (memUsage < dangerouseThreshold) {
      return UsageLevel.WARNING;
    } else {
      return UsageLevel.DANGEROUS;
    }
  }

  @Override
  public void clear() {
    // JVMMemController does not need cleaning
  }

  @Override
  public UsageLevel acquireUsage(Object user, long usage) {
    long memUsage = getTotalUsage() + usage;
    if (memUsage < warningThreshold) {
      return UsageLevel.SAFE;
    } else if (memUsage < dangerouseThreshold) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Warning Threshold : {} allocated to {}, total usage {}",
            MemUtils.bytesCntToStr(usage),
            user.getClass(), MemUtils.bytesCntToStr(memUsage));
      }
      return UsageLevel.WARNING;
    } else {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn("Memory request from {} is denied, memory usage : {}", user.getClass(),
            MemUtils.bytesCntToStr(memUsage));
      }
      return UsageLevel.DANGEROUS;
    }
  }

  @Override
  public void releaseUsage(Object user, long freeSize) {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info("{} freed from {}, total usage {}", MemUtils.bytesCntToStr(freeSize),
          user.getClass(), MemUtils.bytesCntToStr(getTotalUsage()));
    }
  }

  private static class InstanceHolder {

    private InstanceHolder() {
    }

    private static final JVMMemController INSTANCE = new JVMMemController(
        IoTDBDescriptor.getInstance().getConfig());
  }
}
