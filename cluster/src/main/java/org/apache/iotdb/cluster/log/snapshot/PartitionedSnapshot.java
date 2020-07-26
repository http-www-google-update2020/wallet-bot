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

package org.apache.iotdb.cluster.log.snapshot;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.apache.iotdb.cluster.log.Snapshot;

/**
 * PartitionedSnapshot stores the snapshot of each slot in a map.
 */
public class PartitionedSnapshot<T extends Snapshot> extends Snapshot {

  private Map<Integer, T> slotSnapshots;
  private SnapshotFactory<T> factory;

  public PartitionedSnapshot(SnapshotFactory<T> factory) {
    this(new HashMap<>(), factory);
  }

  private PartitionedSnapshot(
      Map<Integer, T> slotSnapshots, SnapshotFactory<T> factory) {
    this.slotSnapshots = slotSnapshots;
    this.factory = factory;
  }

  public void putSnapshot(int slot, T snapshot) {
    slotSnapshots.put(slot, snapshot);
  }

  @Override
  public ByteBuffer serialize() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    try (DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
      dataOutputStream.writeInt(slotSnapshots.size());
      for (Entry<Integer, T> entry : slotSnapshots.entrySet()) {
        dataOutputStream.writeInt(entry.getKey());
        dataOutputStream.write(entry.getValue().serialize().array());
      }
      dataOutputStream.writeLong(getLastLogIndex());
      dataOutputStream.writeLong(getLastLogTerm());
    } catch (IOException e) {
      // unreachable
    }

    return ByteBuffer.wrap(outputStream.toByteArray());
  }

  @Override
  public void deserialize(ByteBuffer buffer) {
    int size = buffer.getInt();
    for (int i = 0; i < size; i++) {
      int slot = buffer.getInt();
      T snapshot = factory.create();
      snapshot.deserialize(buffer);
      slotSnapshots.put(slot, snapshot);
    }
    setLastLogIndex(buffer.getLong());
    setLastLogTerm(buffer.getLong());
  }

  public Snapshot getSnapshot(int slot) {
    return slotSnapshots.get(slot);
  }

  @Override
  public String toString() {
    return "PartitionedSnapshot{" +
        "slotSnapshots=" + slotSnapshots.size() +
        ", lastLogIndex=" + lastLogIndex +
        ", lastLogTerm=" + lastLogTerm +
        '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PartitionedSnapshot<?> snapshot = (PartitionedSnapshot<?>) o;
    return Objects.equals(slotSnapshots, snapshot.slotSnapshots);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slotSnapshots);
  }
}
