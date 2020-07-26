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
package org.apache.iotdb.db.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.iotdb.db.concurrent.ThreadName;
import org.apache.iotdb.db.concurrent.WrappedRunnable;
import org.apache.iotdb.db.conf.IoTDBConfig;
import org.apache.iotdb.db.conf.IoTDBConstant;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.rest.util.RestUtil;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HTTPService implements HTTPServiceMBean, IService {

  private static final Logger logger = LoggerFactory.getLogger(HTTPService.class);
  private final String mbeanName = String.format("%s:%s=%s", IoTDBConstant.IOTDB_PACKAGE,
      IoTDBConstant.JMX_TYPE, getID().getJmxName());

  private Server server;
  private ExecutorService executorService;
  private CountDownLatch startLatch;

  public static HTTPService getInstance() {
    return HTTPServiceHolder.INSTANCE;
  }

  @Override
  public ServiceType getID() {
    return ServiceType.HTTP_SERVICE;
  }

  @Override
  public int getHTTPPort() {
    IoTDBConfig config = IoTDBDescriptor.getInstance().getConfig();
    return config.getHTTPPort();
  }

  @Override
  public void start() {
    if (!IoTDBDescriptor.getInstance().getConfig().isEnableHTTPService()) {
      return;
    }
    try {
      startService();
      JMXService.registerMBean(getInstance(), mbeanName);
    } catch (Exception e) {
      logger.error("Failed to start {} because: ", this.getID().getName(), e);
    }
  }

  @Override
  public void stop() {
    if (!IoTDBDescriptor.getInstance().getConfig().isEnableHTTPService()) {
      return;
    }
    stopService();
    JMXService.deregisterMBean(mbeanName);
  }

  @Override
  public synchronized void startService() {
    if (!IoTDBDescriptor.getInstance().getConfig().isEnableHTTPService()) {
      return;
    }
    logger.info("{}: start {}...", IoTDBConstant.GLOBAL_DB_NAME, this.getID().getName());
    executorService = Executors.newSingleThreadExecutor();
    int port = getHTTPPort();
    server = RestUtil.getJettyServer(RestUtil.getRestContextHandler(), port);
    server.setStopTimeout(10000);
    try {
      reset();
      executorService.execute(new HTTPServiceThread(server, startLatch));
      logger.info("{}: start {} successfully, listening on ip {} port {}",
          IoTDBConstant.GLOBAL_DB_NAME, this.getID().getName(),
          IoTDBDescriptor.getInstance().getConfig().getRpcAddress(),
          IoTDBDescriptor.getInstance().getConfig().getHTTPPort());
      startLatch.await();
    } catch (NullPointerException | InterruptedException e) {
      //issue IOTDB-415, we need to stop the service.
      logger.error("{}: start {} failed, listening on ip {} port {}",
          IoTDBConstant.GLOBAL_DB_NAME, this.getID().getName(),
          IoTDBDescriptor.getInstance().getConfig().getRpcAddress(),
          IoTDBDescriptor.getInstance().getConfig().getHTTPPort());
      Thread.currentThread().interrupt();
      stopService();
    }
  }

  private void reset() {
    startLatch = new CountDownLatch(1);
  }

  @Override
  public void restartService(){
    stopService();
    startService();
  }

  @Override
  public void stopService() {
    if (!IoTDBDescriptor.getInstance().getConfig().isEnableHTTPService()) {
      return;
    }
    logger.info("{}: closing {}...", IoTDBConstant.GLOBAL_DB_NAME, this.getID().getName());
    try {
      if (server != null) {
        server.stop();
        server.destroy();
        server = null;
      }
      if (executorService != null) {
        executorService.shutdown();
        if (!executorService.awaitTermination(
            3000, TimeUnit.MILLISECONDS)) {
          executorService.shutdownNow();
        }
        executorService = null;
      }
      reset();
    } catch (Exception e) {
      logger
          .error("{}: close {} failed because {}", IoTDBConstant.GLOBAL_DB_NAME, getID().getName(),
              e.getMessage());
      executorService.shutdownNow();
    }
    checkAndWaitPortIsClosed();
    logger.info("{}: close {} successfully", IoTDBConstant.GLOBAL_DB_NAME, this.getID().getName());
  }

  private void checkAndWaitPortIsClosed() {
    SocketAddress socketAddress = new InetSocketAddress("localhost", getHTTPPort());
    boolean isClose = false;
    try(Socket socket = new Socket()) {
      socket.connect(socketAddress, 1000);
    } catch (IOException e) {
      logger.debug("Close port successfully.");
      isClose = true;
    } finally {
      if (!isClose) {
        logger.error("Port {} can not be closed.", getHTTPPort());
      }
    }
  }

  private static class HTTPServiceHolder {

    private static final HTTPService INSTANCE = new HTTPService();

    private HTTPServiceHolder() {}
  }

  private class HTTPServiceThread extends WrappedRunnable {

    private Server server;
    private CountDownLatch threadStartLatch;

    HTTPServiceThread(Server server, CountDownLatch threadStartLatch) {
      this.server = server;
      this.threadStartLatch = threadStartLatch;
    }

    @Override
    public void runMayThrow() {
      try {
        Thread.currentThread().setName(ThreadName.HTTP_SERVICE.getName());
        server.start();
        threadStartLatch.countDown();
      } catch (@SuppressWarnings("squid:S2142") InterruptedException e1) {
        //we do not sure why InterruptedException happens, but it indeed occurs in Travis WinOS
        logger.error(e1.getMessage(), e1);
      } catch (Exception e) {
        logger.error("{}: failed to start {}, because ", IoTDBConstant.GLOBAL_DB_NAME, getID().getName(), e);
      }
    }
  }
}
