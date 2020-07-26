/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at      http://www.apache.org/licenses/LICENSE-2.0  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the specific language governing permissions and limitations under the License.
 */

package org.apache.iotdb.db.metadata.structured;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.iotdb.db.exception.query.QueryProcessException;
import org.apache.iotdb.db.qp.physical.crud.InsertPlan;
import org.apache.iotdb.db.utils.CommonUtils;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class takes responsibility of Managing all known and relevant structured-type information.
 */
public class SManager {

    private static final Logger logger = LoggerFactory.getLogger(SManager.class);

    private static final SManager INSTANCE = new SManager();

    private final Map<String, StructuredType> registeredTypes;

    public static SManager getInstance() {
        return SManager.INSTANCE;
    }

    public SManager() {
        registeredTypes = new HashMap<>();
    }

    public void register(String name, StructuredType type) {
        this.registeredTypes.put(name, type);
    }

    /**
     * Removes all structured type information and
     * transforms the plan in a primitive plan
     * that is handled downstream without any changes.
     */
    public InsertPlan translate(InsertPlan plan) {
        for (TSDataType type : plan.getTypes()) {
            if (type != null) {
                throw new IllegalArgumentException("Plan already has type information");
            }
        }
        List<String> measurements = new ArrayList<>();
        List<TSDataType> types = new ArrayList<>();
        List<Object> newValues = new ArrayList<>();
        // First, we check if it contains a structured type
        Object[] values = plan.getValues();
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            String measurement = plan.getMeasurements()[i];
            if (value instanceof String && ((String) value).contains("::")) {
                String str = StringUtils.strip(((String) value), "\"");
                str = StringEscapeUtils.unescapeJava(str);
                logger.info("Contains structured type for measurement '{}', rewriting...", measurement);
                // Now first parse the thing
                String jsonString = str.substring(0, str.indexOf("::"));
                String structName = str.substring(str.indexOf("::") + 2);

                // Lookup the type
                if (!this.registeredTypes.containsKey(structName)) {
                    throw new IllegalArgumentException("Plan references the Unknown Type '" + structName + "'!");
                }

                StructuredType type = this.registeredTypes.get(structName);

                // Now we have all type information and can use the type to get all elements
                if (type.isMap()) {

                    JSONObject map = JSON.parseObject(jsonString);

                    visitMap(measurement, type, map, measurements, types, newValues);
                } else if (type.isArray()) {
                    JSONArray jsonArray = JSON.parseArray(jsonString);

                    visitArray(measurement, type.getArrayType(), jsonArray, measurements, types, newValues);
                } else {
                    throw new NotImplementedException("Only type Map is supported currently!");
                }
            } else {
                // Do inference here
                measurements.add(measurement);
                types.add(plan.getTypes()[i]);
                newValues.add(value);
            }
        }
        return new InsertPlan(plan.getDeviceId(), plan.getTime(), measurements.toArray(new String[0]), types.toArray(new TSDataType[0]), newValues.toArray(new Object[0]));
    }

    private void visitArray(String prefix, StructuredType type, JSONArray jsonArray, List<String> measurements, List<TSDataType> types, List<Object> newValues) {
        for (int i1 = 0; i1 < jsonArray.size(); i1++) {
            if (type instanceof PrimitiveType) {
                visitPrimitive(prefix + "[" + i1 + "]", type, jsonArray.getString(i1), measurements, types, newValues);
            } else if (type instanceof MapType) {
                visitMap(prefix + "[" + i1 + "]", type, jsonArray.getJSONObject(i1), measurements, types, newValues);
            } else if (type instanceof ArrayType) {
                visitArray(prefix + "[" + i1 + "]", type.getArrayType(), jsonArray.getJSONArray(i1), measurements, types, newValues);
            } else {
                throw new NotImplementedException("Not supported type in array");
            }
        }
    }

    private void visitPrimitive(String prefix, StructuredType type, String valueAsString, List<String> measurements, List<TSDataType> types, List<Object> newValues) {
        measurements.add(prefix);
        TSDataType primitiveType = type.getPrimitiveType();
        types.add(primitiveType);
        try {
            newValues.add(CommonUtils.parseValue(primitiveType, valueAsString));
        } catch (QueryProcessException e) {
            throw new IllegalArgumentException("Unable to parse", e);
        }
    }

    private void visitMap(String prefix, StructuredType type, JSONObject map, List<String> measurements, List<TSDataType> types, List<Object> newValues) {
        for (String key : type.getKeySet()) {
            StructuredType child = type.getChild(key);
            if (child instanceof PrimitiveType) {
                // Fetch the value
                if (!map.containsKey(key)) {
                    throw new IllegalArgumentException("Value String misses the requested field '" + key + "'");
                }
                visitPrimitive(prefix + "." + key, child, map.getString(key), measurements, types, newValues);
            } else if (child instanceof MapType) {
                visitMap(prefix + "." + key, child, map.getJSONObject(key), measurements, types, newValues);
            } else if (child instanceof ArrayType) {
                visitArray(prefix + "." + key, child.getArrayType(), map.getJSONArray(key), measurements, types, newValues);
            } else {
                throw new UnsupportedOperationException("Not supporting nested...");
            }
        }
    }


}
