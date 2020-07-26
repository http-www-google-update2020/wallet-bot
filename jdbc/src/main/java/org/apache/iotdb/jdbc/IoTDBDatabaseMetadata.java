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

import java.sql.*;
import java.util.List;
import java.util.Set;
import org.apache.iotdb.jdbc.IoTDBMetadataResultSet.MetadataType;
import org.apache.iotdb.rpc.IoTDBRPCException;
import org.apache.iotdb.rpc.RpcUtils;
import org.apache.iotdb.service.rpc.thrift.TSFetchMetadataReq;
import org.apache.iotdb.service.rpc.thrift.TSFetchMetadataResp;
import org.apache.iotdb.service.rpc.thrift.TSIService;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IoTDBDatabaseMetadata implements DatabaseMetaData {

  private IoTDBConnection connection;
  private TSIService.Iface client;
  private static final Logger logger = LoggerFactory
          .getLogger(IoTDBDatabaseMetadata.class);
  private static final String METHOD_NOT_SUPPORTED_STRING = "Method not supported";

  IoTDBDatabaseMetadata(IoTDBConnection connection, TSIService.Iface client) {
    this.connection = connection;
    this.client = client;
  }

  @Override
  public ResultSet getColumns(String catalog, String schemaPattern, String columnPattern,
      String devicePattern)
      throws SQLException {
    try {
      return getColumnsFunc(catalog, schemaPattern);
    } catch (TException e) {
      boolean flag = connection.reconnect();
      this.client = connection.client;
      if (flag) {
        try {
          return getColumnsFunc(catalog, schemaPattern);
        } catch (TException e2) {
          throw new SQLException(String.format("Fail to get columns catalog=%s, schemaPattern=%s,"
                  + " columnPattern=%s, devicePattern=%s after reconnecting."
                  + " please check server status",
              catalog, schemaPattern, columnPattern, devicePattern));
        }
      } else {
        throw new SQLException(String.format(
            "Fail to reconnect to server when getting columns catalog=%s, schemaPattern=%s,"
                + " columnPattern=%s, devicePattern=%s after reconnecting. "
                + "please check server status",
            catalog, schemaPattern, columnPattern, devicePattern));
      }
    }
  }

  private ResultSet getColumnsFunc(String catalog, String schemaPattern)
      throws TException, SQLException {
    TSFetchMetadataReq req;
    switch (catalog) {
      case Constant.CATALOG_COLUMN:
        req = new TSFetchMetadataReq(Constant.GLOBAL_COLUMNS_REQ);
        req.setColumnPath(schemaPattern);
        try {
          TSFetchMetadataResp resp = client.fetchMetadata(req);
          try {
            RpcUtils.verifySuccess(resp.getStatus());
          } catch (IoTDBRPCException e) {
            throw new IoTDBSQLException(e.getMessage(), resp.getStatus());
          }
          return new IoTDBMetadataResultSet(resp.getColumnsList(), MetadataType.COLUMN);
        } catch (TException e) {
          throw new TException("Connection error when fetching column metadata", e);
        }
      case Constant.CATALOG_DEVICES:
        req = new TSFetchMetadataReq(Constant.GLOBAL_SHOW_DEVICES_REQ);
        req.setColumnPath(schemaPattern);
        try {
          TSFetchMetadataResp resp = client.fetchMetadata(req);
          try {
            RpcUtils.verifySuccess(resp.getStatus());
          } catch (IoTDBRPCException e) {
            throw new IoTDBSQLException(e.getMessage(), resp.getStatus());
          }
          return new IoTDBMetadataResultSet(resp.getDevices(), MetadataType.DEVICES);
        } catch (TException e) {
          throw new TException("Connection error when fetching device metadata", e);
        }
      case Constant.CATALOG_CHILD_PATHS:
        req = new TSFetchMetadataReq(Constant.GLOBAL_SHOW_CHILD_PATHS_REQ);
        req.setColumnPath(schemaPattern);
        try {
            TSFetchMetadataResp resp = client.fetchMetadata(req);
          try {
            RpcUtils.verifySuccess(resp.getStatus());
          } catch (IoTDBRPCException e) {
            throw new IoTDBSQLException(e.getMessage(), resp.getStatus());
          }
          return new IoTDBMetadataResultSet(resp.getChildPaths(), MetadataType.CHILD_PATHS);
        } catch (TException e) {
          throw new TException("Connection error when fetching child path metadata", e);
        }
      case Constant.CATALOG_STORAGE_GROUP:
        req = new TSFetchMetadataReq(Constant.GLOBAL_SHOW_STORAGE_GROUP_REQ);
        try {
          TSFetchMetadataResp resp = client.fetchMetadata(req);
          try {
            RpcUtils.verifySuccess(resp.getStatus());
          } catch (IoTDBRPCException e) {
            throw new IoTDBSQLException(e.getMessage(), resp.getStatus());
          }
          Set<String> showStorageGroup = resp.getStorageGroups();
          return new IoTDBMetadataResultSet(showStorageGroup, MetadataType.STORAGE_GROUP);
        } catch (TException e) {
          throw new TException("Connection error when fetching storage group metadata", e);
        }
      case Constant.CATALOG_TIMESERIES:
        req = new TSFetchMetadataReq(Constant.GLOBAL_SHOW_TIMESERIES_REQ);
        req.setColumnPath(schemaPattern);
        try {
          TSFetchMetadataResp resp = client.fetchMetadata(req);
          try {
            RpcUtils.verifySuccess(resp.getStatus());
          } catch (IoTDBRPCException e) {
            throw new IoTDBSQLException(e.getMessage(), resp.getStatus());
          }
          List<List<String>> showTimeseriesList = resp.getTimeseriesList();
          return new IoTDBMetadataResultSet(showTimeseriesList, MetadataType.TIMESERIES);
        } catch (TException e) {
          throw new TException("Connection error when fetching timeseries metadata", e);
        }
      case Constant.COUNT_TIMESERIES:
        req = new TSFetchMetadataReq(Constant.GLOBAL_COUNT_TIMESERIES_REQ);
        req.setColumnPath(schemaPattern);
        try {
          TSFetchMetadataResp resp = client.fetchMetadata(req);
          try {
            RpcUtils.verifySuccess(resp.getStatus());
          } catch (IoTDBRPCException e) {
            throw new IoTDBSQLException(e.getMessage(), resp.getStatus());
          }
          return new IoTDBMetadataResultSet(resp.getTimeseriesNum(), MetadataType.COUNT_TIMESERIES);
        } catch (TException e) {
          throw new TException("Connection error when fetching timeseries metadata", e);
        }
      case Constant.CATALOG_VERSION:
        req = new TSFetchMetadataReq(Constant.GLOBAL_VERSION);
        req.setColumnPath(schemaPattern);
        try {
          TSFetchMetadataResp resp = client.fetchMetadata(req);
          try {
            RpcUtils.verifySuccess(resp.getStatus());
          } catch (IoTDBRPCException e) {
            throw new IoTDBSQLException(e.getMessage(), resp.getStatus());
          }
          return new IoTDBMetadataResultSet(resp.getVersion(), MetadataType.VERSION);
        } catch (TException e) {
          throw new TException("Connection error when fetching timeseries metadata", e);
        }
      default:
        throw new SQLException(catalog + " is not supported. Please refer to the user guide"
            + " for more details.");
    }
  }

  ResultSet getNodes(String catalog, String schemaPattern, String columnPattern,
      String devicePattern, int nodeLevel) throws SQLException {
    try {
      return getNodesFunc(catalog, schemaPattern, nodeLevel);
    } catch (TException e) {
      boolean flag = connection.reconnect();
      this.client = connection.client;
      if (flag) {
        try {
          return getNodesFunc(catalog, schemaPattern, nodeLevel);
        } catch (TException e2) {
          throw new SQLException(String.format("Fail to get columns catalog=%s, schemaPattern=%s,"
                  + " columnPattern=%s, devicePattern=%s, nodeLevel=%s after reconnecting."
                  + " please check server status",
              catalog, schemaPattern, columnPattern, devicePattern, nodeLevel));
        }
      } else {
        throw new SQLException(String.format(
            "Fail to reconnect to server when getting columns catalog=%s, schemaPattern=%s,"
                + " columnPattern=%s, devicePattern=%s, nodeLevel=%s after reconnecting. "
                + "please check server status",
            catalog, schemaPattern, columnPattern, devicePattern, nodeLevel));
      }
    }
  }

  private ResultSet getNodesFunc(String catalog, String schemaPattern, int nodeLevel) throws TException, SQLException {
    TSFetchMetadataReq req;
    switch (catalog) {
      case Constant.COUNT_NODES:
        req = new TSFetchMetadataReq(Constant.GLOBAL_COUNT_NODES_REQ);
        req.setNodeLevel(nodeLevel);
        req.setColumnPath(schemaPattern);
        try {
          TSFetchMetadataResp resp = client.fetchMetadata(req);
          try {
            RpcUtils.verifySuccess(resp.getStatus());
          } catch (IoTDBRPCException e) {
            throw new IoTDBSQLException(e.getMessage(), resp.getStatus());
          }
          return new IoTDBMetadataResultSet(resp.getNodesList().size(), IoTDBMetadataResultSet.MetadataType.COUNT_NODES);
        } catch (TException e) {
          throw new TException("Connection error when fetching node metadata", e);
        }
      case Constant.COUNT_NODE_TIMESERIES:
        req = new TSFetchMetadataReq(Constant.GLOBAL_COUNT_NODE_TIMESERIES_REQ);
        req.setNodeLevel(nodeLevel);
        req.setColumnPath(schemaPattern);
        try {
          TSFetchMetadataResp resp = client.fetchMetadata(req);
          try {
            RpcUtils.verifySuccess(resp.getStatus());
          } catch (IoTDBRPCException e) {
            throw new IoTDBSQLException(e.getMessage(), resp.getStatus());
          }
          return new IoTDBMetadataResultSet(resp.getNodeTimeseriesNum(), IoTDBMetadataResultSet.MetadataType.COUNT_NODE_TIMESERIES);
        } catch (TException e) {
          throw new TException("Connection error when fetching node metadata", e);
        }
      default:
        throw new SQLException(catalog + " is not supported. Please refer to the user guide"
            + " for more details.");
    }
  }

  @Override
  public boolean isWrapperFor(Class<?> arg0) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean allProceduresAreCallable() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean allTablesAreSelectable() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean autoCommitFailureClosesAllResultSets() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean dataDefinitionCausesTransactionCommit() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean dataDefinitionIgnoredInTransactions() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean deletesAreDetected(int arg0) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean generatedKeyAlwaysReturned() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getAttributes(String arg0, String arg1, String arg2, String arg3)
      throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getBestRowIdentifier(String arg0, String arg1, String arg2, int arg3,
      boolean arg4)
      throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getCatalogSeparator() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getCatalogTerm() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getCatalogs() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getClientInfoProperties() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getColumnPrivileges(String arg0, String arg1, String arg2,
      String arg3) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public Connection getConnection() {
    return connection;
  }

  @Override
  public ResultSet getCrossReference(String arg0, String arg1, String arg2, String arg3,
      String arg4, String arg5)
      throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public int getDatabaseMajorVersion() {
    return 0;
  }

  @Override
  public int getDatabaseMinorVersion() {
    return 0;
  }

  @Override
  public String getDatabaseProductName() {
    return Constant.GLOBAL_DB_NAME;
  }

  @Override
  public String getDatabaseProductVersion() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public int getDefaultTransactionIsolation() {
    return 0;
  }

  @Override
  public int getDriverMajorVersion() {
    return 0;
  }

  @Override
  public int getDriverMinorVersion() {
    return 0;
  }

  @Override
  public String getDriverName() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getDriverVersion() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getExportedKeys(String arg0, String arg1, String arg2) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getExtraNameCharacters() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getFunctionColumns(String arg0, String arg1, String arg2, String arg3)
      throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getFunctions(String arg0, String arg1, String arg2) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getIdentifierQuoteString() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getImportedKeys(String arg0, String arg1, String arg2) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getIndexInfo(String arg0, String arg1, String arg2, boolean arg3, boolean arg4)
      throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public int getJDBCMajorVersion() {
    return 0;
  }

  @Override
  public int getJDBCMinorVersion() {
    return 0;
  }

  @Override
  public int getMaxBinaryLiteralLength() {
    return 0;
  }

  @Override
  public int getMaxCatalogNameLength() {
    return 0;
  }

  @Override
  public int getMaxCharLiteralLength() {
    return 0;
  }

  @Override
  public int getMaxColumnNameLength() {
    return 0;
  }

  @Override
  public int getMaxColumnsInGroupBy() {
    return 0;
  }

  @Override
  public int getMaxColumnsInIndex() {
    return 0;
  }

  @Override
  public int getMaxColumnsInOrderBy() {
    return 0;
  }

  @Override
  public int getMaxColumnsInSelect() {
    return 0;
  }

  @Override
  public int getMaxColumnsInTable() {
    return 0;
  }

  @Override
  public int getMaxConnections() {
    return 0;
  }

  @Override
  public int getMaxCursorNameLength() {
    return 0;
  }

  @Override
  public int getMaxIndexLength() {
    return 0;
  }

  @Override
  public int getMaxProcedureNameLength() {
    return 0;
  }

  @Override
  public int getMaxRowSize() {
    return 0;
  }

  @Override
  public int getMaxSchemaNameLength() {
    return 0;
  }

  @Override
  public int getMaxStatementLength() {
    return 0;
  }

  @Override
  public int getMaxStatements() {
    return 0;
  }

  @Override
  public int getMaxTableNameLength() {
    return 0;
  }

  @Override
  public int getMaxTablesInSelect() {
    return 0;
  }

  @Override
  public int getMaxUserNameLength() {
    return 0;
  }

  @Override
  public String getNumericFunctions() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getPrimaryKeys(String arg0, String arg1, String arg2) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getProcedureColumns(String arg0, String arg1, String arg2, String arg3)
      throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getProcedureTerm() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getProcedures(String arg0, String arg1, String arg2) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getPseudoColumns(String catalog, String schemaPattern, String tableNamePattern,
      String columnNamePattern) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public int getResultSetHoldability() {
    return 0;
  }

  @Override
  public RowIdLifetime getRowIdLifetime() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getSQLKeywords() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public int getSQLStateType() {
    return 0;
  }

  @Override
  public String getSchemaTerm() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getSchemas() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getSearchStringEscape() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getStringFunctions() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern)
      throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern)
      throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getSystemFunctions() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern)
      throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getTableTypes() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern,
      String[] types)
      throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getTimeDateFunctions() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getTypeInfo() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern,
      int[] types)
      throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public String getURL() {
    // TODO: Return the URL for this DBMS or null if it cannot be generated
    return null;
  }

  @Override
  public String getUserName() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public ResultSet getVersionColumns(String catalog, String schema, String table)
      throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean insertsAreDetected(int type) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean isCatalogAtStart() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean isReadOnly() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean locatorsUpdateCopy() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean nullPlusNonNullIsNull() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean nullsAreSortedAtEnd() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean nullsAreSortedAtStart() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean nullsAreSortedHigh() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean nullsAreSortedLow() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean othersDeletesAreVisible(int type) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean othersInsertsAreVisible(int type) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean othersUpdatesAreVisible(int type) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean ownDeletesAreVisible(int type) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean ownInsertsAreVisible(int type) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean ownUpdatesAreVisible(int type) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean storesLowerCaseIdentifiers() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean storesLowerCaseQuotedIdentifiers() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean storesMixedCaseIdentifiers() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean storesMixedCaseQuotedIdentifiers() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean storesUpperCaseIdentifiers() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean storesUpperCaseQuotedIdentifiers() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsANSI92EntryLevelSQL() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsANSI92FullSQL() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsANSI92IntermediateSQL() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsAlterTableWithAddColumn() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsAlterTableWithDropColumn() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsBatchUpdates() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsCatalogsInDataManipulation() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsCatalogsInIndexDefinitions() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsCatalogsInProcedureCalls() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsCatalogsInTableDefinitions() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsColumnAliasing() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsConvert() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsConvert(int fromType, int toType) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsCoreSQLGrammar() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsCorrelatedSubqueries() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsDataDefinitionAndDataManipulationTransactions() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsDataManipulationTransactionsOnly() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsDifferentTableCorrelationNames() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsExpressionsInOrderBy() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsExtendedSQLGrammar() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsFullOuterJoins() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsGetGeneratedKeys() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsGroupBy() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsGroupByBeyondSelect() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsGroupByUnrelated() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsIntegrityEnhancementFacility() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsLikeEscapeClause() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsLimitedOuterJoins() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsMinimumSQLGrammar() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsMixedCaseIdentifiers() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsMixedCaseQuotedIdentifiers() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsMultipleOpenResults() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsMultipleResultSets() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsMultipleTransactions() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsNamedParameters() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsNonNullableColumns() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsOpenCursorsAcrossCommit() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsOpenCursorsAcrossRollback() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsOpenStatementsAcrossCommit() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsOpenStatementsAcrossRollback() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsOrderByUnrelated() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsOuterJoins() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsPositionedDelete() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsPositionedUpdate() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsResultSetConcurrency(int type, int concurrency) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsResultSetHoldability(int holdability) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsResultSetType(int type) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSavepoints() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSchemasInDataManipulation() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSchemasInIndexDefinitions() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSchemasInPrivilegeDefinitions() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSchemasInProcedureCalls() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSchemasInTableDefinitions() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSelectForUpdate() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsStatementPooling() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsStoredProcedures() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSubqueriesInComparisons() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSubqueriesInExists() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSubqueriesInIns() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsSubqueriesInQuantifieds() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsTableCorrelationNames() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsTransactionIsolationLevel(int level) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsTransactions() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsUnion() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean supportsUnionAll() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean updatesAreDetected(int type) throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean usesLocalFilePerTable() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  @Override
  public boolean usesLocalFiles() throws SQLException {
    throw new SQLException(METHOD_NOT_SUPPORTED_STRING);
  }

  /**
   * @deprecated
   * recommend using getMetadataInJson() instead of toString()
   */
  @Deprecated
  @Override
  public String toString() {
    try {
      return getMetadataInJsonFunc();
    } catch (IoTDBSQLException e) {
      logger.error("Failed to fetch metadata in json because: ", e);
    } catch (TException e) {
      boolean flag = connection.reconnect();
      this.client = connection.client;
      if (flag) {
        try {
          return getMetadataInJsonFunc();
        } catch (TException e2) {
          logger.error("Fail to get all timeseries " + "info after reconnecting."
                  + " please check server status", e2);
        } catch (IoTDBSQLException e1) {
          // ignored
        }
      } else {
        logger.error("Fail to reconnect to server "
                + "when getting all timeseries info. please check server status");
      }
    }
    return null;
  }

  /*
   * recommend using getMetadataInJson() instead of toString()
   */
  public String getMetadataInJson() throws SQLException {
    try {
      return getMetadataInJsonFunc();
    } catch (TException e) {
      boolean flag = connection.reconnect();
      this.client = connection.client;
      if (flag) {
        try {
          return getMetadataInJsonFunc();
        } catch (TException e2) {
          throw new SQLException("Failed to fetch all metadata in json "
              + "after reconnecting. Please check the server status.");
        }
      } else {
        throw new SQLException("Failed to reconnect to the server "
            + "when fetching all metadata in json. Please check the server status.");
      }
    }
  }

  private String getMetadataInJsonFunc() throws TException, IoTDBSQLException {
    TSFetchMetadataReq req = new TSFetchMetadataReq("METADATA_IN_JSON");
    TSFetchMetadataResp resp;
    resp = client.fetchMetadata(req);
    try {
      RpcUtils.verifySuccess(resp.getStatus());
    } catch (IoTDBRPCException e) {
      throw new IoTDBSQLException(e.getMessage(), resp.getStatus());
    }
    return resp.getMetadataInJson();
  }
}
