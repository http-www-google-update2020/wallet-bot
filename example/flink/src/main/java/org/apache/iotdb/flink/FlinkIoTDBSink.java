/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.iotdb.flink;

import com.google.common.collect.Lists;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class FlinkIoTDBSink {
    public static void main(String[] args) throws Exception {
        // run the flink job on local mini cluster
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        IoTDBOptions options = new IoTDBOptions();
        options.setHost("127.0.0.1");
        options.setPort(6667);
        options.setUser("root");
        options.setPassword("root");
        options.setStorageGroup("root.sg");
        options.setTimeseriesOptionList(Lists.newArrayList(new IoTDBOptions.TimeseriesOption("root.sg.d1.s1")));

        IoTSerializationSchema serializationSchema = new DefaultIoTSerializationSchema();
        IoTDBSink ioTDBSink = new IoTDBSink(options, serializationSchema)
                // enable batching
                .withBatchSize(10);

        env.addSource(new SensorSource())
                .name("sensor-source")
                .setParallelism(1)
                .addSink(ioTDBSink)
                .name("iotdb-sink")
                .setParallelism(1);

        env.execute("iotdb-flink-example");
    }

    private static class SensorSource implements SourceFunction<Map<String,String>> {
        boolean running = true;

        @Override
        public void run(SourceContext context) throws Exception {
            Random random = new Random();
            while (running) {
                Map<String,String> tuple = new HashMap();
                tuple.put("device", "root.sg.d1");
                tuple.put("timestamp", String.valueOf(System.currentTimeMillis()));
                tuple.put("measurements", "s1");
                tuple.put("types", "DOUBLE");
                tuple.put("values", String.valueOf(random.nextDouble()));

                context.collect(tuple);
                Thread.sleep(1000);
            }
        }

        @Override
        public void cancel() {
            running = false;
        }
    }
}
