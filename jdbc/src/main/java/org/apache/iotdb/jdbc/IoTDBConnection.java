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
package org.apache.iotdb.jdbc;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.time.ZoneId;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import org.apache.iotdb.rpc.IoTDBRPCException;
import org.apache.iotdb.rpc.RpcUtils;
import org.apache.iotdb.service.rpc.thrift.*;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class IoTDBConnection implements Connection {
  private static final Logger logger = LoggerFactory.getLogger(IoTDBConnection.class);
  private final TSProtocolVersion protocolVersion = TSProtocolVersion.IOTDB_SERVICE_PROTOCOL_V1;
  public TSIService.Iface client = null;
  TS_SessionHandle sessionHandle = null;
  private IoTDBConnectionParams params;
  private boolean isClosed = true;
  private SQLWarning warningChain = null;
  private TSocket transport;
  private TSProtocolVersion protocol;
  private ZoneId zoneId;
  private boolean autoCommit;

  public IoTDBConnection() {
    // allowed to create an instance without parameter input.
  }

  public IoTDBConnection(String url, Properties info) throws SQLException, TTransportException {
    if (url == null) {
      throw new IoTDBURLException("Input url cannot be null");
    }
    params = Utils.parseUrl(url, info);

    openTransport();
    if(Config.rpcThriftCompressionEnable) {
      client = new TSIService.Client(new TCompactProtocol(transport));
    }
    else {
      client = new TSIService.Client(new TBinaryProtocol(transport));
    }
    // open client session
    openSession();
    // Wrap the client with a thread-safe proxy to serialize the RPC calls
    client = RpcUtils.newSynchronizedClient(client);
    autoCommit = false;
  }

  @Override
  public boolean isWrapperFor(Class<?> arg0) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public <T> T unwrap(Class<T> arg0) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public void abort(Executor arg0) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public void clearWarnings() {
    warningChain = null;
  }

  @Override
  public void close() throws SQLException {
    if (isClosed) {
      return;
    }
    TSCloseSessionReq req = new TSCloseSessionReq(sessionHandle);
    try {
      client.closeSession(req);
    } catch (TException e) {
      throw new SQLException("Error occurs when closing session at server. Maybe server is down.", e);
    } finally {
      isClosed = true;
      if (transport != null) {
        transport.close();
      }
    }
  }

  @Override
  public void commit() throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public Array createArrayOf(String arg0, Object[] arg1) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public Blob createBlob() throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public Clob createClob() throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public NClob createNClob() throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public SQLXML createSQLXML() throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public Statement createStatement() throws SQLException {
    if (isClosed) {
      throw new SQLException("Cannot create statement because connection is closed");
    }
    return new IoTDBStatement(this, client, sessionHandle, zoneId);
  }

  @Override
  public Statement createStatement(int resultSetType, int resultSetConcurrency)
      throws SQLException {
    if (resultSetConcurrency != ResultSet.CONCUR_READ_ONLY) {
      throw new SQLException(
          String.format("Statements with result set concurrency %d are not supported",
              resultSetConcurrency));
    }
    if (resultSetType == ResultSet.TYPE_SCROLL_SENSITIVE) {
      throw new SQLException(String.format("Statements with ResultSet type %d are not supported",
          resultSetType));
    }
    return new IoTDBStatement(this, client, sessionHandle, zoneId);
  }

  @Override
  public Statement createStatement(int arg0, int arg1, int arg2) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public Struct createStruct(String arg0, Object[] arg1) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public boolean getAutoCommit() {
    return autoCommit;
  }

  @Override
  public void setAutoCommit(boolean arg0) {
    autoCommit = arg0;
  }

  @Override
  public String getCatalog() {
    return "no catalog";
  }

  @Override
  public void setCatalog(String arg0) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public Properties getClientInfo() throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public void setClientInfo(Properties arg0) throws SQLClientInfoException {
    throw new SQLClientInfoException("Method not supported", null);
  }

  @Override
  public String getClientInfo(String arg0) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public int getHoldability() {
    // throw new SQLException("Method not supported");
    return 0;
  }

  @Override
  public void setHoldability(int arg0) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public DatabaseMetaData getMetaData() throws SQLException {
    if (isClosed) {
      throw new SQLException("Cannot create statement because connection is closed");
    }
    return new IoTDBDatabaseMetadata(this, client);
  }

  @Override
  public int getNetworkTimeout() {
    return Config.connectionTimeoutInMs;
  }

  @Override
  public String getSchema() throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public void setSchema(String arg0) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public int getTransactionIsolation() {
    return Connection.TRANSACTION_NONE;
  }

  @Override
  public void setTransactionIsolation(int arg0) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public Map<String, Class<?>> getTypeMap() throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public void setTypeMap(Map<String, Class<?>> arg0) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public SQLWarning getWarnings() {
    return warningChain;
  }

  @Override
  public boolean isClosed() {
    return isClosed;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public void setReadOnly(boolean arg0) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public boolean isValid(int arg0) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public String nativeSQL(String arg0) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public CallableStatement prepareCall(String arg0) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public CallableStatement prepareCall(String arg0, int arg1, int arg2) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public CallableStatement prepareCall(String arg0, int arg1, int arg2, int arg3)
      throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public PreparedStatement prepareStatement(String sql) throws SQLException {
    if (sql.equalsIgnoreCase("INSERT")) {
      return new IoTDBPreparedInsertionStatement(this, client, sessionHandle, zoneId);
    }
    return new IoTDBPreparedStatement(this, client, sessionHandle, sql, zoneId);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
      throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
      int resultSetHoldability) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public void releaseSavepoint(Savepoint arg0) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public void rollback() {
    // do nothing in rollback
  }

  @Override
  public void rollback(Savepoint arg0) {
    // do nothing in rollback
  }

  @Override
  public void setClientInfo(String arg0, String arg1) throws SQLClientInfoException {
    throw new SQLClientInfoException("Method not supported", null);
  }

  @Override
  public void setNetworkTimeout(Executor arg0, int arg1) throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public Savepoint setSavepoint() throws SQLException {
    throw new SQLException("Method not supported");
  }

  @Override
  public Savepoint setSavepoint(String arg0) throws SQLException {
    throw new SQLException("Method not supported");
  }

  private void openTransport() throws TTransportException {
    transport = new TSocket(params.getHost(), params.getPort(), Config.connectionTimeoutInMs);
    if (!transport.isOpen()) {
      transport.open();
    }
  }

  private void openSession() throws SQLException {
    TSOpenSessionReq openReq = new TSOpenSessionReq(TSProtocolVersion.IOTDB_SERVICE_PROTOCOL_V1);

    openReq.setUsername(params.getUsername());
    openReq.setPassword(params.getPassword());

    try {
      TSOpenSessionResp openResp = client.openSession(openReq);

      // validate connection
      try {
        RpcUtils.verifySuccess(openResp.getStatus());
      } catch (IoTDBRPCException e) {
        // failed to connect, disconnect from the server
        transport.close();
        throw new IoTDBSQLException(e.getMessage(), openResp.getStatus());
      }
      if (protocolVersion.getValue() != openResp.getServerProtocolVersion().getValue()) {
        throw new TException(String
            .format("Protocol not supported, Client version is %d, but Server version is %d",
                protocolVersion.getValue(), openResp.getServerProtocolVersion().getValue()));
      }
      setProtocol(openResp.getServerProtocolVersion());
      sessionHandle = openResp.getSessionHandle();

      if (zoneId != null) {
        setTimeZone(zoneId.toString());
      } else {
        zoneId = ZoneId.of(getTimeZone());
      }

    } catch (TException e) {
      throw new SQLException(String.format("Can not establish connection with %s.",
          params.getJdbcUriString()), e);
    }
    isClosed = false;
  }

  boolean reconnect() {
    boolean flag = false;
    for (int i = 1; i <= Config.RETRY_NUM; i++) {
      try {
        if (transport != null) {
          transport.close();
          openTransport();
          if(Config.rpcThriftCompressionEnable) {
            client = new TSIService.Client(new TCompactProtocol(transport));
          }
          else {
            client = new TSIService.Client(new TBinaryProtocol(transport));
          }
          openSession();
          client = RpcUtils.newSynchronizedClient(client);
          flag = true;
          break;
        }
      } catch (Exception e) {
        try {
          Thread.sleep(Config.RETRY_INTERVAL);
        } catch (InterruptedException e1) {
          logger.error("reconnect is interrupted.", e1);
        }
      }
    }
    return flag;
  }

  public String getTimeZone() throws TException, IoTDBSQLException {
    if (zoneId != null) {
      return zoneId.toString();
    }

    TSGetTimeZoneResp resp = client.getTimeZone();
    try {
      RpcUtils.verifySuccess(resp.getStatus());
    } catch (IoTDBRPCException e) {
      throw new IoTDBSQLException(e.getMessage(), resp.getStatus());
    }
    return resp.getTimeZone();
  }

  public void setTimeZone(String zoneId) throws TException, IoTDBSQLException {
    TSSetTimeZoneReq req = new TSSetTimeZoneReq(zoneId);
    TSStatus resp = client.setTimeZone(req);
    try {
      RpcUtils.verifySuccess(resp);
    } catch (IoTDBRPCException e) {
      throw new IoTDBSQLException(e.getMessage(), resp);
    }
    this.zoneId = ZoneId.of(zoneId);
  }

  public ServerProperties getServerProperties() throws TException {
    return client.getProperties();
  }

  private void setProtocol(TSProtocolVersion protocol) {
    this.protocol = protocol;
  }

}
