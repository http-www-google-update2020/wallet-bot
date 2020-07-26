/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at      http://www.apache.org/licenses/LICENSE-2.0  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the specific language governing permissions and limitations under the License.
 */

package org.apache.iotdb.db.metadata.structured;

import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.*;

public class StructuredTypeTest {

    @Test
    public void describeGPS() {
        HashMap<String, StructuredType> children = new HashMap<>();
        children.put("latitude", new PrimitiveType(TSDataType.DOUBLE, TSEncoding.GORILLA, CompressionType.SNAPPY));
        children.put("longitude", new PrimitiveType(TSDataType.DOUBLE, TSEncoding.GORILLA, CompressionType.SNAPPY));

        MapType gpsStructure = new MapType(children);

        System.out.println(gpsStructure.toString());
    }
}