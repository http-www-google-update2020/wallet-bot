/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at      http://www.apache.org/licenses/LICENSE-2.0  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the specific language governing permissions and limitations under the License.
 */

package org.apache.iotdb.db.metadata.structured;

import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;

import java.util.Objects;
import java.util.Set;

public class PrimitiveType implements StructuredType {

    private final TSDataType type;
    private final TSEncoding encoding;
    private final CompressionType compression;

    public PrimitiveType(TSDataType type, TSEncoding encoding, CompressionType compression) {
        this.type = type;
        this.encoding = encoding;
        this.compression = compression;
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

    @Override
    public TSDataType getPrimitiveType() {
        return this.type;
    }

    @Override
    public TSEncoding getEncoding() {
        return this.encoding;
    }

    @Override
    public CompressionType getCompression() {
        return this.compression;
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
        return false;
    }

    @Override
    public Set<String> getKeySet() {
        throw new UnsupportedOperationException();
    }

    @Override
    public StructuredType getChild(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrimitiveType that = (PrimitiveType) o;
        return type == that.type &&
                getEncoding() == that.getEncoding() &&
                getCompression() == that.getCompression();
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, getEncoding(), getCompression());
    }

    @Override
    public String toString() {
        return type.toString();
    }
}
