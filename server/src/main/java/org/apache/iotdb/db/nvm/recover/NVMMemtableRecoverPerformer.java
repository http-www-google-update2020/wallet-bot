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
package org.apache.iotdb.db.nvm.recover;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.iotdb.db.engine.storagegroup.TsFileResource;
import org.apache.iotdb.db.exception.StartupException;
import org.apache.iotdb.db.engine.memtable.nvm.NVMPrimitiveMemTable;
import org.apache.iotdb.db.nvm.space.NVMDataSpace;
import org.apache.iotdb.db.nvm.space.NVMSpaceManager;
import org.apache.iotdb.db.nvm.NVMSpaceMetadataManager;
import org.apache.iotdb.tsfile.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NVMMemtableRecoverPerformer {

  private static final Logger logger = LoggerFactory.getLogger(NVMMemtableRecoverPerformer.class);

  private final static NVMMemtableRecoverPerformer INSTANCE = new NVMMemtableRecoverPerformer();

  private NVMSpaceManager spaceManager = NVMSpaceManager.getInstance();
  private NVMSpaceMetadataManager metadataManager = NVMSpaceMetadataManager.getInstance();
  private Map<String, Map<String, Map<String, Pair<List<NVMDataSpace>, List<NVMDataSpace>>>>> dataMap;

  private NVMMemtableRecoverPerformer() {}

  public void init() throws StartupException {
    try {
      dataMap = recoverDataInNVM();
    } catch (IOException e) {
      throw new StartupException(e);
    }
  }

  public void close() {
    dataMap.clear();
  }

  public static NVMMemtableRecoverPerformer getInstance() {
    return INSTANCE;
  }

  private Map<String, Map<String, Map<String, Pair<List<NVMDataSpace>, List<NVMDataSpace>>>>> recoverDataInNVM()
      throws IOException {
    Map<String, Map<String, Map<String, Pair<List<NVMDataSpace>, List<NVMDataSpace>>>>> dataMap = new HashMap<>();
    List<Integer> validTimeSpaceIndexList = metadataManager.getValidTimeSpaceIndexList();
    logger.debug("Valid time space index num: {}", validTimeSpaceIndexList.size());

    for (Integer timeSpaceIndex : validTimeSpaceIndexList) {
      int valueSpaceIndex = metadataManager.getValueSpaceIndexByTimeSpaceIndex(timeSpaceIndex);
      NVMDataSpace timeSpace = spaceManager.getNVMDataSpaceByIndex(timeSpaceIndex);
      NVMDataSpace valueSpace = spaceManager.getNVMDataSpaceByIndex(valueSpaceIndex);

      String[] timeseries = metadataManager.getTimeseriesBySpaceIndex(timeSpaceIndex);
      Map<String, Map<String, Pair<List<NVMDataSpace>, List<NVMDataSpace>>>> deviceTVMap = dataMap.computeIfAbsent(timeseries[0], k -> new HashMap<>());
      Map<String, Pair<List<NVMDataSpace>, List<NVMDataSpace>>> measurementTVMap = deviceTVMap.computeIfAbsent(timeseries[1], k -> new HashMap<>());
      Pair<List<NVMDataSpace>, List<NVMDataSpace>> tvPairList = measurementTVMap.computeIfAbsent(timeseries[2], k -> new Pair<>(new ArrayList<>(), new ArrayList<>()));
      tvPairList.left.add(timeSpace);
      tvPairList.right.add(valueSpace);
    }
    return dataMap;
  }

  public void reconstructMemtable(NVMPrimitiveMemTable memTable, TsFileResource tsFileResource) {
    String sgId = memTable.getStorageGroupId();
    Map<String, Map<String, Pair<List<NVMDataSpace>, List<NVMDataSpace>>>> dataOfSG = dataMap.remove(sgId);
    if (dataOfSG == null) {
      return;
    }

    memTable.loadData(dataOfSG);

    Map<String, Long>[] maps = getMinMaxTimeMapFromData(dataOfSG);
    Map<String, Long> minTimeMap = maps[0];
    Map<String, Long> maxTimeMap = maps[1];
    minTimeMap.forEach((k, v) -> tsFileResource.updateStartTime(k, v));
    maxTimeMap.forEach((k, v) -> tsFileResource.updateEndTime(k, v));
  }

  private Map<String, Long>[] getMinMaxTimeMapFromData(Map<String, Map<String, Pair<List<NVMDataSpace>, List<NVMDataSpace>>>> sgDataMap) {
    Map<String, Long> minTimeMap = new HashMap<>();
    Map<String, Long> maxTimeMap = new HashMap<>();
    for (Entry<String, Map<String, Pair<List<NVMDataSpace>, List<NVMDataSpace>>>> deviceDataMapEntry : sgDataMap
        .entrySet()) {
      String deviceId = deviceDataMapEntry.getKey();
      Map<String, Pair<List<NVMDataSpace>, List<NVMDataSpace>>> measurementDataMap = deviceDataMapEntry.getValue();
      long minTime = Long.MAX_VALUE;
      long maxTime = Long.MIN_VALUE;
      for (Pair<List<NVMDataSpace>, List<NVMDataSpace>> tvListPair : measurementDataMap.values()) {
        List<NVMDataSpace> timeSpaceList = tvListPair.left;
        for (int i = 0; i < timeSpaceList.size(); i++) {
          NVMDataSpace timeSpace = timeSpaceList.get(i);
          int unitNum = timeSpace.getUnitNum();
          if (i == timeSpaceList.size() - 1) {
            unitNum = timeSpace.getValidUnitNum();
          }

          for (int j = 0; j < unitNum; j++) {
            long time = (long) timeSpace.getData(j);
            minTime = Math.min(minTime, time);
            maxTime = Math.max(maxTime, time);
          }
        }
      }
      if (minTime != Long.MAX_VALUE) {
        minTimeMap.put(deviceId, minTime);
      }
      if (maxTime != Long.MIN_VALUE) {
        maxTimeMap.put(deviceId, maxTime);
      }
    }
    Map<String, Long>[] res = new Map[2];
    res[0] = minTimeMap;
    res[1] = maxTimeMap;
    return res;
  }
}
