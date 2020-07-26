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
package org.apache.iotdb.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.pool.SessionDataSetWrapper;
import org.apache.iotdb.session.pool.SessionPool;

public class SessionTest {

  public static void sessionInsert() {

    for (int i = 0; i < 6; i++) {
      Thread thread = new Thread(new Write(i));
      thread.start();
    }

//    try {
//      Thread.sleep(5000);
//    } catch (InterruptedException e) {
//      e.printStackTrace();
//    }
//
//    new Thread(() -> {
//      try {
//        for (int i = 0; i < 6; i++) {
//          query(i);
//        }
//      } catch (IoTDBConnectionException e) {
//        e.printStackTrace();
//      } catch (StatementExecutionException e) {
//        e.printStackTrace();
//      }
//    }).start();

  }

  private static class Write implements Runnable {

    private int device;

    Random random = new Random();

    public Write(int i) {
      this.device = i;
    }

    @Override
    public void run() {
      SessionPool session = new SessionPool("127.0.0.1", 6667, "root", "root", 5);

      long time = 0;
      while (true) {
        if (device >= 4) {
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
        long start = System.currentTimeMillis();
        time += 5000;
        String deviceId = "root.sg.d1";
        List<String> measurements = new ArrayList<>();
        for (int i = 0; i < 50000; i++) {
          measurements.add("s" + (i + device * 50000));
        }

        List<String> values = new ArrayList<>();
        for (int i = 0; i < 50000; i++) {
          values.add(random.nextInt(1000) + "");
        }

        try {
          session.insertRecord(deviceId, time, measurements, values);
        } catch (IoTDBConnectionException e) {
          e.printStackTrace();
        } catch (StatementExecutionException e) {
          e.printStackTrace();
        }
        System.out.println(
            Thread.currentThread().getName() + " write: " + (System.currentTimeMillis() - start)
                + " time " + time);
      }
    }
  }

  private static void query(int device)
      throws IoTDBConnectionException, StatementExecutionException {
    SessionDataSetWrapper dataSet;
    SessionPool session = new SessionPool("127.0.0.1", 6667, "root", "root", 5);

    while (true) {
      try {
        Thread.sleep(5000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      long start = System.currentTimeMillis();

      StringBuilder builder = new StringBuilder("select last ");
      for (int c = 0; c < 49999; c++) {
        builder.append("s").append(c).append(",");
      }

      builder.append("s49999 ");
      builder.append("from root.sg1.d" + device);

      dataSet = session.executeQueryStatement(builder.toString());
      int a = 0;
      while (dataSet.hasNext()) {
        a++;
        dataSet.next();
      }
      System.out.print(Thread.currentThread().getName() + " read " + a + "  ");
      System.out.println(System.currentTimeMillis() - start);
      session.closeResultSet(dataSet);
    }

  }
}