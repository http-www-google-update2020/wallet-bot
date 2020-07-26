/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at      http://www.apache.org/licenses/LICENSE-2.0  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the specific language governing permissions and limitations under the License.
 */

package org.apache.iotdb.db.metadata.structured;

import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;

import java.util.Set;

public class ArrayType implements StructuredType {

    private final StructuredType arrayType;

    public ArrayType(StructuredType arrayType) {
        this.arrayType = arrayType;
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
        return true;
    }

    @Override
    public StructuredType getArrayType() {
        return this.arrayType;
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
    public String toString() {
        return arrayType.toString() + "[]";
    }
}
