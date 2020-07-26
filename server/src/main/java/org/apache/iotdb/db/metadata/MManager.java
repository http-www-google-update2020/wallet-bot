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
package org.apache.iotdb.db.metadata;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.iotdb.db.conf.IoTDBConfig;
import org.apache.iotdb.db.conf.IoTDBConstant;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.conf.adapter.IoTDBConfigDynamicAdapter;
import org.apache.iotdb.db.engine.fileSystem.SystemFileFactory;
import org.apache.iotdb.db.exception.ConfigAdjusterException;
import org.apache.iotdb.db.exception.metadata.MetadataException;
import org.apache.iotdb.db.exception.metadata.TimeseriesAlreadyExistException;
import org.apache.iotdb.db.exception.path.MTreePathException;
import org.apache.iotdb.db.exception.path.NotStorageGroupException;
import org.apache.iotdb.db.exception.path.PathException;
import org.apache.iotdb.db.exception.storageGroup.StorageGroupException;
import org.apache.iotdb.db.monitor.MonitorConstants;
import org.apache.iotdb.db.utils.RandomDeleteCache;
import org.apache.iotdb.tsfile.common.conf.TSFileDescriptor;
import org.apache.iotdb.tsfile.exception.cache.CacheException;
import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.read.common.Path;
import org.apache.iotdb.tsfile.write.schema.MeasurementSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class takes the responsibility of serialization of all the metadata info and persistent it
 * into files. This class contains all the interfaces to modify the metadata for delta system. All
 * the operations will be insert into the logs temporary in case the downtime of the delta system.
 */
public class MManager {

  private static final Logger logger = LoggerFactory.getLogger(MManager.class);
  private static final String DOUB_SEPARATOR = "\\.";
  private static final String ROOT_NAME = MetadataConstant.ROOT;
  private static final String TIME_SERIES_TREE_HEADER = "===  Timeseries Tree  ===\n\n";

  // the lock for read/insert
  private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  // the log file seriesPath
  private String logFilePath;
  private MGraph mgraph;
  private BufferedWriter logWriter;
  private boolean writeToLog;
  private String schemaDir;

  private RandomDeleteCache<String, PathCheckRet> checkAndGetDataTypeCache;
  private RandomDeleteCache<String, MNode> mNodeCache;

  private Map<String, Integer> seriesNumberInStorageGroups = new HashMap<>();
  private long maxSeriesNumberAmongStorageGroup;
  private boolean initialized;

  private MManager() {

    schemaDir =
        IoTDBDescriptor.getInstance().getConfig().getSchemaDir();

    File systemFolder = SystemFileFactory.INSTANCE.getFile(schemaDir);
    if (!systemFolder.exists()) {
      if (systemFolder.mkdirs()) {
        logger.info("create system folder {}", systemFolder.getAbsolutePath());
      } else {
        logger.info("create system folder {} failed.", systemFolder.getAbsolutePath());
      }
    }
    logFilePath = schemaDir + File.separator + MetadataConstant.METADATA_LOG;
    writeToLog = false;

    int cacheSize = IoTDBDescriptor.getInstance().getConfig().getmManagerCacheSize();
    checkAndGetDataTypeCache = new RandomDeleteCache<String, PathCheckRet>(cacheSize) {
      @Override
      public void beforeRemove(PathCheckRet object) {
        //allowed to do nothing
      }

      @Override
      public PathCheckRet loadObjectByKey(String key) throws CacheException {
        return loadPathToCache(key);
      }
    };

    mNodeCache = new RandomDeleteCache<String, MNode>(cacheSize) {
      @Override
      public void beforeRemove(MNode object) {
        //allowed to do nothing
      }

      @Override
      public MNode loadObjectByKey(String key) throws CacheException {
        try {
          return getNodeByPathWithCheck(key);
        } catch (PathException | StorageGroupException e) {
          throw new CacheException(e);
        }
      }
    };
  }

  public static MManager getInstance() {
    return MManagerHolder.INSTANCE;
  }

  //Because the writer will be used later and should not be closed here.
  @SuppressWarnings("squid:S2093")
  public void init() {

    if (initialized) {
      return;
    }
    lock.writeLock().lock();
    File logFile = SystemFileFactory.INSTANCE.getFile(logFilePath);

    try {
      initFromLog(logFile);
      seriesNumberInStorageGroups = mgraph.countSeriesNumberInEachStorageGroup();
      if (seriesNumberInStorageGroups.isEmpty()) {
        maxSeriesNumberAmongStorageGroup = 0;
      } else {
        maxSeriesNumberAmongStorageGroup = seriesNumberInStorageGroups.values().stream()
            .max(Integer::compareTo).get();
      }
      writeToLog = true;
    } catch (PathException | IOException | MetadataException e) {
      mgraph = new MGraph(ROOT_NAME);
      logger.error("Cannot read MGraph from file, using an empty new one", e);
    } finally {
      lock.writeLock().unlock();
    }
    initialized = true;
  }


