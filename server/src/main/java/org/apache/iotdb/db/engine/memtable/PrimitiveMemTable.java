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

package org.apache.iotdb.db.engine.memtable;

import java.util.HashMap;
import java.util.Map;

import org.apache.iotdb.db.rescon.TVListAllocator;
import org.apache.iotdb.db.utils.datastructure.TVList;
import org.apache.iotdb.tsfile.write.schema.MeasurementSchema;

public class PrimitiveMemTable extends AbstractMemTable {

  public PrimitiveMemTable(String sgId) {
    super(sgId);
  }

  public PrimitiveMemTable(Map<String, Map<String, IWritableMemChunk>> memTableMap, String sgId) {
    super(memTableMap, sgId);
  }

  @Override
  protected IWritableMemChunk genMemSeries(String deviceId, String measurementId, MeasurementSchema schema) {
    return new WritableMemChunk(schema,
        (TVList) TVListAllocator.getInstance().allocate(schema.getType(), false));
  }

  @Override
  public IMemTable copy() {
    Map<String, Map<String, IWritableMemChunk>> newMap = new HashMap<>(getMemTableMap());

    return new PrimitiveMemTable(newMap, storageGroupId);
  }

  @Override
  public boolean isSignalMemTable() {
    return false;
  }

  @Override
  public int hashCode() {return (int) getVersion();}

  @Override
  public boolean equals(Object obj) {
    return this == obj;
  }
}
