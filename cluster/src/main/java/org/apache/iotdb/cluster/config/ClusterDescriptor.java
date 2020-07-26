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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.iotdb.cluster.service.TSServiceClusterImpl;
import org.apache.iotdb.db.conf.IoTDBConfig;
import org.apache.iotdb.db.conf.IoTDBConstant;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterDescriptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClusterDescriptor.class);

  private IoTDBConfig ioTDBConf = IoTDBDescriptor.getInstance().getConfig();

  private ClusterConfig conf = new ClusterConfig();

  private ClusterDescriptor() {
    loadProps();
  }

  public static ClusterDescriptor getInstance() {
    return ClusterDescriptorHolder.INSTANCE;
  }

  public ClusterConfig getConfig() {
    return conf;
  }

  /**
   * Load an property file and set ClusterConfig variables. Change this method to public only for
   * test. In most case, you should invoke this method.
   */
  public void loadProps() {
    // modify iotdb config
    ioTDBConf.setRpcImplClassName(TSServiceClusterImpl.class.getName());
    ioTDBConf.setEnableWal(false);

    // cluster config
    conf.setDefaultPath();
    InputStream inputStream;
    String url = System.getProperty(IoTDBConstant.IOTDB_CONF, null);
    if (url == null) {
      url = System.getProperty(IoTDBConstant.IOTDB_HOME, null);
      if (url != null) {
        url = url + File.separatorChar + "conf" + File.separatorChar + ClusterConfig.CONFIG_NAME;
      } else {
        LOGGER.warn(
            "Cannot find IOTDB_HOME or CLUSTER_CONF environment variable when loading "
                + "config file {}, use default configuration",
            ClusterConfig.CONFIG_NAME);
        conf.createAllPath();
        return;
      }
    } else {
      url += (File.separatorChar + ClusterConfig.CONFIG_NAME);
    }

    try {
      inputStream = new FileInputStream(new File(url));
    } catch (FileNotFoundException e) {
      LOGGER.warn("Fail to find config file {}", url, e);
      conf.createAllPath();
      return;
    }

    LOGGER.info("Start to read config file {}", url);
    Properties properties = new Properties();
    try {
      properties.load(inputStream);
      String[] nodes = properties.getProperty("nodes", ClusterConfig.DEFAULT_NODE)
          .split(",");
      for(int i = 0 ; i < nodes.length ; i++){
        nodes[i] = nodes[i].trim();
      }
      conf.setNodes(nodes);

      conf.setReplication(Integer
          .parseInt(properties.getProperty("replication",
              Integer.toString(conf.getReplication()))));

      conf.setIp(properties.getProperty("ip", conf.getIp()));

      conf.setPort(Integer.parseInt(properties.getProperty("port",
          Integer.toString(conf.getPort()))));

      conf.setRaftLogPath(properties.getProperty("raft_log_path", conf.getRaftLogPath()));

      conf.setRaftSnapshotPath(
          properties.getProperty("raft_snapshot_path", conf.getRaftSnapshotPath()));

      conf.setRaftMetadataPath(
          properties.getProperty("raft_metadata_path", conf.getRaftMetadataPath()));

      conf.setElectionTimeoutMs(Integer
          .parseInt(properties.getProperty("election_timeout_ms",
              Integer.toString(conf.getElectionTimeoutMs()))));

      conf.setMaxCatchUpLogNum(Integer
          .parseInt(properties.getProperty("max_catch_up_log_num",
              Integer.toString(conf.getMaxCatchUpLogNum()))));

      conf.setDelaySnapshot(Boolean
          .parseBoolean(properties.getProperty("delay_snapshot",
              Boolean.toString(conf.isDelaySnapshot()))));

      conf.setDelayHours(Integer
          .parseInt(properties.getProperty("delay_hours",
              Integer.toString(conf.getDelayHours()))));

      conf.setQpTaskRedoCount(Integer
          .parseInt(properties.getProperty("qp_task_redo_count",
              Integer.toString(conf.getQpTaskRedoCount()))));

      conf.setQpTaskTimeout(Integer
          .parseInt(properties.getProperty("qp_task_timeout_ms",
              Integer.toString(conf.getQpTaskTimeout()))));

      conf.setNumOfVirtualNodes(Integer
          .parseInt(properties.getProperty("num_of_virtual_nodes",
              Integer.toString(conf.getNumOfVirtualNodes()))));

      conf.setConcurrentInnerRpcClientThread(Integer
          .parseInt(properties.getProperty("concurrent_inner_rpc_client_thread",
              Integer.toString(conf.getConcurrentInnerRpcClientThread()))));

      conf.setMaxQueueNumOfQPTask(Integer
          .parseInt(properties.getProperty("max_queue_num_of_inner_rpc_client",
              Integer.toString(conf.getMaxQueueNumOfQPTask()))));

      String readMetadataLevelName = properties.getProperty("read_metadata_consistency_level", "");
      int readMetadataLevel = ClusterConsistencyLevel.getLevel(readMetadataLevelName);
      if(readMetadataLevel == ClusterConsistencyLevel.UNSUPPORT_LEVEL){
        readMetadataLevel = ClusterConsistencyLevel.STRONG.ordinal();
      }
      conf.setReadMetadataConsistencyLevel(readMetadataLevel);

      String readDataLevelName = properties.getProperty("read_data_consistency_level", "");
      int readDataLevel = ClusterConsistencyLevel.getLevel(readDataLevelName);
      if(readDataLevel == ClusterConsistencyLevel.UNSUPPORT_LEVEL){
        readDataLevel = ClusterConsistencyLevel.STRONG.ordinal();
      }
      conf.setReadDataConsistencyLevel(readDataLevel);

      conf.setConcurrentQPSubTaskThread(Integer
          .parseInt(properties.getProperty("concurrent_qp_sub_task_thread",
              Integer.toString(conf.getConcurrentQPSubTaskThread()))));

      conf.setBatchReadSize(Integer.parseInt(properties.getProperty("batch_read_size",
          Integer.toString(conf.getBatchReadSize()))));

      conf.setMaxCachedBatchDataListSize(Integer.parseInt(properties
          .getProperty("max_cached_batch_data_list_size",
              Integer.toString(conf.getMaxCachedBatchDataListSize()))));

      if (conf.getConcurrentQPSubTaskThread() <= 0) {
        conf.setConcurrentQPSubTaskThread(Runtime.getRuntime().availableProcessors() * 10);
      }

      if (conf.getConcurrentInnerRpcClientThread() <= 0) {
        conf.setConcurrentInnerRpcClientThread(Runtime.getRuntime().availableProcessors() * 10);
      }

      if (conf.getMaxCachedBatchDataListSize() <= 0) {
        conf.setMaxCachedBatchDataListSize(2);
      }

      if (conf.getBatchReadSize() <= 0) {
        conf.setBatchReadSize(10000);
      }

    } catch (IOException e) {
      LOGGER.warn("Cannot load config file because, use default configuration", e);
    } catch (Exception e) {
      LOGGER.warn("Incorrect format in config file, use default configuration", e);
    } finally {
      conf.createAllPath();
      try {
        inputStream.close();
      } catch (IOException e) {
        LOGGER.error("Fail to close config file input stream because ", e);
      }
    }
  }

  private static class ClusterDescriptorHolder {

    private static final ClusterDescriptor INSTANCE = new ClusterDescriptor();
  }
}
