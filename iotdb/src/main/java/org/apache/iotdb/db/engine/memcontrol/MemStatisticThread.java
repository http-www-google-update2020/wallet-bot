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

import org.apache.iotdb.db.concurrent.ThreadName;
import org.apache.iotdb.db.utils.MemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MemStatisticThread extends Thread {

  private static Logger logger = LoggerFactory.getLogger(MemStatisticThread.class);

  private long minMemUsage = Long.MAX_VALUE;
  private long maxMemUsage = Long.MIN_VALUE;
  private double meanMemUsage = 0.0;
  private long minJvmUsage = Long.MAX_VALUE;
  private long maxJvmUsage = Long.MIN_VALUE;
  private double meanJvmUsage = 0.0;
  private int cnt = 0;

  public MemStatisticThread() {
    this.setName(ThreadName.MEMORY_STATISTICS.getName());
  }

  @Override
  public void run() {
    logger.info("{} started", this.getClass().getSimpleName());
    try {
      // wait 3 mins for system to setup
      Thread.sleep(3 * 60 * 1000L);
    } catch (InterruptedException e) {
      logger.info("{} exiting...", this.getClass().getSimpleName());
      Thread.currentThread().interrupt();
      return;
    }
    super.run();
    workLoop();
  }

  private void workLoop() {
    while (true) {
      if (this.isInterrupted()) {
        logger.info("{} exiting...", this.getClass().getSimpleName());
        return;
      }
      long memUsage = BasicMemController.getInstance().getTotalUsage();
      long jvmUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
      minMemUsage = memUsage < minMemUsage ? memUsage : minMemUsage;
      minJvmUsage = jvmUsage < minJvmUsage ? jvmUsage : minJvmUsage;
      maxMemUsage = memUsage > maxMemUsage ? memUsage : maxMemUsage;
      maxJvmUsage = jvmUsage > maxJvmUsage ? jvmUsage : maxJvmUsage;
      double doubleCnt = cnt;
      meanMemUsage = meanMemUsage * (doubleCnt / (doubleCnt + 1.0)) + memUsage / (doubleCnt + 1.0);
      meanJvmUsage = meanJvmUsage * (doubleCnt / (doubleCnt + 1.0)) + jvmUsage / (doubleCnt + 1.0);

      // log statistic every so many intervals
      report();

      try {
        // update statistic every such interval
        // in ms
        long checkInterval = 100;
        Thread.sleep(checkInterval);
      } catch (InterruptedException e) {
        logger.info("MemMonitorThread exiting...");
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private void report() {
    int reportCycle = 6000;
    if (++cnt % reportCycle == 0 && logger.isDebugEnabled()) {
      logger.debug(
              "Monitored memory usage, min {}, max {}, mean {} \n"
                      + "JVM memory usage, min {}, max {}, mean {}",
              MemUtils.bytesCntToStr(minMemUsage), MemUtils.bytesCntToStr(maxMemUsage),
              MemUtils.bytesCntToStr((long) meanMemUsage),
              MemUtils.bytesCntToStr(minJvmUsage), MemUtils.bytesCntToStr(maxJvmUsage),
              MemUtils.bytesCntToStr((long) meanJvmUsage));
    }
  }

}
