/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at      http://www.apache.org/licenses/LICENSE-2.0  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the specific language governing permissions and limitations under the License.
 */

package org.apache.iotdb.db.metadata.structured;

import org.apache.iotdb.db.qp.physical.crud.InsertPlan;
import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.*;

public class SManagerTest {

    @Test
    public void translateSimplePlan() {
        SManager sManager = new SManager();

        sManager.register("gps", gpsType());

        InsertPlan plan = sManager.translate(new InsertPlan("root.sg1.d1", 0, new String[]{"gps"}, new String[]{"{ \"lat\" : 40.0, \"long\" : 20.0}::gps"}));

        assertArrayEquals(new String[]{ "gps.lat", "gps.long" }, plan.getMeasurements());
        assertArrayEquals(new TSDataType[]{ TSDataType.DOUBLE, TSDataType.DOUBLE }, plan.getTypes());
        assertArrayEquals(new Object[]{ 40.0, 20.0 }, plan.getValues());
    }

    @Test
    public void arrayType() {
        SManager sManager = new SManager();

        sManager.register("two_int", new ArrayType(new PrimitiveType(TSDataType.INT32, TSEncoding.RLE, CompressionType.SNAPPY)));

        InsertPlan plan = sManager.translate(new InsertPlan("root.sg1.d1", 0, new String[]{"two"}, new String[]{"[1,2]::two_int"}));

        assertArrayEquals(new String[]{ "two[0]", "two[1]" }, plan.getMeasurements());
        assertArrayEquals(new TSDataType[]{ TSDataType.INT32, TSDataType.INT32 }, plan.getTypes());
        assertArrayEquals(new Object[]{ 1, 2 }, plan.getValues());
    }

    @Test
    public void nestedStructure() {
        SManager sManager = new SManager();

        sManager.register("nested", new MapType(Collections.singletonMap("a", new MapType(Collections.singletonMap("b", new PrimitiveType(TSDataType.INT32, TSEncoding.RLE, CompressionType.SNAPPY))))));

        InsertPlan plan = sManager.translate(new InsertPlan("root.sg1.d1", 0, new String[]{"two"}, new String[]{"{\"a\":{\"b\":1}}::nested"}));

        assertArrayEquals(new String[]{ "two.a.b" }, plan.getMeasurements());
        assertArrayEquals(new TSDataType[]{ TSDataType.INT32}, plan.getTypes());
        assertArrayEquals(new Object[]{ 1}, plan.getValues());
    }

    @Test
    public void nestedArray() {
        SManager sManager = new SManager();

        sManager.register("nested", new MapType(Collections.singletonMap("a", new ArrayType(new PrimitiveType(TSDataType.INT32, TSEncoding.RLE, CompressionType.SNAPPY)))));

        InsertPlan plan = sManager.translate(new InsertPlan("root.sg1.d1", 0, new String[]{"two"}, new String[]{"{\"a\":[1]}::nested"}));

        assertArrayEquals(new String[]{ "two.a[0]" }, plan.getMeasurements());
        assertArrayEquals(new TSDataType[]{ TSDataType.INT32}, plan.getTypes());
        assertArrayEquals(new Object[]{ 1 }, plan.getValues());
    }

    @Test
    public void arrayOfStructType() {
        SManager sManager = new SManager();

        sManager.register("struct_array", new ArrayType(new MapType(Collections.singletonMap("a", new PrimitiveType(TSDataType.INT32, TSEncoding.RLE, CompressionType.SNAPPY)))));

        InsertPlan plan = sManager.translate(new InsertPlan("root.sg1.d1", 0, new String[]{"two"}, new String[]{"[{\"a\":1}]::struct_array"}));

        assertArrayEquals(new String[]{ "two[0].a" }, plan.getMeasurements());
        assertArrayEquals(new TSDataType[]{ TSDataType.INT32}, plan.getTypes());
        assertArrayEquals(new Object[]{ 1 }, plan.getValues());
    }

    @Test
    public void arrayOfArray() {
        SManager sManager = new SManager();

        sManager.register("array_array", new ArrayType(new ArrayType(new PrimitiveType(TSDataType.INT32, TSEncoding.RLE, CompressionType.SNAPPY))));

        InsertPlan plan = sManager.translate(new InsertPlan("root.sg1.d1", 0, new String[]{"two"}, new String[]{"[[1]]::array_array"}));

        assertArrayEquals(new String[]{ "two[0][0]" }, plan.getMeasurements());
        assertArrayEquals(new TSDataType[]{ TSDataType.INT32}, plan.getTypes());
        assertArrayEquals(new Object[]{ 1 }, plan.getValues());
    }

    private StructuredType gpsType() {
        HashMap<String, StructuredType> children = new HashMap<>();
        children.put("lat", new PrimitiveType(TSDataType.DOUBLE, TSEncoding.GORILLA, CompressionType.SNAPPY));
        children.put("long", new PrimitiveType(TSDataType.DOUBLE, TSEncoding.GORILLA, CompressionType.SNAPPY));

        return new MapType(children);
    }
}