  private void initFromLog(File logFile)
      throws IOException, PathException, MetadataException {
    // init the metadata from the operation log
    mgraph = new MGraph(ROOT_NAME);
    if (logFile.exists()) {
      try (FileReader fr = new FileReader(logFile);
          BufferedReader br = new BufferedReader(fr)) {
        String cmd;
        while ((cmd = br.readLine()) != null) {
          operation(cmd);
        }
      }
    }
  }

  /**
   * function for clearing MGraph.
   */
  public void clear() {
    lock.writeLock().lock();
    try {
      this.mgraph = new MGraph(ROOT_NAME);
      this.checkAndGetDataTypeCache.clear();
      this.mNodeCache.clear();
      this.seriesNumberInStorageGroups.clear();
      this.maxSeriesNumberAmongStorageGroup = 0;
      if (logWriter != null) {
        logWriter.close();
        logWriter = null;
      }
    } catch (IOException e) {
      logger.error("Cannot close metadata log writer, because:", e);
    } finally {
      lock.writeLock().unlock();
    }
  }

  public void operation(String cmd)
      throws PathException, IOException, MetadataException {
    //see addPathToMTree() to get the detailed format of the cmd
    String[] args = cmd.trim().split(",");
    switch (args[0]) {
      case MetadataOperationType.ADD_PATH_TO_MTREE:
        Map<String, String> props = null;
        if (args.length > 5) {
          String[] kv;
          props = new HashMap<>(args.length - 5 + 1, 1);
          for (int k = 5; k < args.length; k++) {
            kv = args[k].split("=");
            props.put(kv[0], kv[1]);
          }
        }

        addPathToMTree(new Path(args[1]), TSDataType.deserialize(Short.parseShort(args[2])),
            TSEncoding.deserialize(Short.parseShort(args[3])),
            CompressionType.deserialize(Short.parseShort(args[4])),
            props);
        break;
      case MetadataOperationType.DELETE_PATH_FROM_MTREE:
        deletePaths(Collections.singletonList(new Path(args[1])), false);
        break;
      case MetadataOperationType.SET_STORAGE_GROUP_TO_MTREE:
        setStorageGroupToMTree(args[1]);
        break;
      case MetadataOperationType.DELETE_STORAGE_GROUP_FROM_MTREE:
        List<Path> storageGroups = new ArrayList<>();
        for (int l = 1; l < args.length; l++) {
          storageGroups.add(new Path(args[l]));
        }
        deleteStorageGroupsFromMTree(storageGroups);
        break;
      case MetadataOperationType.ADD_A_PTREE:
        addAPTree(args[1]);
        break;
      case MetadataOperationType.ADD_A_PATH_TO_PTREE:
        addPathToPTree(args[1]);
        break;
      case MetadataOperationType.DELETE_PATH_FROM_PTREE:
        deletePathFromPTree(args[1]);
        break;
      case MetadataOperationType.LINK_MNODE_TO_PTREE:
        linkMNodeToPTree(args[1], args[2]);
        break;
      case MetadataOperationType.UNLINK_MNODE_FROM_PTREE:
        unlinkMNodeFromPTree(args[1], args[2]);
        break;
      case MetadataOperationType.SET_TTL:
        setTTL(args[1], Long.parseLong(args[2]));
        break;
      default:
        logger.error("Unrecognizable command {}", cmd);
    }
  }

  private BufferedWriter getLogWriter() throws IOException {
    if (logWriter == null) {
      File logFile = SystemFileFactory.INSTANCE.getFile(logFilePath);
      File metadataDir = SystemFileFactory.INSTANCE.getFile(schemaDir);
      if (!metadataDir.exists()) {
        if (metadataDir.mkdirs()) {
          logger.info("create schema folder {}.", metadataDir);
        } else {
          logger.info("create schema folder {} failed.", metadataDir);
        }
      }
      FileWriter fileWriter;
      fileWriter = new FileWriter(logFile, true);
      logWriter = new BufferedWriter(fileWriter);
    }
    return logWriter;
  }

  public boolean addPathToMTree(String path, TSDataType dataType, TSEncoding encoding,
      CompressionType compressor, Map<String, String> props)
      throws MetadataException, PathException {
    return addPathToMTree(new Path(path), dataType, encoding, compressor, props);
  }

