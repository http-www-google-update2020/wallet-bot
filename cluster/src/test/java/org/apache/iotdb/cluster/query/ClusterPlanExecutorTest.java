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

package org.apache.iotdb.cluster.query;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.List;
import org.apache.iotdb.db.exception.StorageEngineException;
import org.apache.iotdb.db.exception.metadata.MetadataException;
import org.apache.iotdb.db.exception.query.QueryProcessException;
import org.apache.iotdb.db.metadata.mnode.StorageGroupMNode;
import org.apache.iotdb.db.qp.physical.crud.RawDataQueryPlan;
import org.apache.iotdb.db.query.context.QueryContext;
import org.apache.iotdb.db.query.control.QueryResourceManager;
import org.apache.iotdb.tsfile.exception.filter.QueryFilterOptimizationException;
import org.apache.iotdb.tsfile.read.query.dataset.QueryDataSet;
import org.junit.Before;
import org.junit.Test;

public class ClusterPlanExecutorTest extends BaseQueryTest{

  private ClusterPlanExecutor queryExecutor;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    queryExecutor = new ClusterPlanExecutor(testMetaMember);
  }

  @Test
  public void testQuery()
      throws QueryProcessException, QueryFilterOptimizationException, StorageEngineException, IOException,
      MetadataException {
    RawDataQueryPlan queryPlan = new RawDataQueryPlan();
    queryPlan.setDeduplicatedPaths(pathList);
    queryPlan.setDeduplicatedDataTypes(dataTypes);
    queryPlan.setPaths(pathList);
    queryPlan.setDataTypes(dataTypes);
    QueryContext context = new RemoteQueryContext(QueryResourceManager.getInstance().assignQueryId(true));

    QueryDataSet dataSet = queryExecutor.processQuery(queryPlan, context);
    checkSequentialDataset(dataSet, 0, 20);
  }

  @Test
  public void testMatchPaths() throws MetadataException {
    List<String> allMatchedPaths = queryExecutor.getPathsName("root.*.s0");
    for (int i = 0; i < allMatchedPaths.size(); i++) {
      assertEquals(pathList.get(i).getFullPath(), allMatchedPaths.get(i));
    }
  }

  @Test
  public void testGetAllStorageGroupNodes() {
    List<StorageGroupMNode> allStorageGroupNodes = queryExecutor.getAllStorageGroupNodes();
    for (int i = 0; i < allStorageGroupNodes.size(); i++) {
      assertEquals(testMetaMember.getAllStorageGroupNodes().get(i).getFullPath(), allStorageGroupNodes.get(i).getFullPath());
    }
  }

  @Test
  public void testGetAllStorageGroupNames() {
    List<String> allStorageGroupNames = queryExecutor.getAllStorageGroupNames();
    for (int i = 0; i < allStorageGroupNames.size(); i++) {
      assertEquals(testMetaMember.getAllStorageGroupNames().get(i), allStorageGroupNames.get(i));
    }
  }
}