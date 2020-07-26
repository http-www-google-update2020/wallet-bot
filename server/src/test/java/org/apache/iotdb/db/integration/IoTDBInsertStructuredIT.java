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

import org.apache.iotdb.db.metadata.structured.*;
import org.apache.iotdb.db.qp.physical.crud.InsertPlan;
import org.apache.iotdb.db.utils.EnvironmentUtils;
import org.apache.iotdb.jdbc.Config;
import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.assertj.core.api.WithAssertions;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class IoTDBInsertStructuredIT implements WithAssertions {

  private static String[] sqls = new String[]{
  };

  @BeforeClass
  public static void setUp() throws Exception {
    EnvironmentUtils.closeStatMonitor();
    EnvironmentUtils.envSetUp();

    insertData();

  }

  @AfterClass
  public static void tearDown() throws Exception {
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
  public void insertMap() throws ClassNotFoundException {
    String[] retArray = new String[]{
        "root.sg1.d1.\"coordinates.lat\",null,root.sg1,DOUBLE,GORILLA,SNAPPY,",
        "root.sg1.d1.\"coordinates.long\",null,root.sg1,DOUBLE,GORILLA,SNAPPY,",
    };

    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
        .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
         Statement statement = connection.createStatement()) {

      // Prepare type
      SManager.getInstance().register("gps", gpsType());

      // Insert value
      statement.execute("INSERT INTO root.sg1.d1 (timestamp, coordinates) VALUES (NOW(), \"{\\\"lat\\\":40.0, \\\"long\\\":20.0}::gps\")");

      boolean hasResultSet = statement.execute(
          "SHOW TIMESERIES");
      Assert.assertTrue(hasResultSet);

      try (ResultSet resultSet = statement.getResultSet()) {
        ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
        StringBuilder header = new StringBuilder();
        for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
          header.append(resultSetMetaData.getColumnName(i)).append(",");
        }
        Assert.assertEquals("timeseries,alias,storage group,dataType,encoding,compression,", header.toString());
        Assert.assertEquals(Types.VARCHAR, resultSetMetaData.getColumnType(1));

        int cnt = 0;
        while (resultSet.next()) {
          StringBuilder builder = new StringBuilder();
          for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
            builder.append(resultSet.getString(i)).append(",");
          }
          Assert.assertEquals(retArray[cnt], builder.toString());
          cnt++;
        }
        Assert.assertEquals(retArray.length, cnt);
      }
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void insertArray() throws ClassNotFoundException {
    String[] retArray = new String[]{
            "root.sg1.d1.coordinates[0],null,root.sg1,INT32,RLE,SNAPPY,",
            "root.sg1.d1.coordinates[1],null,root.sg1,INT32,RLE,SNAPPY,",
    };

    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
            .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
         Statement statement = connection.createStatement()) {

      // Prepare type
      SManager.getInstance().register("two_int", new ArrayType(new PrimitiveType(TSDataType.INT32, TSEncoding.RLE, CompressionType.SNAPPY)));

      // Insert value
      statement.execute("INSERT INTO root.sg1.d1 (timestamp, coordinates) VALUES (NOW(), \"[1,2]::two_int\")");

      boolean hasResultSet = statement.execute(
              "SHOW TIMESERIES");
      Assert.assertTrue(hasResultSet);

      try (ResultSet resultSet = statement.getResultSet()) {
        ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
        StringBuilder header = new StringBuilder();
        for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
          header.append(resultSetMetaData.getColumnName(i)).append(",");
        }
        Assert.assertEquals("timeseries,alias,storage group,dataType,encoding,compression,", header.toString());
        Assert.assertEquals(Types.VARCHAR, resultSetMetaData.getColumnType(1));

        int cnt = 0;
        while (resultSet.next()) {
          StringBuilder builder = new StringBuilder();
          for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
            builder.append(resultSet.getString(i)).append(",");
          }
          Assert.assertEquals(retArray[cnt], builder.toString());
          cnt++;
        }
        Assert.assertEquals(retArray.length, cnt);
      }
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void insertComplex() throws ClassNotFoundException {
    String[] retArray = new String[]{
            "root.sg1.d1.\"coordinates.max_speed\",null,root.sg1,DOUBLE,GORILLA,SNAPPY,",
            "root.sg1.d1.\"coordinates.look.color\",null,root.sg1,TEXT,PLAIN,SNAPPY,",
            "root.sg1.d1.\"coordinates.look.clean\",null,root.sg1,BOOLEAN,RLE,SNAPPY,",
            "root.sg1.d1.\"coordinates.drivers[0]\",null,root.sg1,TEXT,PLAIN,SNAPPY,",
            "root.sg1.d1.\"coordinates.drivers[1]\",null,root.sg1,TEXT,PLAIN,SNAPPY,",
    };

    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
            .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
         Statement statement = connection.createStatement()) {

      // Prepare type
      HashMap<String, StructuredType> lookMap = new HashMap<>();
      lookMap.put("clean", new PrimitiveType(TSDataType.BOOLEAN, TSEncoding.PLAIN, CompressionType.UNCOMPRESSED));
      lookMap.put("color", new PrimitiveType(TSDataType.TEXT, TSEncoding.PLAIN, CompressionType.UNCOMPRESSED));

      HashMap<String, StructuredType> carMap = new HashMap<>();
      carMap.put("max_speed", new PrimitiveType(TSDataType.DOUBLE, TSEncoding.GORILLA, CompressionType.GZIP));
      carMap.put("look", new MapType(lookMap));
      carMap.put("drivers", new ArrayType(new PrimitiveType(TSDataType.TEXT, TSEncoding.PLAIN, CompressionType.UNCOMPRESSED)));

      MapType carType = new MapType(carMap);
      System.out.println("Using Type: " + carType);

      SManager.getInstance().register("car", carType);

      // Insert value
      statement.execute("INSERT INTO root.sg1.d1 (timestamp, coordinates) VALUES (NOW(), \"{\\\"max_speed\\\": 160.0, \\\"look\\\":{\\\"clean\\\":true, \\\"color\\\": \\\"blue\\\"}, \\\"drivers\\\":[\\\"julian\\\", \\\"xiangdong\\\"]}::car\")");

      boolean hasResultSet = statement.execute(
              "SHOW TIMESERIES");
      Assert.assertTrue(hasResultSet);

      try (ResultSet resultSet = statement.getResultSet()) {
        ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
        StringBuilder header = new StringBuilder();
        for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
          header.append(resultSetMetaData.getColumnName(i)).append(",");
        }
        Assert.assertEquals("timeseries,alias,storage group,dataType,encoding,compression,", header.toString());
        Assert.assertEquals(Types.VARCHAR, resultSetMetaData.getColumnType(1));

        int cnt = 0;
        while (resultSet.next()) {
          StringBuilder builder = new StringBuilder();
          for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
            builder.append(resultSet.getString(i)).append(",");
          }
          Assert.assertEquals(retArray[cnt], builder.toString());
          cnt++;
        }
        Assert.assertEquals(retArray.length, cnt);
      }
    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void insertMissingField_fails() throws ClassNotFoundException {
    String[] retArray = new String[]{
            "root.sg1.d1.\"coordinates.lat\",null,root.sg1,DOUBLE,GORILLA,SNAPPY,",
            "root.sg1.d1.\"coordinates.long\",null,root.sg1,DOUBLE,GORILLA,SNAPPY,",
    };

    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
            .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
         Statement statement = connection.createStatement()) {

      // Prepare type
      SManager.getInstance().register("gps", gpsType());

      // Insert value
      assertThatThrownBy(() -> statement.execute("INSERT INTO root.sg1.d1 (timestamp, coordinates) VALUES (NOW(), \"{\\\"lat\\\":40.0}::gps\")"))
              .hasMessage("500: Value String misses the requested field 'long'");

    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void insertUnknownType_fails() throws ClassNotFoundException {
    String[] retArray = new String[]{
            "root.sg1.d1.\"coordinates.lat\",null,root.sg1,DOUBLE,GORILLA,SNAPPY,",
            "root.sg1.d1.\"coordinates.long\",null,root.sg1,DOUBLE,GORILLA,SNAPPY,",
    };

    Class.forName(Config.JDBC_DRIVER_NAME);
    try (Connection connection = DriverManager
            .getConnection(Config.IOTDB_URL_PREFIX + "127.0.0.1:6667/", "root", "root");
         Statement statement = connection.createStatement()) {

      // Prepare type
      SManager.getInstance().register("gps", gpsType());

      // Insert value
      assertThatThrownBy(() -> statement.execute("INSERT INTO root.sg1.d1 (timestamp, coordinates) VALUES (NOW(), \"{\\\"lat\\\":40.0}::unknown_type\")"))
              .hasMessage("500: Plan references the Unknown Type 'unknown_type'!");

    } catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  private StructuredType gpsType() {
    HashMap<String, StructuredType> children = new HashMap<>();
    children.put("lat", new PrimitiveType(TSDataType.DOUBLE, TSEncoding.GORILLA, CompressionType.SNAPPY));
    children.put("long", new PrimitiveType(TSDataType.DOUBLE, TSEncoding.GORILLA, CompressionType.SNAPPY));

    return new MapType(children);
  }

}