  /**
   * <p> Add one timeseries to metadata tree.
   *
   * @param path the timeseries seriesPath
   * @param dataType the dateType {@code DataType} for the timeseries
   * @param encoding the encoding function {@code Encoding} for the timeseries
   * @param compressor the compressor function {@code Compressor} for the time series
   * @return whether the measurement occurs for the first time in this storage group (if true, the
   * measurement should be registered to the StorageEngine too)
   */
  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  public boolean addPathToMTree(Path path, TSDataType dataType, TSEncoding encoding,
      CompressionType compressor, Map<String, String> props)
      throws MetadataException, PathException {
    lock.writeLock().lock();
    try {
      if (pathExist(path.getFullPath())) {
        throw new TimeseriesAlreadyExistException(path.getFullPath());
      }
      IoTDBConfig conf = IoTDBDescriptor.getInstance().getConfig();
      if (!checkStorageGroupByPath(path.getFullPath())) {
        if (!conf.isAutoCreateSchemaEnabled()) {
          throw new MetadataException("Storage group should be created first");
        }
        String storageGroupName = getStorageGroupNameByAutoLevel(
            path.getFullPath(), conf.getDefaultStorageGroupLevel());
        setStorageGroupToMTree(storageGroupName);
      }
      // optimize the speed of adding timeseries
      String fileNodePath;
      try {
        fileNodePath = getStorageGroupNameByPath(path.getFullPath());
      } catch (StorageGroupException e) {
        throw new MetadataException(e);
      }
      // the two map is stored in the storage group node
      Map<String, MeasurementSchema> schemaMap = getStorageGroupSchemaMap(fileNodePath);
      Map<String, Integer> numSchemaMap = getStorageGroupNumSchemaMap(fileNodePath);
      String lastNode = path.getMeasurement();
      boolean isNewMeasurement = true;
      // Thread safety: just one thread can access/modify the schemaMap
      synchronized (schemaMap) {
        // Need to check the path again to avoid duplicated inserting by multi concurrent threads
        if (pathExist(path.getFullPath())) {
          throw new TimeseriesAlreadyExistException(path.getFullPath());
        }
        if (schemaMap.containsKey(lastNode)) {
          isNewMeasurement = false;
          MeasurementSchema columnSchema = schemaMap.get(lastNode);
          if (!columnSchema.getType().equals(dataType)
              || !columnSchema.getEncodingType().equals(encoding)
              || !columnSchema.getCompressor().equals(compressor)) {
            throw new MetadataException(String.format(
                "The resultDataType or encoding or compression of the last node %s is conflicting "
                    + "in the storage group %s", lastNode, fileNodePath));
          }
          try {
            addPathToMTreeInternal(path.getFullPath(), dataType, encoding, compressor, props);
          } catch (PathException | StorageGroupException e) {
            throw new MetadataException(e);
          } catch (IOException e) {
            throw new MetadataException(e.getMessage());
          }
          numSchemaMap.put(lastNode, numSchemaMap.get(lastNode) + 1);
        } else {
          try {
            addPathToMTreeInternal(path.getFullPath(), dataType, encoding, compressor, props);
          } catch (PathException | StorageGroupException e) {
            throw new MetadataException(e);
          } catch (IOException e) {
            throw new MetadataException(e.getMessage());
          }
          MeasurementSchema columnSchema;
          try {
            columnSchema = getSchemaForOnePath(path.toString());
          } catch (PathException e) {
            throw new MetadataException(e);
          }
          schemaMap.put(lastNode, columnSchema);
          numSchemaMap.put(lastNode, 1);
        }
        try {
          IoTDBConfigDynamicAdapter.getInstance().addOrDeleteTimeSeries(1);
        } catch (ConfigAdjusterException e) {
          // Undo create time series
          deletePaths(Collections.singletonList(path), true);
          throw new MetadataException(e);
        }
        return isNewMeasurement;
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * path will be added to mgraph with no check
   */
  private void addPathToMTreeInternal(String path, TSDataType dataType, TSEncoding encoding,
      CompressionType compressor, Map<String, String> props)
      throws PathException, IOException, StorageGroupException {

    mgraph.addPathToMTree(path, dataType, encoding, compressor, props);
    String storageGroupName = mgraph.getStorageGroupNameByPath(path);
    int size = seriesNumberInStorageGroups.get(storageGroupName);
    seriesNumberInStorageGroups.put(storageGroupName, size + 1);
    if (size + 1 > maxSeriesNumberAmongStorageGroup) {
      maxSeriesNumberAmongStorageGroup = size + 1;
    }
    if (writeToLog) {
      BufferedWriter writer = getLogWriter();
      writer.write(String.format("%s,%s,%s,%s,%s", MetadataOperationType.ADD_PATH_TO_MTREE,
          path, dataType.serialize(), encoding.serialize(), compressor.serialize()));
      if (props != null) {
        for (Map.Entry entry : props.entrySet()) {
          writer.write(String.format(",%s=%s", entry.getKey(), entry.getValue()));
        }
      }
      writer.newLine();
      writer.flush();
    }
  }

  /**
   * <p> Add one timeseries to metadata. Must invoke the<code>pathExist</code> and
   * <code>getStorageGroupNameByPath</code> method first to check timeseries. </p>
   *
   * this is just for compatibility  TEST ONLY
   *
   * @param path the timeseries seriesPath
   * @param dataType the dateType {@code DataType} for the timeseries
   * @param encoding the encoding function {@code Encoding} for the timeseries
   */
  public void addPathToMTree(String path, String dataType, String encoding)
      throws PathException, IOException, StorageGroupException {
    lock.writeLock().lock();
    try {
      TSDataType tsDataType = TSDataType.valueOf(dataType);
      TSEncoding tsEncoding = TSEncoding.valueOf(encoding);
      CompressionType type = CompressionType
          .valueOf(TSFileDescriptor.getInstance().getConfig().getCompressor());
      addPathToMTreeInternal(path, tsDataType, tsEncoding, type, Collections.emptyMap());
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * <p> Add one <code>deviceId</code> to metadata.</p>
   *
   * This is available IF and ONLY IF creating schema automatically is enabled.
   *
   * @param deviceId the deviceId of a timeseries seriesPath
   */
  private MNode addDeviceIdToMTree(String deviceId) throws PathException {
    lock.writeLock().lock();
    try {
      return mgraph.addDeviceIdToMTree(deviceId);
    } finally {
      lock.writeLock().unlock();
    }
  }

  private List<String> collectPaths(List<Path> paths) throws MetadataException {
    Set<String> pathSet = new HashSet<>();
    // Attention: Monitor storage group seriesPath is not allowed to be deleted
    for (Path p : paths) {
      List<String> subPaths;
      subPaths = getPaths(p.getFullPath());
      if (subPaths.isEmpty()) {
        throw new MetadataException(String
            .format("There are no timeseries in the prefix of %s seriesPath",
                p.getFullPath()));
      }
      List<String> newSubPaths = new ArrayList<>();
      for (String eachSubPath : subPaths) {
        String storageGroupName;
        try {
          storageGroupName = getStorageGroupNameByPath(eachSubPath);
        } catch (StorageGroupException e) {
          throw new MetadataException(e);
        }

        if (MonitorConstants.STAT_STORAGE_GROUP_PREFIX.equals(storageGroupName)) {
          continue;
        }
        newSubPaths.add(eachSubPath);
      }
      pathSet.addAll(newSubPaths);
    }
    for (String p : pathSet) {
      if (!pathExist(p)) {
        throw new MetadataException(String.format(
            "Timeseries %s does not exist and cannot be deleted", p));
      }
    }
    return new ArrayList<>(pathSet);
  }

  /**
   * delete given paths from metadata.
   *
   * @param deletePathList list of paths to be deleted
   * @return a set contains StorageGroups that contain no more timeseries after this deletion and
   * files of such StorageGroups should be deleted to reclaim disk space.
   */
  public Set<String> deletePaths(List<Path> deletePathList, boolean isUndo)
      throws MetadataException {
    if (deletePathList != null && !deletePathList.isEmpty()) {
      List<String> fullPath = collectPaths(deletePathList);

      Set<String> emptyStorageGroups = new HashSet<>();
      for (String p : fullPath) {
        if (!isUndo) {
          try {
            IoTDBConfigDynamicAdapter.getInstance().addOrDeleteTimeSeries(-1);
          } catch (ConfigAdjusterException e) {
            throw new MetadataException(e);
          }
        }
        String emptiedStorageGroup = deletePath(p);
        if (emptiedStorageGroup != null) {
          emptyStorageGroups.add(emptiedStorageGroup);
        }
      }
      return emptyStorageGroups;
    }
    return Collections.emptySet();
  }

  private String deletePath(String pathStr) throws MetadataException {
    String storageGroupName;
    try {
      storageGroupName = getStorageGroupNameByPath(pathStr);
    } catch (StorageGroupException e) {
      throw new MetadataException(e);
    }
    String emptiedStorageGroup;
    // the two maps are stored in the storage group node
    Map<String, MeasurementSchema> schemaMap = getStorageGroupSchemaMap(storageGroupName);
    Map<String, Integer> numSchemaMap = getStorageGroupNumSchemaMap(storageGroupName);
    // Thread safety: just one thread can access/modify the schemaMap
    synchronized (schemaMap) {
      // TODO: don't delete the storage group seriesPath recursively
      Path path = new Path(pathStr);
      String measurementId = path.getMeasurement();
      if (numSchemaMap.get(measurementId) == 1) {
        numSchemaMap.remove(measurementId);
        schemaMap.remove(measurementId);
      } else {
        numSchemaMap.put(measurementId, numSchemaMap.get(measurementId) - 1);
      }
      try {
        emptiedStorageGroup = deletePathFromMTree(pathStr);
      } catch (PathException | StorageGroupException e) {
        throw new MetadataException(e);
      } catch (IOException e) {
        throw new MetadataException(e.getMessage());
      }
    }
    return emptiedStorageGroup;
  }

  /**
   * function for deleting a given path from mTree.
   *
   * @return the related storage group name if there is no path in the storage group anymore;
   * otherwise null
   */
  private String deletePathFromMTree(String path)
      throws PathException, IOException, StorageGroupException {
    lock.writeLock().lock();
    try {
      checkAndGetDataTypeCache.clear();
      mNodeCache.clear();
      String storageGroupName = mgraph.deletePath(path);
      if (writeToLog) {
        BufferedWriter writer = getLogWriter();
        writer.write(MetadataOperationType.DELETE_PATH_FROM_MTREE + "," + path);
        writer.newLine();
        writer.flush();
      }
      String storageGroup = getStorageGroupNameByPath(path);
      int size = seriesNumberInStorageGroups.get(storageGroup);
      seriesNumberInStorageGroups.put(storageGroup, size - 1);
      if (size == maxSeriesNumberAmongStorageGroup) {
        //recalculate
        if (seriesNumberInStorageGroups.isEmpty()) {
          maxSeriesNumberAmongStorageGroup = 0;
        } else {
          maxSeriesNumberAmongStorageGroup = seriesNumberInStorageGroups.values().stream()
              .max(Integer::compareTo).get();
        }
      } else {
        maxSeriesNumberAmongStorageGroup--;
      }
      return storageGroupName;
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * function for setting storage group of the given path to mTree.
   */
  public void setStorageGroupToMTree(String path) throws MetadataException {
    lock.writeLock().lock();
    try {
      if (mgraph.checkStorageGroup(path)) {
        return;
      }
      mgraph.setStorageGroup(path);
      IoTDBConfigDynamicAdapter.getInstance().addOrDeleteStorageGroup(1);
      seriesNumberInStorageGroups.put(path, 0);
      if (writeToLog) {
        BufferedWriter writer = getLogWriter();
        writer.write(MetadataOperationType.SET_STORAGE_GROUP_TO_MTREE + "," + path);
        writer.newLine();
        writer.flush();
      }
    } catch (StorageGroupException e) {
      throw new MetadataException(e);
    } catch (IOException e) {
      throw new MetadataException(e.getMessage());
    } catch (ConfigAdjusterException e) {
      try {
        mgraph.deleteStorageGroup(path);
        throw new MetadataException(e);
      } catch (PathException ex) {
        throw new MetadataException(ex);
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * function for deleting storage groups of the given path from mTree. the log format is like
   * "delete_storage_group,sg1,sg2,sg3" TODO: return value unused
   */
  public boolean deleteStorageGroupsFromMTree(List<Path> deletePathList)
      throws MetadataException {
    List<String> pathList = new ArrayList<>();
    StringBuilder jointPath = new StringBuilder();
    for (Path storagePath : deletePathList) {
      pathList.add(storagePath.getFullPath());
      jointPath.append(",").append(storagePath.getFullPath());
    }
    lock.writeLock().lock();
    try {
      if (writeToLog) {
        BufferedWriter writer = getLogWriter();
        writer.write(MetadataOperationType.DELETE_STORAGE_GROUP_FROM_MTREE + jointPath);
        writer.newLine();
        writer.flush();
      }
      for (String delStorageGroup : pathList) {
        try {
          checkAndGetDataTypeCache.clear();
          mNodeCache.clear();
          IoTDBConfigDynamicAdapter.getInstance().addOrDeleteStorageGroup(-1);
          mgraph.deleteStorageGroup(delStorageGroup);
          seriesNumberInStorageGroups.remove(delStorageGroup);
        } catch (PathException e) {
          try {
            IoTDBConfigDynamicAdapter.getInstance().addOrDeleteStorageGroup(1);
          } catch (ConfigAdjusterException ex) {
            throw new MetadataException(ex);
          }
          throw new MetadataException(e);
        }
      }
    } catch (ConfigAdjusterException e) {
      throw new MetadataException(e);
    } catch (IOException e) {
      throw new MetadataException(e.getMessage());
    } finally {
      lock.writeLock().unlock();
    }
    return true;
  }

  /**
   * function for checking if the given path is storage group of mTree or not.
   *
   * @apiNote :for cluster
   */
  boolean checkStorageGroupOfMTree(String path) {
    lock.readLock().lock();
    try {
      return mgraph.checkStorageGroup(path);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * function for adding a pTree.
   */
  public void addAPTree(String ptreeRootName) throws IOException, MetadataException {

    lock.writeLock().lock();
    try {
      mgraph.addAPTree(ptreeRootName);
      if (writeToLog) {
        BufferedWriter writer = getLogWriter();
        writer.write(MetadataOperationType.ADD_A_PTREE + "," + ptreeRootName);
        writer.newLine();
        writer.flush();
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * function for adding a given path to pTree.
   */
  public void addPathToPTree(String path)
      throws PathException, IOException {

    lock.writeLock().lock();
    try {
      mgraph.addPathToPTree(path);
      if (writeToLog) {
        BufferedWriter writer = getLogWriter();
        writer.write(MetadataOperationType.ADD_A_PATH_TO_PTREE + "," + path);
        writer.newLine();
        writer.flush();
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * function for deleting a given path from pTree.
   */
  public void deletePathFromPTree(String path) throws PathException, IOException {

    lock.writeLock().lock();
    try {
      mgraph.deletePath(path);
      if (writeToLog) {
        BufferedWriter writer = getLogWriter();
        writer.write(MetadataOperationType.DELETE_PATH_FROM_PTREE + "," + path);
        writer.newLine();
        writer.flush();
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * function for linking MNode to pTree.
   */
  public void linkMNodeToPTree(String path, String mpath) throws PathException, IOException {

    lock.writeLock().lock();
    try {
      mgraph.linkMNodeToPTree(path, mpath);
      if (writeToLog) {
        BufferedWriter writer = getLogWriter();
        writer.write(MetadataOperationType.LINK_MNODE_TO_PTREE + "," + path + "," + mpath);
        writer.newLine();
        writer.flush();
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * function for unlinking MNode from pTree.
   */
  public void unlinkMNodeFromPTree(String path, String mpath)
      throws PathException, IOException {

    lock.writeLock().lock();
    try {
      mgraph.unlinkMNodeFromPTree(path, mpath);
      if (writeToLog) {
        BufferedWriter writer = getLogWriter();
        writer.write(MetadataOperationType.UNLINK_MNODE_FROM_PTREE + "," + path + "," + mpath);
        writer.newLine();
        writer.flush();
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Get series type for given seriesPath.
   *
   * @return TSDataType
   */
  public TSDataType getSeriesType(String fullPath) throws PathException {
    lock.readLock().lock();
    try {
      return getSchemaForOnePath(fullPath).getType();
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * function for getting series type.
   */
  public TSDataType getSeriesType(MNode node, String fullPath) throws PathException {

    lock.readLock().lock();
    try {
      return getSchemaForOnePath(node, fullPath).getType();
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * function for getting series type with check.
   */
  TSDataType getSeriesTypeWithCheck(MNode node, String fullPath) throws PathException {

    lock.readLock().lock();
    try {
      return getSchemaForOnePathWithCheck(node, fullPath).getType();
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * unction for getting series type with check.
   */
  TSDataType getSeriesTypeWithCheck(String fullPath) throws PathException {

    lock.readLock().lock();
    try {
      return getSchemaForOnePathWithCheck(fullPath).getType();
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Get all device type in current Metadata Tree.
   *
   * @return a HashMap contains all distinct device type separated by device Type
   */
  // future feature
  @SuppressWarnings("unused")
  public Map<String, List<MeasurementSchema>> getSchemaForAllType() throws PathException {

    lock.readLock().lock();
    try {
      return mgraph.getSchemaForAllType();
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Get the full Metadata info.
   *
   * @return A {@code Metadata} instance which stores all metadata info
   */
  public Metadata getMetadata() throws PathException {

    lock.readLock().lock();
    try {
      return mgraph.getMetadata();
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Get the full devices info.
   *
   * @return A HashSet instance which stores all devices info
   */
  public Set<String> getAllDevices() throws SQLException {

    lock.readLock().lock();
    try {
      return mgraph.getAllDevices();
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Get all nodes from the given level
   *
   * @return A List instance which stores all node at given level
   */
  public List<String> getNodesList(String prefixPath, int nodeLevel) throws SQLException {

    lock.readLock().lock();
    try {
      return mgraph.getNodesList(prefixPath, nodeLevel);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * @param path A seriesPath represented one Delta object
   * @return a list contains all column schema
   * @deprecated Get all MeasurementSchemas for given delta object type.
   */
  @Deprecated
  public List<MeasurementSchema> getSchemaForOneType(String path) throws PathException {
    lock.readLock().lock();
    try {
      return mgraph.getSchemaForOneType(path);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Get all MeasurementSchemas for the storage group seriesPath.
   */
  public List<MeasurementSchema> getSchemaForStorageGroup(String path) {
    lock.readLock().lock();
    try {
      return mgraph.getSchemaInOneStorageGroup(path);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * function for getting schema map for one file node.
   */
  private Map<String, MeasurementSchema> getStorageGroupSchemaMap(String path) {

    lock.readLock().lock();
    try {
      return mgraph.getSchemaMapForOneFileNode(path);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * function for getting num schema map for one file node.
   */
  private Map<String, Integer> getStorageGroupNumSchemaMap(String path) {

    lock.readLock().lock();
    try {
      return mgraph.getNumSchemaMapForOneFileNode(path);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Calculate the count of storage-group nodes included in given seriesPath.
   *
   * @return The total count of storage-group nodes.
   */
  // future feature
  @SuppressWarnings("unused")
  public int getFileCountForOneType(String path) throws PathException {

    lock.readLock().lock();
    try {
      return mgraph.getFileCountForOneType(path);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Get the file name for given seriesPath Notice: This method could be called if and only if the
   * seriesPath includes one node whose {@code isStorageGroup} is true.
   *
   * @return A String represented the file name
   */
  public String getStorageGroupNameByPath(String path) throws StorageGroupException {

    lock.readLock().lock();
    try {
      return mgraph.getStorageGroupNameByPath(path);
    } catch (StorageGroupException e) {
      throw new StorageGroupException(e.getMessage());
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * function for getting file name by path. TODO: return value unused
   */
  private String getStorageGroupNameByPath(MNode node, String path) throws StorageGroupException {
    lock.readLock().lock();
    try {
      return mgraph.getStorageGroupNameByPath(node, path);
    } catch (StorageGroupException e) {
      throw new StorageGroupException(e.getMessage());
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * function for checking storage group name by path.
   */
  boolean checkStorageGroupByPath(String path) {

    lock.readLock().lock();
    try {
      return mgraph.checkStorageGroupByPath(path);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Get the names of all storage groups.
   *
   * @return A list which stores all storage group names.
   */
  public List<String> getAllStorageGroupNames() {

    lock.readLock().lock();
    try {
      return mgraph.getAllStorageGroupNames();
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * function for getting all storage groups' MNodes
   */
  public List<MNode> getAllStorageGroups() {
    lock.readLock().lock();
    try {
      return mgraph.getAllStorageGroupNodes();
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Get all storage group names for given seriesPath
   *
   * @return List of String represented all storage group names
   */
  List<String> getAllStorageGroupNamesByPath(String path) throws MetadataException {

    lock.readLock().lock();
    try {
      return mgraph.getAllStorageGroupNamesByPath(path);
    } catch (PathException e) {
      throw new MetadataException(e);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * return a HashMap contains all the paths separated by storage group name.
   */
  Map<String, List<String>> getAllPathGroupByStorageGroup(String path)
      throws MetadataException {
    lock.readLock().lock();
    try {
      return mgraph.getAllPathGroupByStorageGroup(path);
    } catch (PathException e) {
      throw new MetadataException(e);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Return all paths for given seriesPath if the seriesPath is abstract. Or return the seriesPath
   * itself.
   */
  public List<String> getPaths(String path) throws MetadataException {

    lock.readLock().lock();
    try {
      List<String> res = new ArrayList<>();
      Map<String, List<String>> pathsGroupBySG = getAllPathGroupByStorageGroup(path);
      for (List<String> ps : pathsGroupBySG.values()) {
        res.addAll(ps);
      }
      return res;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * function for getting all timeseries paths under the given seriesPath.
   */
  public List<List<String>> getShowTimeseriesPath(String path) throws PathException {
    lock.readLock().lock();
    try {
      return mgraph.getShowTimeseriesPath(path);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * function for getting leaf node path in the next level of given seriesPath.
   */
  List<String> getLeafNodePathInNextLevel(String path) throws PathException {
    lock.readLock().lock();
    try {
      return mgraph.getLeafNodePathInNextLevel(path);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * function for getting leaf node path in the next level of given seriesPath.
   */
  public Set<String> getChildNodePathInNextLevel(String path) throws PathException {
    lock.readLock().lock();
    try {
      return mgraph.getChildNodePathInNextLevel(path);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Check whether the seriesPath given exists.
   */
  public boolean pathExist(String path) {

    lock.readLock().lock();
    try {
      return mgraph.pathExist(path);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * function for checking whether the path exists.
   */
  boolean pathExist(MNode node, String path) {

    lock.readLock().lock();
    try {
      return mgraph.pathExist(node, path);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * function for getting node by path.
   */
  MNode getNodeByPath(String path) throws PathException {
    lock.readLock().lock();
    try {
      return mgraph.getNodeByPath(path);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * function for getting node by deviceId from cache.
   */
  public MNode getNodeByDeviceIdFromCache(String deviceId) throws CacheException, PathException {
    lock.readLock().lock();
    IoTDBConfig conf = IoTDBDescriptor.getInstance().getConfig();
    MNode node = null;
    boolean createSchema = false;
    boolean setStorageGroup = false;
    try {
      node = mNodeCache.get(deviceId);
    } catch (CacheException e) {
      if (!conf.isAutoCreateSchemaEnabled()) {
        throw e;
      } else {
        createSchema = true;
        setStorageGroup = e.getCause() instanceof StorageGroupException;
      }
    } finally {
      lock.readLock().unlock();
      if (createSchema) {
        if (setStorageGroup) {
          try {
            String storageGroupName = getStorageGroupNameByAutoLevel(
                deviceId, conf.getDefaultStorageGroupLevel());
            setStorageGroupToMTree(storageGroupName);
          } catch (MetadataException | PathException e1) {
            throw new CacheException(e1);
          }
        }
        node = addDeviceIdToMTree(deviceId);
      }
    }
    return node;
  }

  /**
   * function for getting node by path with check.
   */
  public MNode getNodeByPathWithCheck(String path) throws PathException, StorageGroupException {
    lock.readLock().lock();
    try {
      return mgraph.getNodeByPathWithCheck(path);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Get MeasurementSchema for given seriesPath. Notice: Path must be a complete Path from root to
   * leaf node.
   */
  private MeasurementSchema getSchemaForOnePath(String path) throws PathException {

    lock.readLock().lock();
    try {
      return mgraph.getSchemaForOnePath(path);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * function for getting schema for one path.
   */
  private MeasurementSchema getSchemaForOnePath(MNode node, String path) throws PathException {

    lock.readLock().lock();
    try {
      return mgraph.getSchemaForOnePath(node, path);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * function for getting schema for one path with check.
   */
  private MeasurementSchema getSchemaForOnePathWithCheck(MNode node, String path)
      throws PathException {

    lock.readLock().lock();
    try {
      return mgraph.getSchemaForOnePathWithCheck(node, path);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * function for getting schema for one path with check.
   */
  private MeasurementSchema getSchemaForOnePathWithCheck(String path) throws PathException {

    lock.readLock().lock();
    try {
      return mgraph.getSchemaForOnePathWithCheck(path);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Check whether given seriesPath contains a MNode whose {@code MNode.isStorageGroup} is true.
   */
  public boolean checkFileLevel(List<Path> path) throws StorageGroupException {

    lock.readLock().lock();
    try {
      for (Path p : path) {
        getStorageGroupNameByPath(p.getFullPath());
      }
      return true;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * function for checking file level.
   */
  boolean checkFileLevel(MNode node, List<Path> path) throws StorageGroupException {

    lock.readLock().lock();
    try {
      for (Path p : path) {
        getStorageGroupNameByPath(node, p.getFullPath());
      }
      return true;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * function for checking file level.
   */
  boolean checkFileLevel(String path) throws StorageGroupException {

    lock.readLock().lock();
    try {
      getStorageGroupNameByPath(path);
      return true;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * function for checking file level with check.
   */
  boolean checkFileLevelWithCheck(MNode node, String path) throws StorageGroupException {
    lock.readLock().lock();
    try {
      getStorageGroupNameByPath(node, path);
      return true;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * function for getting metadata in string.
   */
  public String getMetadataInString() {
    lock.readLock().lock();
    try {
      return TIME_SERIES_TREE_HEADER + mgraph.toString();
    } finally {
      lock.readLock().unlock();
    }
  }

  public int getSeriesNumber(String storageGroup) {
    return seriesNumberInStorageGroups.getOrDefault(storageGroup, 0);
  }

  /**
   * function for getting storage group name when creating schema automatically is enable
   */
  String getStorageGroupNameByAutoLevel(String fullPath, int level)
      throws PathException {
    String[] nodeNames = MetaUtils.getNodeNames(fullPath, DOUB_SEPARATOR);
    StringBuilder storageGroupName = new StringBuilder(nodeNames[0]);
    if (nodeNames.length < level || !storageGroupName.toString().equals(ROOT_NAME)) {
      throw new MTreePathException("Timeseries", fullPath);
    }
    for (int i = 1; i < level; i++) {
      storageGroupName.append(IoTDBConstant.PATH_SEPARATOR).append(nodeNames[i]);
    }
    return storageGroupName.toString();
  }

  /**
   * Check whether {@code seriesPath} exists and whether {@code seriesPath} has been set storage
   * group.
   *
   * @return {@link PathCheckRet}
   */
  PathCheckRet checkPathStorageGroupAndGetDataType(String path) throws PathException {
    try {
      return checkAndGetDataTypeCache.get(path);
    } catch (CacheException e) {
      throw new MTreePathException(e.getMessage());
    }
  }

  private PathCheckRet loadPathToCache(String path) throws CacheException {
    try {
      if (!pathExist(path)) {
        return new PathCheckRet(false, null);
      }
      List<Path> p = new ArrayList<>();
      p.add(new Path(path));
      if (!checkFileLevel(p)) {
        return new PathCheckRet(false, null);
      }
      return new PathCheckRet(true, getSeriesType(path));
    } catch (PathException | StorageGroupException e) {
      throw new CacheException(e);
    }
  }

  /**
   * Only for test
   */
  public void setMaxSeriesNumberAmongStorageGroup(long maxSeriesNumberAmongStorageGroup) {
    this.maxSeriesNumberAmongStorageGroup = maxSeriesNumberAmongStorageGroup;
  }

  public long getMaximalSeriesNumberAmongStorageGroups() {
    return maxSeriesNumberAmongStorageGroup;
  }

  private static class MManagerHolder {

    private MManagerHolder() {
      //allowed to do nothing
    }

    private static final MManager INSTANCE = new MManager();
  }

  public static class PathCheckRet {

    private boolean successfully;
    private TSDataType dataType;

    PathCheckRet(boolean successfully, TSDataType dataType) {
      this.successfully = successfully;
      this.dataType = dataType;
    }

    public boolean isSuccessfully() {
      return successfully;
    }

    public TSDataType getDataType() {
      return dataType;
    }
  }

  public void setTTL(String storageGroup, long dataTTL) throws PathException, IOException {
    lock.writeLock().lock();
    try {
      MNode sgNode = getNodeByPath(storageGroup);
      if (!sgNode.isStorageGroup()) {
        throw new NotStorageGroupException(storageGroup);
      }
      sgNode.setDataTTL(dataTTL);
      if (writeToLog) {
        BufferedWriter writer = getLogWriter();
        writer
            .write(String.format("%s,%s,%s", MetadataOperationType.SET_TTL, storageGroup, dataTTL));
        writer.newLine();
        writer.flush();
      }
    } finally {
      lock.writeLock().unlock();
    }
  }
}
