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
package org.apache.iotdb;

import java.util.ArrayList;
import java.util.List;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.pool.SessionDataSetWrapper;
import org.apache.iotdb.session.pool.SessionPool;

public class SessionExample {

  public static void main(String[] args) {

    for (int i = 0; i < 6; i++) {
      new Thread(new WriteThread(i)).start();
    }

    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    for (int i = 0; i < 6; i++) {
      new Thread(new ReadThread(i)).start();
    }

  }

  static class WriteThread implements Runnable{
    int device;

    WriteThread(int device) {
      this.device = device;
    }

    @Override
    public void run() {
      SessionPool session = new SessionPool("127.0.0.1", 6667, "root", "root", 6);

      long time = 0;
      while (true) {
        try {
          Thread.sleep(5000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        long start = System.currentTimeMillis();

        time += 5000;
        String deviceId = "root.sg1.d1";
        List<String> measurements = new ArrayList<>();
        for (int i = 0; i < 50000; i++) {
          measurements.add("s" + (i + device * 50000));
        }

        List<String> values = new ArrayList<>();
        for (int i = 0; i < 50000; i++) {
          values.add("1");
        }

        try {
          session.insertRecord(deviceId, time, measurements, values);
        } catch (IoTDBConnectionException | StatementExecutionException e) {
          e.printStackTrace();
        }
        System.out.println(
            Thread.currentThread().getName() + " write: " + (System.currentTimeMillis() - start));
      }
    }
  }

  static class ReadThread implements Runnable {
    int device;

    ReadThread(int device) {
      this.device = device;
    }

    @Override
    public void run() {
      SessionDataSetWrapper dataSet = null;
      SessionPool session = new SessionPool("127.0.0.1", 6667, "root", "root", 2);

      try {
//        while (true) {
          Thread.sleep(5000);
          long start = System.currentTimeMillis();

          StringBuilder builder = new StringBuilder("select last ");
          for (int c = 50000*device; c < 50000*device + 49999; c++) {
            builder.append("s").append(c).append(",");
          }

          builder.append("s" + ((device+1)*50000-1));
          builder.append(" from root.sg1.d1");

          dataSet = session.executeQueryStatement(builder.toString());
          System.out.println(builder.toString());
          int a = 0;
          while (dataSet.hasNext()) {
            a++;
            dataSet.next();
          }
          System.out.print(Thread.currentThread().getName() + " read " + a + "  ");
          System.out.println(System.currentTimeMillis() - start);
          session.closeResultSet(dataSet);
//        }
      } catch (Exception e) {
        e.printStackTrace();
      }

    }
  }

}