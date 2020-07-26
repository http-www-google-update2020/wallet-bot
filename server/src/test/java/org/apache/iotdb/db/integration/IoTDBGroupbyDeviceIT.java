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
package org.apache.iotdb.db.integration;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import org.apache.iotdb.db.service.IoTDB;
import org.apache.iotdb.db.utils.EnvironmentUtils;
import org.apache.iotdb.jdbc.Config;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class IoTDBGroupbyDeviceIT {

  private static IoTDB daemon;
  private static String[] sqls = new String[]{

      "SET STORAGE GROUP TO root.vehicle",
      "SET STORAGE GROUP TO root.other",

      "CREATE TIMESERIES root.vehicle.d0.s0 WITH DATATYPE=INT32, ENCODING=RLE",
      "CREATE TIMESERIES root.vehicle.d0.s1 WITH DATATYPE=INT64, ENCODING=RLE",
      "CREATE TIMESERIES root.vehicle.d0.s2 WITH DATATYPE=FLOAT, ENCODING=RLE",
      "CREATE TIMESERIES root.vehicle.d0.s3 WITH DATATYPE=TEXT, ENCODING=PLAIN",
      "CREATE TIMESERIES root.vehicle.d0.s4 WITH DATATYPE=BOOLEAN, ENCODING=PLAIN",

      "CREATE TIMESERIES root.vehicle.d1.s0 WITH DATATYPE=INT32, ENCODING=RLE",

      "CREATE TIMESERIES root.other.d1.s0 WITH DATATYPE=FLOAT, ENCODING=RLE",

      "insert into root.vehicle.d0(timestamp,s0) values(1,101)",
      "insert into root.vehicle.d0(timestamp,s0) values(2,198)",
      "insert into root.vehicle.d0(timestamp,s0) values(100,99)",
      "insert into root.vehicle.d0(timestamp,s0) values(101,99)",
      "insert into root.vehicle.d0(timestamp,s0) values(102,80)",
      "insert into root.vehicle.d0(timestamp,s0) values(103,99)",
      "insert into root.vehicle.d0(timestamp,s0) values(104,90)",
      "insert into root.vehicle.d0(timestamp,s0) values(105,99)",
      "insert into root.vehicle.d0(timestamp,s0) values(106,99)",
      "insert into root.vehicle.d0(timestamp,s0) values(2,10000)",
      "insert into root.vehicle.d0(timestamp,s0) values(50,10000)",
      "insert into root.vehicle.d0(timestamp,s0) values(1000,22222)",

      "insert into root.vehicle.d0(timestamp,s1) values(1,1101)",
      "insert into root.vehicle.d0(timestamp,s1) values(2,198)",
      "insert into root.vehicle.d0(timestamp,s1) values(100,199)",
      "insert into root.vehicle.d0(timestamp,s1) values(101,199)",
      "insert into root.vehicle.d0(timestamp,s1) values(102,180)",
      "insert into root.vehicle.d0(timestamp,s1) values(103,199)",
      "insert into root.vehicle.d0(timestamp,s1) values(104,190)",
      "insert into root.vehicle.d0(timestamp,s1) values(105,199)",
      "insert into root.vehicle.d0(timestamp,s1) values(2,40000)",
      "insert into root.vehicle.d0(timestamp,s1) values(50,50000)",
      "insert into root.vehicle.d0(timestamp,s1) values(1000,55555)",

      "insert into root.vehicle.d0(timestamp,s2) values(1000,55555)",
      "insert into root.vehicle.d0(timestamp,s2) values(2,2.22)",
      "insert into root.vehicle.d0(timestamp,s2) values(3,3.33)",
      "insert into root.vehicle.d0(timestamp,s2) values(4,4.44)",
      "insert into root.vehicle.d0(timestamp,s2) values(102,10.00)",
      "insert into root.vehicle.d0(timestamp,s2) values(105,11.11)",
      "insert into root.vehicle.d0(timestamp,s2) values(1000,1000.11)",

      "insert into root.vehicle.d0(timestamp,s3) values(60,'aaaaa')",
      "insert into root.vehicle.d0(timestamp,s3) values(70,'bbbbb')",
      "insert into root.vehicle.d0(timestamp,s3) values(80,'ccccc')",
      "insert into root.vehicle.d0(timestamp,s3) values(101,'ddddd')",
      "insert into root.vehicle.d0(timestamp,s3) values(102,'fffff')",

      "insert into root.vehicle.d1(timestamp,s0) values(1,999)",
      "insert into root.vehicle.d1(timestamp,s0) values(1000,888)",

      "insert into root.vehicle.d0(timestamp,s1) values(2000-01-01T08:00:00+08:00, 100)",
      "insert into root.vehicle.d0(timestamp,s3) values(2000-01-01T08:00:00+08:00, 'good')",

      "insert into root.vehicle.d0(timestamp,s4) values(100, false)",
      "insert into root.vehicle.d0(timestamp,s4) values(100, true)",

      "insert into root.other.d1(timestamp,s0) values(2, 3.14)",};

  @BeforeClass
  public static void setUp() throws Exception {
    EnvironmentUtils.closeStatMonitor();
    daemon = IoTDB.getInstance();
    daemon.active();
    EnvironmentUtils.envSetUp();

    insertData();

  }

  @AfterClass
  public static void tearDown() throws Exception {
    daemon.stop();
    EnvironmentUtils.cleanEnv();
  }

  private static void insertData() throws ClassNotFoundException {
    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {

      for (String sql : sqls) {
        statement.execute(sql);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void selectTest() throws ClassNotFoundException {
    String[] retArray = new String[]{
        "1,root.vehicle.d0,101,1101,null,null,null,",
        "2,root.vehicle.d0,10000,40000,2.22,null,null,",
        "3,root.vehicle.d0,null,null,3.33,null,null,",
        "4,root.vehicle.d0,null,null,4.44,null,null,",
        "50,root.vehicle.d0,10000,50000,null,null,null,",
        "60,root.vehicle.d0,null,null,null,aaaaa,null,",
        "70,root.vehicle.d0,null,null,null,bbbbb,null,",
        "80,root.vehicle.d0,null,null,null,ccccc,null,",
        "100,root.vehicle.d0,99,199,null,null,true,",
        "101,root.vehicle.d0,99,199,null,ddddd,null,",
        "102,root.vehicle.d0,80,180,10.0,fffff,null,",
        "103,root.vehicle.d0,99,199,null,null,null,",
        "104,root.vehicle.d0,90,190,null,null,null,",
        "105,root.vehicle.d0,99,199,11.11,null,null,",
        "106,root.vehicle.d0,99,null,null,null,null,",
        "1000,root.vehicle.d0,22222,55555,1000.11,null,null,",
        "946684800000,root.vehicle.d0,null,100,null,good,null,",
        "1,root.vehicle.d1,999,null,null,null,null,",
        "1000,root.vehicle.d1,888,null,null,null,null,",
    };

    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      boolean hasResultSet = statement.execute(
          "select * from root.vehicle group by device");
      Assert.assertTrue(hasResultSet);

      try (ResultSet resultSet = statement.getResultSet()) {
        ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
        StringBuilder header = new StringBuilder();
        for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
          header.append(resultSetMetaData.getColumnName(i)).append(",");
        }
        Assert.assertEquals("Time,Device,s0,s1,s2,s3,s4,", header.toString());
        Assert.assertEquals(Types.TIMESTAMP, resultSetMetaData.getColumnType(1));
        Assert.assertEquals(Types.VARCHAR, resultSetMetaData.getColumnType(2));
        Assert.assertEquals(Types.INTEGER, resultSetMetaData.getColumnType(3));
        Assert.assertEquals(Types.BIGINT, resultSetMetaData.getColumnType(4));
        Assert.assertEquals(Types.FLOAT, resultSetMetaData.getColumnType(5));
        Assert.assertEquals(Types.VARCHAR, resultSetMetaData.getColumnType(6));
        Assert.assertEquals(Types.BOOLEAN, resultSetMetaData.getColumnType(7));

        int cnt = 0;
        while (resultSet.next()) {
          StringBuilder builder = new StringBuilder();
          for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
            builder.append(resultSet.getString(i)).append(",");
          }
          Assert.assertEquals(retArray[cnt], builder.toString());
          cnt++;
        }
        Assert.assertEquals(19, cnt);
      }
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void selectWithDuplicatedPathsTest() throws ClassNotFoundException {
    String[] retArray = new String[]{
        "1,root.vehicle.d0,101,101,1101,",
        "2,root.vehicle.d0,10000,10000,40000,",
        "50,root.vehicle.d0,10000,10000,50000,",
        "100,root.vehicle.d0,99,99,199,",
        "101,root.vehicle.d0,99,99,199,",
        "102,root.vehicle.d0,80,80,180,",
        "103,root.vehicle.d0,99,99,199,",
        "104,root.vehicle.d0,90,90,190,",
        "105,root.vehicle.d0,99,99,199,",
        "106,root.vehicle.d0,99,99,null,",
        "1000,root.vehicle.d0,22222,22222,55555,",
        "946684800000,root.vehicle.d0,null,null,100,",
        "1,root.vehicle.d1,999,999,null,",
        "1000,root.vehicle.d1,888,888,null,"
    };

    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      boolean hasResultSet = statement.execute(
          "select s0,s0,s1 from root.vehicle.d0, root.vehicle.d1 group by device");
      Assert.assertTrue(hasResultSet);

      try (ResultSet resultSet = statement.getResultSet()) {
        ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
        StringBuilder header = new StringBuilder();
        for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
          header.append(resultSetMetaData.getColumnName(i)).append(",");
        }
        Assert.assertEquals("Time,Device,s0,s0,s1,", header.toString());
        Assert.assertEquals(Types.TIMESTAMP, resultSetMetaData.getColumnType(1));
        Assert.assertEquals(Types.VARCHAR, resultSetMetaData.getColumnType(2));
        Assert.assertEquals(Types.INTEGER, resultSetMetaData.getColumnType(3));
        Assert.assertEquals(Types.INTEGER, resultSetMetaData.getColumnType(4));
        Assert.assertEquals(Types.BIGINT, resultSetMetaData.getColumnType(5));

        int cnt = 0;
        while (resultSet.next()) {
          StringBuilder builder = new StringBuilder();
          for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
            builder.append(resultSet.getString(i)).append(",");
          }
          Assert.assertEquals(retArray[cnt], builder.toString());
          cnt++;
        }
        Assert.assertEquals(14, cnt);
      }
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void selectLimitTest() throws ClassNotFoundException {
    String[] retArray = new String[]{
        "2,root.vehicle.d0,10000,10000,40000,",
        "50,root.vehicle.d0,10000,10000,50000,",
        "100,root.vehicle.d0,99,99,199,",
        "101,root.vehicle.d0,99,99,199,",
        "102,root.vehicle.d0,80,80,180,",
        "103,root.vehicle.d0,99,99,199,",
        "104,root.vehicle.d0,90,90,190,",
        "105,root.vehicle.d0,99,99,199,",
        "106,root.vehicle.d0,99,99,null,",
        "1000,root.vehicle.d0,22222,22222,55555,",
    };

    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      boolean hasResultSet = statement.execute(
          "select s0,s0,s1 from root.vehicle.* limit 10 offset 1 group by device");
      Assert.assertTrue(hasResultSet);

      try (ResultSet resultSet = statement.getResultSet()) {
        ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
        StringBuilder header = new StringBuilder();
        for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
          header.append(resultSetMetaData.getColumnName(i)).append(",");
        }
        Assert.assertEquals("Time,Device,s0,s0,s1,", header.toString());
        Assert.assertEquals(Types.TIMESTAMP, resultSetMetaData.getColumnType(1));
        Assert.assertEquals(Types.VARCHAR, resultSetMetaData.getColumnType(2));
        Assert.assertEquals(Types.INTEGER, resultSetMetaData.getColumnType(3));
        Assert.assertEquals(Types.INTEGER, resultSetMetaData.getColumnType(4));
        Assert.assertEquals(Types.BIGINT, resultSetMetaData.getColumnType(5));

        int cnt = 0;
        while (resultSet.next()) {
          StringBuilder builder = new StringBuilder();
          for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
            builder.append(resultSet.getString(i)).append(",");
          }
          Assert.assertEquals(retArray[cnt], builder.toString());
          cnt++;
        }
        Assert.assertEquals(10, cnt);
      }
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void selectSlimitTest() throws ClassNotFoundException {
    String[] retArray = new String[]{
        "1,root.vehicle.d0,101,1101,",
        "2,root.vehicle.d0,10000,40000,",
        "50,root.vehicle.d0,10000,50000,",
        "100,root.vehicle.d0,99,199,",
        "101,root.vehicle.d0,99,199,",
        "102,root.vehicle.d0,80,180,",
        "103,root.vehicle.d0,99,199,",
        "104,root.vehicle.d0,90,190,",
        "105,root.vehicle.d0,99,199,",
        "106,root.vehicle.d0,99,null,",
        "1000,root.vehicle.d0,22222,55555,",
        "946684800000,root.vehicle.d0,null,100,",
        "1,root.vehicle.d1,999,null,",
        "1000,root.vehicle.d1,888,null,"
    };

    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      boolean hasResultSet = statement.execute(
          "select s0,s0,s1 from root.vehicle.* slimit 2 soffset 1 group by device");
      Assert.assertTrue(hasResultSet);

      try (ResultSet resultSet = statement.getResultSet()) {
        ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
        StringBuilder header = new StringBuilder();
        for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
          header.append(resultSetMetaData.getColumnName(i)).append(",");
        }
        Assert.assertEquals("Time,Device,s0,s1,", header.toString());
        Assert.assertEquals(Types.TIMESTAMP, resultSetMetaData.getColumnType(1));
        Assert.assertEquals(Types.VARCHAR, resultSetMetaData.getColumnType(2));
        Assert.assertEquals(Types.INTEGER, resultSetMetaData.getColumnType(3));
        Assert.assertEquals(Types.BIGINT, resultSetMetaData.getColumnType(4));

        int cnt = 0;
        while (resultSet.next()) {
          StringBuilder builder = new StringBuilder();
          for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
            builder.append(resultSet.getString(i)).append(",");
          }
          Assert.assertEquals(retArray[cnt], builder.toString());
          cnt++;
        }
        Assert.assertEquals(14, cnt);
      }
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }


  @Test
  public void selectWithValueFilterTest() throws ClassNotFoundException {
    String[] retArray = new String[]{
        "1,root.vehicle.d0,101,1101,null,null,null,",
        "2,root.vehicle.d0,10000,40000,2.22,null,null,",
        "50,root.vehicle.d0,10000,50000,null,null,null,",
        "100,root.vehicle.d0,99,199,null,null,true,",
        "101,root.vehicle.d0,99,199,null,ddddd,null,",
        "102,root.vehicle.d0,80,180,10.0,fffff,null,",
        "103,root.vehicle.d0,99,199,null,null,null,",
        "104,root.vehicle.d0,90,190,null,null,null,",
        "105,root.vehicle.d0,99,199,11.11,null,null,",
        "106,root.vehicle.d0,99,null,null,null,null,",
        "1000,root.vehicle.d0,22222,55555,1000.11,null,null,",
        "1,root.vehicle.d1,999,null,null,null,null,",
        "1000,root.vehicle.d1,888,null,null,null,null,",
    };

    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      boolean hasResultSet = statement.execute(
          "select * from root.vehicle where root.vehicle.d0.s0 > 0 group by device");
      Assert.assertTrue(hasResultSet);

      try (ResultSet resultSet = statement.getResultSet()) {
        ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
        StringBuilder header = new StringBuilder();
        for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
          header.append(resultSetMetaData.getColumnName(i)).append(",");
        }
        Assert.assertEquals("Time,Device,s0,s1,s2,s3,s4,", header.toString());
        Assert.assertEquals(Types.TIMESTAMP, resultSetMetaData.getColumnType(1));
        Assert.assertEquals(Types.VARCHAR, resultSetMetaData.getColumnType(2));
        Assert.assertEquals(Types.INTEGER, resultSetMetaData.getColumnType(3));
        Assert.assertEquals(Types.BIGINT, resultSetMetaData.getColumnType(4));
        Assert.assertEquals(Types.FLOAT, resultSetMetaData.getColumnType(5));
        Assert.assertEquals(Types.VARCHAR, resultSetMetaData.getColumnType(6));
        Assert.assertEquals(Types.BOOLEAN, resultSetMetaData.getColumnType(7));

        int cnt = 0;
        while (resultSet.next()) {
          StringBuilder builder = new StringBuilder();
          for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
            builder.append(resultSet.getString(i)).append(",");
          }
          Assert.assertEquals(retArray[cnt], builder.toString());
          cnt++;
        }
        Assert.assertEquals(13, cnt);
      }
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void aggregateTest() throws ClassNotFoundException {
    String[] retArray = new String[]{
        "0,root.vehicle.d1,2,null,null,null,null,",
        "0,root.vehicle.d0,11,11,6,6,1,"
    };

    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      boolean hasResultSet = statement.execute(
          "select count(s0),count(s1),count(s2),count(s3),count(s4) "
              + "from root.vehicle.d1,root.vehicle.d0 group by device");
      Assert.assertTrue(hasResultSet);

      try (ResultSet resultSet = statement.getResultSet()) {
        ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
        StringBuilder header = new StringBuilder();
        for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
          header.append(resultSetMetaData.getColumnName(i)).append(",");
        }
        Assert.assertEquals("Time,Device,count(s0),count(s1),count(s2),count(s3),count(s4),",
            header.toString());
        Assert.assertEquals(Types.TIMESTAMP, resultSetMetaData.getColumnType(1));
        Assert.assertEquals(Types.VARCHAR, resultSetMetaData.getColumnType(2));
        Assert.assertEquals(Types.BIGINT, resultSetMetaData.getColumnType(3));
        Assert.assertEquals(Types.BIGINT, resultSetMetaData.getColumnType(4));
        Assert.assertEquals(Types.BIGINT, resultSetMetaData.getColumnType(5));
        Assert.assertEquals(Types.BIGINT, resultSetMetaData.getColumnType(6));
        Assert.assertEquals(Types.BIGINT, resultSetMetaData.getColumnType(7));

        int cnt = 0;
        while (resultSet.next()) {
          StringBuilder builder = new StringBuilder();
          for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
            builder.append(resultSet.getString(i)).append(",");
          }
          Assert.assertEquals(retArray[cnt], builder.toString());
          cnt++;
        }
        Assert.assertEquals(2, cnt);
      }
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void groupbyTimeTest() throws ClassNotFoundException {
    String[] retArray = new String[]{
        "2,root.vehicle.d0,1,1,3,0,0,",
        "20,root.vehicle.d0,0,0,0,0,0,",
        "40,root.vehicle.d0,1,1,0,0,0,",
        "2,root.vehicle.d1,0,null,null,null,null,",
        "20,root.vehicle.d1,0,null,null,null,null,",
        "40,root.vehicle.d1,0,null,null,null,null,"
    };

    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      boolean hasResultSet = statement.execute(
          "select count(*) from root.vehicle GROUP BY (20ms,0,[2,50]) group by device");
      Assert.assertTrue(hasResultSet);

      try (ResultSet resultSet = statement.getResultSet()) {
        ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
        StringBuilder header = new StringBuilder();
        for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
          header.append(resultSetMetaData.getColumnName(i)).append(",");
        }
        Assert.assertEquals("Time,Device,count(s0),count(s1),count(s2),count(s3),count(s4),",
            header.toString());
        Assert.assertEquals(Types.TIMESTAMP, resultSetMetaData.getColumnType(1));
        Assert.assertEquals(Types.VARCHAR, resultSetMetaData.getColumnType(2));
        Assert.assertEquals(Types.BIGINT, resultSetMetaData.getColumnType(3));
        Assert.assertEquals(Types.BIGINT, resultSetMetaData.getColumnType(4));
        Assert.assertEquals(Types.BIGINT, resultSetMetaData.getColumnType(5));
        Assert.assertEquals(Types.BIGINT, resultSetMetaData.getColumnType(6));
        Assert.assertEquals(Types.BIGINT, resultSetMetaData.getColumnType(7));

        int cnt = 0;
        while (resultSet.next()) {
          StringBuilder builder = new StringBuilder();
          for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
            builder.append(resultSet.getString(i)).append(",");
          }
          Assert.assertEquals(retArray[cnt], builder.toString());
          cnt++;
        }
        Assert.assertEquals(6, cnt);
      }
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void fillTest() throws ClassNotFoundException {
    String[] retArray = new String[]{
        "3,root.vehicle.d0,10000,null,3.33,null,null,",
        "3,root.vehicle.d1,999,null,null,null,null,",
    };

    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      boolean hasResultSet = statement.execute(
          "select * from root.vehicle where time = 3 Fill(int32[previous, 5ms]) group by device");
      Assert.assertTrue(hasResultSet);

      try (ResultSet resultSet = statement.getResultSet()) {
        ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
        StringBuilder header = new StringBuilder();
        for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
          header.append(resultSetMetaData.getColumnName(i)).append(",");
        }
        Assert.assertEquals("Time,Device,s0,s1,s2,s3,s4,", header.toString());
        Assert.assertEquals(Types.TIMESTAMP, resultSetMetaData.getColumnType(1));
        Assert.assertEquals(Types.VARCHAR, resultSetMetaData.getColumnType(2));
        Assert.assertEquals(Types.INTEGER, resultSetMetaData.getColumnType(3));
        Assert.assertEquals(Types.BIGINT, resultSetMetaData.getColumnType(4));
        Assert.assertEquals(Types.FLOAT, resultSetMetaData.getColumnType(5));
        Assert.assertEquals(Types.VARCHAR, resultSetMetaData.getColumnType(6));
        Assert.assertEquals(Types.BOOLEAN, resultSetMetaData.getColumnType(7));

        int cnt = 0;
        while (resultSet.next()) {
          StringBuilder builder = new StringBuilder();
          for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
            builder.append(resultSet.getString(i)).append(",");
          }
          Assert.assertEquals(retArray[cnt], builder.toString());
          cnt++;
        }
        Assert.assertEquals(2, cnt);
      }
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void errorCaseTest1() throws ClassNotFoundException {
    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      boolean hasResultSet = statement.execute(
          "select d0.s1, d0.s2, d1.s0 from root.vehicle group by device");
      fail("No exception thrown.");
    } catch (Exception e) {
      Assert.assertTrue(e.getMessage().contains(
          "The paths of the SELECT clause can only be single level. In other words, "
              + "the paths of the SELECT clause can only be measurements or STAR, without DOT."));
    }
  }

  @Test
  public void errorCaseTest2() throws ClassNotFoundException {
    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      boolean hasResultSet = statement.execute(
          "select s0 from root.nonexistent.noneixtsent group by device");
      assertTrue(hasResultSet);
    } catch (Exception e) {
      Assert.assertTrue(e.getMessage().contains("do not select any existing series"));
    }
  }

  @Test
  public void errorCaseTest3() throws ClassNotFoundException {
    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      boolean hasResultSet = statement.execute(
          "select s0 from root.*.* group by device");
      fail("No exception thrown.");
    } catch (Exception e) {
      // root.vehicle.d0.s0 INT32
      // root.vehicle.d1.s0 INT32
      // root.other.d1.s0 FLOAT
      Assert.assertTrue(e.getMessage().contains(
          "The data types of the same measurement column should be the same across devices in "
              + "GROUP_BY_DEVICE sql."));
    }
  }

  @Test
  public void unusualCaseTest1() throws ClassNotFoundException {
    String[] retArray = new String[]{
        "0,root.other.d1,1,",
        "0,root.vehicle.d0,11,",
        "0,root.vehicle.d1,2,"
    };

    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      // root.vehicle.d0.s0 INT32
      // root.vehicle.d1.s0 INT32
      // root.other.d1.s0 FLOAT
      // but
      // count(root.vehicle.d0.s0) INT64
      // count(root.vehicle.d1.s0) INT64
      // count(root.other.d1.s0) INT64
      boolean hasResultSet = statement.execute(
          "select count(s0) from root.*.* group by device");
      Assert.assertTrue(hasResultSet);

      try (ResultSet resultSet = statement.getResultSet()) {
        ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
        StringBuilder header = new StringBuilder();
        for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
          header.append(resultSetMetaData.getColumnName(i)).append(",");
        }
        Assert.assertEquals("Time,Device,count(s0),",
            header.toString());
        Assert.assertEquals(Types.TIMESTAMP, resultSetMetaData.getColumnType(1));
        Assert.assertEquals(Types.VARCHAR, resultSetMetaData.getColumnType(2));
        Assert.assertEquals(Types.BIGINT, resultSetMetaData.getColumnType(3));

        int cnt = 0;
        while (resultSet.next()) {
          StringBuilder builder = new StringBuilder();
          for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
            builder.append(resultSet.getString(i)).append(",");
          }
          Assert.assertEquals(retArray[cnt], builder.toString());
          cnt++;
        }
        Assert.assertEquals(3, cnt);
      }
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void unusualCaseTest2() throws ClassNotFoundException {
    String[] retArray = new String[]{
        "1,root.vehicle.d0,101,101,1101,101,1101,null,null,null,",
        "2,root.vehicle.d0,10000,10000,40000,10000,40000,2.22,null,null,",
        "3,root.vehicle.d0,null,null,null,null,null,3.33,null,null,",
        "4,root.vehicle.d0,null,null,null,null,null,4.44,null,null,",
        "1,root.vehicle.d1,999,999,null,999,null,null,null,null,"
    };

    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
        Statement statement = connection.createStatement()) {
      // duplicated devices and nonexistent devices
      boolean hasResultSet = statement.execute(
          "select s0,s0,s1,* from root.vehicle.*, root.vehicle.d0, root.vehicle.d1, "
              + "root.nonexistent.* where time < 20 group by device");
      Assert.assertTrue(hasResultSet);

      try (ResultSet resultSet = statement.getResultSet()) {
        ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
        StringBuilder header = new StringBuilder();
        for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
          header.append(resultSetMetaData.getColumnName(i)).append(",");
        }
        Assert.assertEquals("Time,Device,s0,s0,s1,s0,s1,s2,s3,s4,",
            header.toString());
        Assert.assertEquals(Types.TIMESTAMP, resultSetMetaData.getColumnType(1));
        Assert.assertEquals(Types.VARCHAR, resultSetMetaData.getColumnType(2));
        Assert.assertEquals(Types.INTEGER, resultSetMetaData.getColumnType(3));
        Assert.assertEquals(Types.INTEGER, resultSetMetaData.getColumnType(4));
        Assert.assertEquals(Types.BIGINT, resultSetMetaData.getColumnType(5));
        Assert.assertEquals(Types.INTEGER, resultSetMetaData.getColumnType(6));
        Assert.assertEquals(Types.BIGINT, resultSetMetaData.getColumnType(7));
        Assert.assertEquals(Types.FLOAT, resultSetMetaData.getColumnType(8));
        Assert.assertEquals(Types.VARCHAR, resultSetMetaData.getColumnType(9));
        Assert.assertEquals(Types.BOOLEAN, resultSetMetaData.getColumnType(10));

        int cnt = 0;
        while (resultSet.next()) {
          StringBuilder builder = new StringBuilder();
          for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
            builder.append(resultSet.getString(i)).append(",");
          }
          Assert.assertEquals(retArray[cnt], builder.toString());
          cnt++;
        }
        Assert.assertEquals(5, cnt);
      }
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

}
