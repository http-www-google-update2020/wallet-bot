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
package org.apache.iotdb.cluster.query.reader.querynode;

import java.io.IOException;
import java.util.List;
import org.apache.iotdb.db.query.reader.IBatchReader;
import org.apache.iotdb.tsfile.read.common.BatchData;

/**
 * Cluster batch reader, which provides another method to get batch data by batch timestamp.
 */
public interface IClusterSelectSeriesBatchReader extends IBatchReader {

  /**
   * Get batch data by batch time
   *
   * @param batchTime valid batch timestamp
   * @return corresponding batch data
   */
  BatchData nextBatch(List<Long> batchTime) throws IOException;

}
