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
package org.apache.iotdb.db.query.reader;

import java.io.IOException;

public interface IReaderByTimestamp {

  /**
   * Returns the corresponding value under this timestamp. Returns null if no value under this
   * timestamp.
   * <p>
   * Note that calling this method will change the status of this reader irreversibly just like
   * <code>next</code>. The difference is that <code>next</code> moves one step forward while
   * <code>getValueInTimestamp</code> advances towards the given timestamp.
   * <p>
   * Attention: DO call this method with monotonically increasing timestamps. There is no guarantee
   * of correctness with any other way of calling. For example, DO NOT call this method twice with
   * the same timestamp.
   */
  Object getValueInTimestamp(long timestamp) throws IOException;

  boolean hasNext() throws IOException;
}
