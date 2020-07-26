/**
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
package org.apache.iotdb.cluster.integration;

import static org.apache.iotdb.cluster.integration.Constant.count;
import static org.apache.iotdb.cluster.integration.Constant.first;
import static org.apache.iotdb.cluster.integration.Constant.last;
import static org.apache.iotdb.cluster.integration.Constant.max_time;
import static org.apache.iotdb.cluster.integration.Constant.max_value;
import static org.apache.iotdb.cluster.integration.Constant.mean;
import static org.apache.iotdb.cluster.integration.Constant.min_time;
import static org.apache.iotdb.cluster.integration.Constant.min_value;
import static org.apache.iotdb.cluster.integration.Constant.sum;
import static org.apache.iotdb.cluster.utils.Utils.insertData;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.iotdb.cluster.config.ClusterConfig;
import org.apache.iotdb.cluster.config.ClusterDescriptor;
import org.apache.iotdb.cluster.entity.Server;
import org.apache.iotdb.cluster.utils.EnvironmentUtils;
import org.apache.iotdb.cluster.utils.QPExecutorUtils;
import org.apache.iotdb.cluster.utils.hash.PhysicalNode;
import org.apache.iotdb.jdbc.Config;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class IOTDBGroupByIT {

  private Server server;
  private static final ClusterConfig CLUSTER_CONFIG = ClusterDescriptor.getInstance().getConfig();
  private static final PhysicalNode localNode = new PhysicalNode(CLUSTER_CONFIG.getIp(),
      CLUSTER_CONFIG.getPort());

  private static String[] createSqls = new String[]{
      "SET STORAGE GROUP TO root.ln.wf01.wt01",
      "CREATE TIMESERIES root.ln.wf01.wt01.status WITH DATATYPE=BOOLEAN, ENCODING=PLAIN",
      "CREATE TIMESERIES root.ln.wf01.wt01.temperature WITH DATATYPE=DOUBLE, ENCODING=PLAIN",
      "CREATE TIMESERIES root.ln.wf01.wt01.hardware WITH DATATYPE=INT32, ENCODING=PLAIN"};
  private static String[] insertSqls = new String[]{
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(1, 1.1, false, 11)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(2, 2.2, true, 22)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(3, 3.3, false, 33 )",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(4, 4.4, false, 44)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(5, 5.5, false, 55)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(100, 100.1, false, 110)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(150, 200.2, true, 220)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(200, 300.3, false, 330 )",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(250, 400.4, false, 440)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(300, 500.5, false, 550)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(10, 10.1, false, 110)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(20, 20.2, true, 220)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(30, 30.3, false, 330 )",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(40, 40.4, false, 440)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(50, 50.5, false, 550)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(500, 100.1, false, 110)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(510, 200.2, true, 220)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(520, 300.3, false, 330 )",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(530, 400.4, false, 440)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(540, 500.5, false, 550)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(580, 100.1, false, 110)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(590, 200.2, true, 220)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(600, 300.3, false, 330 )",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(610, 400.4, false, 440)",
      "INSERT INTO root.ln.wf01.wt01(timestamp,temperature,status, hardware) "
          + "values(620, 500.5, false, 550)",
  };

  private static final String TIMESTAMP_STR = "Time";

  @Before
  public void setUp() throws Exception {
    EnvironmentUtils.closeStatMonitor();
    EnvironmentUtils.closeMemControl();
    CLUSTER_CONFIG.createAllPath();
    server = Server.getInstance();
    server.start();
    EnvironmentUtils.envSetUp();
    Class.forName(Config.JDBC_DRIVER_NAME);
    insertSql();
  }

  @After
  public void tearDown() throws Exception {
    server.stop();
    QPExecutorUtils.setLocalNodeAddr(localNode.getIp(), localNode.getPort());
    EnvironmentUtils.cleanEnv();
  }

  @Test
  public void countSumMeanTest() {
    String[] retArray1 = new String[]{
        "2,1,4.4,4.4",
        "5,3,35.8,11.933333333333332",
        "25,1,30.3,30.3",
        "50,1,50.5,50.5",
        "65,0,0.0,null",
        "85,1,100.1,100.1",
        "105,0,0.0,null",
        "125,0,0.0,null",
        "145,1,200.2,200.2",
        "310,0,0.0,null"
    };
    String[] retArray2 = new String[]{
        "2,2,7.7,3.85",
        "5,3,35.8,11.933333333333332",
        "25,1,30.3,30.3",
        "50,1,50.5,50.5",
        "65,0,0.0,null",
        "85,1,100.1,100.1",
        "105,0,0.0,null",
        "125,0,0.0,null",
        "145,1,200.2,200.2",
        "310,0,0.0,null"
    };
    try (Connection connection = DriverManager.
        getConnection("jdbc:iotdb://127.0.0.1:6667/", "root", "root")) {
      Statement statement = connection.createStatement();
      boolean hasResultSet = statement.execute(
          "select count(temperature), sum(temperature), mean(temperature) from "
              + "root.ln.wf01.wt01 where time > 3 "
              + "GROUP BY (20ms, 5,[2,30], [35,37], [50, 160], [310, 314])");

      Assert.assertTrue(hasResultSet);
      ResultSet resultSet = statement.getResultSet();
      int cnt = 0;
      while (resultSet.next()) {
        String ans = resultSet.getString(TIMESTAMP_STR) + "," + resultSet
            .getString(count("root.ln.wf01.wt01.temperature")) + "," +
            resultSet.getString(sum("root.ln.wf01.wt01.temperature")) + "," + resultSet
            .getString(mean("root.ln.wf01.wt01.temperature"));
        Assert.assertEquals(retArray1[cnt], ans);
        cnt++;
      }
      Assert.assertEquals(retArray1.length, cnt);
      statement.close();

      statement = connection.createStatement();
      hasResultSet = statement.execute(
          "select count(temperature), sum(temperature), mean(temperature) from "
              + "root.ln.wf01.wt01 where temperature > 3 "
              + "GROUP BY (20ms, 5,[2,30], [35,37], [50, 160], [310, 314])");

      Assert.assertTrue(hasResultSet);
      resultSet = statement.getResultSet();
      cnt = 0;
      while (resultSet.next()) {
        String ans = resultSet.getString(TIMESTAMP_STR) + "," + resultSet
            .getString(count("root.ln.wf01.wt01.temperature")) + "," +
            resultSet.getString(sum("root.ln.wf01.wt01.temperature")) + "," + resultSet
            .getString(mean("root.ln.wf01.wt01.temperature"));
        Assert.assertEquals(retArray2[cnt], ans);
        cnt++;
      }
      Assert.assertEquals(retArray2.length, cnt);
      statement.close();

    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void maxMinValeTimeTest() {
    String[] retArray1 = new String[]{
        "2,4.4,4.4,4,4",
        "5,20.2,5.5,20,5",
        "25,30.3,30.3,30,30",
        "50,50.5,50.5,50,50",
        "65,null,null,null,null",
        "85,100.1,100.1,100,100",
        "105,null,null,null,null",
        "125,null,null,null,null",
        "145,200.2,200.2,150,150",
        "310,null,null,null,null"
    };
    String[] retArray2 = new String[]{
        "2,4.4,3.3,4,3",
        "5,20.2,5.5,20,5",
        "25,30.3,30.3,30,30",
        "50,50.5,50.5,50,50",
        "65,null,null,null,null",
        "85,100.1,100.1,100,100",
        "105,null,null,null,null",
        "125,null,null,null,null",
        "145,200.2,200.2,150,150",
        "310,null,null,null,null"
    };
    try (Connection connection = DriverManager.
        getConnection("jdbc:iotdb://127.0.0.1:6667/", "root", "root")) {
      Statement statement = connection.createStatement();
      boolean hasResultSet = statement.execute(
          "select max_value(temperature), min_value(temperature), max_time(temperature), "
              + "min_time(temperature) from root.ln.wf01.wt01 where time > 3 "
              + "GROUP BY (20ms, 5,[2,30], [35,37], [50, 160], [310, 314])");

      Assert.assertTrue(hasResultSet);
      ResultSet resultSet = statement.getResultSet();
      int cnt = 0;
      while (resultSet.next()) {
        String ans = resultSet.getString(TIMESTAMP_STR) + "," + resultSet
            .getString(max_value("root.ln.wf01.wt01.temperature"))
            + "," + resultSet.getString(min_value("root.ln.wf01.wt01.temperature")) + ","
            + resultSet.getString(max_time("root.ln.wf01.wt01.temperature"))
            + "," + resultSet.getString(min_time("root.ln.wf01.wt01.temperature"));
        Assert.assertEquals(retArray1[cnt], ans);
        cnt++;
      }
      Assert.assertEquals(retArray1.length, cnt);
      statement.close();

      statement = connection.createStatement();
      hasResultSet = statement.execute(
          "select max_value(temperature), min_value(temperature), max_time(temperature), "
              + "min_time(temperature) from root.ln.wf01.wt01 where temperature > 3 "
              + "GROUP BY (20ms, 5,[2,30], [35,37], [50, 160], [310, 314])");

      Assert.assertTrue(hasResultSet);
      resultSet = statement.getResultSet();
      cnt = 0;
      while (resultSet.next()) {
        String ans = resultSet.getString(TIMESTAMP_STR) + "," + resultSet
            .getString(max_value("root.ln.wf01.wt01.temperature"))
            + "," + resultSet.getString(min_value("root.ln.wf01.wt01.temperature")) + ","
            + resultSet.getString(max_time("root.ln.wf01.wt01.temperature"))
            + "," + resultSet.getString(min_time("root.ln.wf01.wt01.temperature"));
        Assert.assertEquals(retArray2[cnt], ans);
        cnt++;
      }
      Assert.assertEquals(retArray2.length, cnt);
      statement.close();

    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void firstLastTest() {
    String[] retArray1 = new String[]{
        "2,4.4,4.4",
        "5,20.2,5.5",
        "25,30.3,30.3",
        "50,50.5,50.5",
        "65,null,null",
        "85,100.1,100.1",
        "105,null,null",
        "125,null,null",
        "145,200.2,200.2",
        "310,null,null"
    };
    String[] retArray2 = new String[]{
        "2,4.4,3.3",
        "5,20.2,5.5",
        "25,30.3,30.3",
        "50,50.5,50.5",
        "65,null,null",
        "85,100.1,100.1",
        "105,null,null",
        "125,null,null",
        "145,200.2,200.2",
        "310,null,null"
    };
    try (Connection connection = DriverManager.
        getConnection("jdbc:iotdb://127.0.0.1:6667/", "root", "root")) {
      Statement statement = connection.createStatement();
      boolean hasResultSet = statement.execute(
          "select last(temperature), first(temperature) from root.ln.wf01.wt01 where time > 3 "
              + "GROUP BY (20ms, 5,[2,30], [35,37], [50, 160], [310, 314])");

      Assert.assertTrue(hasResultSet);
      ResultSet resultSet = statement.getResultSet();
      int cnt = 0;
      while (resultSet.next()) {
        String ans = resultSet.getString(TIMESTAMP_STR) + "," + resultSet
            .getString(last("root.ln.wf01.wt01.temperature"))
            + "," + resultSet.getString(first("root.ln.wf01.wt01.temperature"));
        Assert.assertEquals(retArray1[cnt], ans);
        cnt++;
      }
      Assert.assertEquals(retArray1.length, cnt);
      statement.close();

      statement = connection.createStatement();
      hasResultSet = statement.execute(
          "select first(temperature), last(temperature) from root.ln.wf01.wt01 "
              + "where temperature > 3 "
              + "GROUP BY (20ms, 5,[2,30], [35,37], [50, 160], [310, 314])");

      Assert.assertTrue(hasResultSet);
      resultSet = statement.getResultSet();
      cnt = 0;
      while (resultSet.next()) {
        String ans = resultSet.getString(TIMESTAMP_STR) + "," + resultSet
            .getString(last("root.ln.wf01.wt01.temperature"))
            + "," + resultSet.getString(first("root.ln.wf01.wt01.temperature"));
        Assert.assertEquals(retArray2[cnt], ans);
        cnt++;
      }
      Assert.assertEquals(retArray2.length, cnt);
      statement.close();

    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void largeIntervalTest() {
    String[] retArray1 = new String[]{
        "2,4.4,4,20,4",
        "30,30.3,16,610,30",
        "620,500.5,1,620,620"
    };
    String[] retArray2 = new String[]{
        "2,3.3,5,20,3",
        "30,30.3,16,610,30",
        "620,500.5,1,620,620"
    };
    try (Connection connection = DriverManager.
        getConnection("jdbc:iotdb://127.0.0.1:6667/", "root", "root")) {
      Statement statement = connection.createStatement();
      boolean hasResultSet = statement.execute(
          "select min_value(temperature), count(temperature), max_time(temperature), "
              + "min_time(temperature) from root.ln.wf01.wt01 where time > 3 GROUP BY "
              + "(590ms, 30, [2, 30], [30, 120], [100, 120], [123, 125], [155, 550], [540, 680])");

      Assert.assertTrue(hasResultSet);
      ResultSet resultSet = statement.getResultSet();
      int cnt = 0;
      while (resultSet.next()) {
        String ans = resultSet.getString(TIMESTAMP_STR) + "," + resultSet
            .getString(min_value("root.ln.wf01.wt01.temperature"))
            + "," + resultSet.getString(count("root.ln.wf01.wt01.temperature")) + "," +
            resultSet.getString(max_time("root.ln.wf01.wt01.temperature"))
            + "," + resultSet.getString(min_time("root.ln.wf01.wt01.temperature"));
        Assert.assertEquals(retArray1[cnt], ans);
        cnt++;
      }
      Assert.assertEquals(retArray1.length, cnt);
      statement.close();

      statement = connection.createStatement();
      hasResultSet = statement.execute(
          "select min_value(temperature), count (temperature), max_time(temperature), "
              + "min_time(temperature) from root.ln.wf01.wt01 where temperature > 3 GROUP BY "
              + "(590ms, 30, [2, 30], [30, 120], [100, 120], [123, 125], [155, 550],[540, 680])");

      Assert.assertTrue(hasResultSet);
      resultSet = statement.getResultSet();
      cnt = 0;
      while (resultSet.next()) {
        String ans = resultSet.getString(TIMESTAMP_STR) + "," + resultSet
            .getString(min_value("root.ln.wf01.wt01.temperature"))
            + "," + resultSet.getString(count("root.ln.wf01.wt01.temperature")) + ","
            + resultSet.getString(max_time("root.ln.wf01.wt01.temperature"))
            + "," + resultSet.getString(min_time("root.ln.wf01.wt01.temperature"));
        Assert.assertEquals(retArray2[cnt], ans);
        cnt++;
      }
      Assert.assertEquals(retArray2.length, cnt);
      statement.close();

    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void smallPartitionTest() {
    String[] retArray1 = new String[]{
        "50,100.1,50.5,150.6",
        "615,500.5,500.5,500.5"

    };
    String[] retArray2 = new String[]{
        "50,100.1,50.5,150.6",
        "585,null,null,0.0",
        "590,500.5,200.2,700.7"
    };
    try (Connection connection = DriverManager.
        getConnection("jdbc:iotdb://127.0.0.1:6667/", "root", "root")) {
      Statement statement = connection.createStatement();
      boolean hasResultSet = statement.execute(
          "select last(temperature), first(temperature), sum(temperature) from "
              + "root.ln.wf01.wt01 where time > 3 "
              + "GROUP BY (80ms, 30,[50,100], [615, 650])");

      Assert.assertTrue(hasResultSet);
      ResultSet resultSet = statement.getResultSet();
      int cnt = 0;
      while (resultSet.next()) {
        String ans = resultSet.getString(TIMESTAMP_STR) + "," + resultSet
            .getString(last("root.ln.wf01.wt01.temperature"))
            + "," + resultSet.getString(first("root.ln.wf01.wt01.temperature")) + ","
            + resultSet.getString(sum("root.ln.wf01.wt01.temperature"));
        Assert.assertEquals(retArray1[cnt], ans);
        cnt++;
      }
      Assert.assertEquals(retArray1.length, cnt);
      statement.close();

      statement = connection.createStatement();
      hasResultSet = statement.execute(
          "select first(temperature), last(temperature), sum(temperature) from "
              + "root.ln.wf01.wt01 where temperature > 3 "
              + "GROUP BY (80ms, 30,[50,100], [585,590], [615, 650])");

      Assert.assertTrue(hasResultSet);
      resultSet = statement.getResultSet();
      cnt = 0;
      while (resultSet.next()) {
        String ans = resultSet.getString(TIMESTAMP_STR) + "," + resultSet
            .getString(last("root.ln.wf01.wt01.temperature"))
            + "," + resultSet.getString(first("root.ln.wf01.wt01.temperature")) + ","
            + resultSet.getString(sum("root.ln.wf01.wt01.temperature"));
        Assert.assertEquals(retArray2[cnt], ans);
        cnt++;
      }
      Assert.assertEquals(retArray2.length, cnt);
      statement.close();

    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  private void insertSql() {
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root",
            "root")) {
      insertData(connection, createSqls, insertSqls);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
