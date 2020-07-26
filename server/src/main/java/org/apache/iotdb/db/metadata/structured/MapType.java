/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at      http://www.apache.org/licenses/LICENSE-2.0  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the specific language governing permissions and limitations under the License.
 */

package org.apache.iotdb.db.metadata.structured;

import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MapType implements StructuredType {

    private final Map<String, StructuredType> children;

    public MapType() {
        this(new HashMap<>());
    }

    public MapType(Map<String, StructuredType> children) {
        this.children = children;
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public TSDataType getPrimitiveType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public TSEncoding getEncoding() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompressionType getCompression() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public StructuredType getArrayType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isMap() {
        return true;
    }

    @Override
    public Set<String> getKeySet() {
        return this.children.keySet();
    }

    @Override
    public StructuredType getChild(String name) {
        return this.children.get(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MapType mapType = (MapType) o;
        return Objects.equals(children, mapType.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(children);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        for (Map.Entry<String, StructuredType> entry : this.children.entrySet()) {
            builder.append("\"" + entry.getKey() + "\": " + entry.getValue().toString() + ",");
        }
        builder.append("}");
        return builder.toString();
    }
}
