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
package org.apache.iotdb.db.qp.strategy;

import static org.apache.iotdb.db.qp.constant.SQLConstant.LESSTHAN;
import static org.apache.iotdb.db.qp.constant.SQLConstant.LESSTHANOREQUALTO;
import static org.apache.iotdb.db.sql.parse.TqlParser.OPERATOR_AND;
import static org.apache.iotdb.db.sql.parse.TqlParser.OPERATOR_EQ;
import static org.apache.iotdb.db.sql.parse.TqlParser.OPERATOR_GT;
import static org.apache.iotdb.db.sql.parse.TqlParser.OPERATOR_GTE;
import static org.apache.iotdb.db.sql.parse.TqlParser.OPERATOR_LT;
import static org.apache.iotdb.db.sql.parse.TqlParser.OPERATOR_LTE;
import static org.apache.iotdb.db.sql.parse.TqlParser.OPERATOR_NEQ;
import static org.apache.iotdb.db.sql.parse.TqlParser.OPERATOR_NOT;
import static org.apache.iotdb.db.sql.parse.TqlParser.OPERATOR_OR;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_ADD;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_AGGREGATE;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_ALL;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_ALTER;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_CREATE;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_DATETIME;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_DELETE;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_DROP;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_FILL;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_FROM;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_GRANT;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_GRANT_WATERMARK_EMBEDDING;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_GROUPBY;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_GROUPBY_DEVICE;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_INSERT;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_LABEL;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_LIMIT;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_LINEAR;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_LINK;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_LIST;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_LOAD;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_PATH;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_PREVIOUS;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_PRIVILEGES;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_PROPERTY;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_QUERY;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_REVOKE;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_REVOKE_WATERMARK_EMBEDDING;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_ROLE;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_ROOT;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_SELECT;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_SET;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_SHOW;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_SLIMIT;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_SOFFSET;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_STORAGEGROUP;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_TIMESERIES;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_TTL;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_UNLINK;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_UNSET;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_UPDATE;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_USER;
import static org.apache.iotdb.db.sql.parse.TqlParser.TOK_WHERE;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.antlr.runtime.Token;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.exception.metadata.MetadataException;
import org.apache.iotdb.db.exception.query.IllegalASTFormatException;
import org.apache.iotdb.db.exception.query.LogicalOperatorException;
import org.apache.iotdb.db.exception.query.QueryProcessException;
import org.apache.iotdb.db.qp.constant.DatetimeUtils;
import org.apache.iotdb.db.qp.constant.SQLConstant;
import org.apache.iotdb.db.qp.constant.TqlParserConstant;
import org.apache.iotdb.db.qp.logical.RootOperator;
import org.apache.iotdb.db.qp.logical.crud.BasicFunctionOperator;
import org.apache.iotdb.db.qp.logical.crud.DeleteDataOperator;
import org.apache.iotdb.db.qp.logical.crud.FilterOperator;
import org.apache.iotdb.db.qp.logical.crud.FromOperator;
import org.apache.iotdb.db.qp.logical.crud.InsertOperator;
import org.apache.iotdb.db.qp.logical.crud.QueryOperator;
import org.apache.iotdb.db.qp.logical.crud.SFWOperator;
import org.apache.iotdb.db.qp.logical.crud.SelectOperator;
import org.apache.iotdb.db.qp.logical.crud.UpdateOperator;
import org.apache.iotdb.db.qp.logical.sys.AuthorOperator;
import org.apache.iotdb.db.qp.logical.sys.CreateTimeSeriesOperator;
import org.apache.iotdb.db.qp.logical.sys.DataAuthOperator;
import org.apache.iotdb.db.qp.logical.sys.DeleteStorageGroupOperator;
import org.apache.iotdb.db.qp.logical.sys.DeleteTimeSeriesOperator;
import org.apache.iotdb.db.qp.logical.sys.LoadDataOperator;
import org.apache.iotdb.db.qp.logical.sys.PropertyOperator;
import org.apache.iotdb.db.qp.logical.sys.SetStorageGroupOperator;
import org.apache.iotdb.db.qp.logical.sys.SetTTLOperator;
import org.apache.iotdb.db.qp.logical.sys.ShowTTLOperator;
import org.apache.iotdb.db.query.fill.IFill;
import org.apache.iotdb.db.query.fill.LinearFill;
import org.apache.iotdb.db.query.fill.PreviousFill;
import org.apache.iotdb.db.sql.parse.AstNode;
import org.apache.iotdb.db.sql.parse.Node;
import org.apache.iotdb.db.sql.parse.TqlParser;
import org.apache.iotdb.tsfile.common.conf.TSFileDescriptor;
import org.apache.iotdb.tsfile.common.constant.TsFileConstant;
import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.read.common.Path;
import org.apache.iotdb.tsfile.utils.Pair;
import org.apache.iotdb.tsfile.utils.StringContainer;

/**
 * This class receives an AstNode and transform it to an operator which is a logical plan.
 */
public class LogicalGenerator {

  private static final String INCORRECT_AUTHOR_COMMAND = "grant author";
  private static final String UPDATE_PASSWORD_COMMAND = "update password";
  private static final String DATA_LOAD_COMMAND = "data load";

  private RootOperator initializedOperator = null;
  private ZoneId zoneId;

  public LogicalGenerator(ZoneId zoneId) {
    this.zoneId = zoneId;
  }

  public RootOperator getLogicalPlan(AstNode astNode)
      throws QueryProcessException, MetadataException {
    analyze(astNode);
    return initializedOperator;
  }

  /**
   * input an astNode parsing by {@code antlr} and analyze it.
   *
   * @throws IllegalASTFormatException exception in query process
   */
  private void analyze(AstNode astNode)
      throws QueryProcessException, MetadataException {
    Token token = astNode.getToken();
    if (token == null) {
      throw new IllegalASTFormatException("given token is null");
    }
    int tokenIntType = token.getType();
    switch (tokenIntType) {
      case TOK_INSERT:
        analyzeInsert(astNode);
        return;
      case TOK_SELECT:
        analyzeSelectedPath(astNode);
        return;
      case TOK_FROM:
        analyzeFrom(astNode);
        return;
      case TOK_WHERE:
        analyzeWhere(astNode);
        return;
      case TOK_GROUPBY:
        analyzeGroupBy(astNode);
        return;
      case TOK_FILL:
        analyzeFill(astNode);
        return;
      case TOK_ALTER:
        analyzeAuthorAlter(astNode);
        return;
      case TOK_UPDATE:
        analyzeUpdate(astNode);
        return;
      case TOK_DELETE:
        switch (astNode.getChild(0).getType()) {
          case TOK_TIMESERIES:
            analyzeMetadataDelete(astNode);
            break;
          case TOK_LABEL:
            analyzePropertyDeleteLabel(astNode);
            break;
          case TOK_STORAGEGROUP:
            analyzeMetaDataDeleteFileLevel(astNode);
            break;
          default:
            analyzeDelete(astNode);
            break;
        }
        return;
      case TOK_SET:
        analyzeMetadataSetFileLevel(astNode);
        return;
      case TOK_ADD:
        analyzePropertyAddLabel(astNode);
        return;
      case TOK_LINK:
        analyzePropertyLink(astNode);
        return;
      case TOK_UNLINK:
        analyzePropertyUnLink(astNode);
        return;
      case TOK_CREATE:
        switch (astNode.getChild(0).getType()) {
          case TOK_USER:
          case TOK_ROLE:
            analyzeAuthorCreate(astNode);
            break;
          case TOK_PATH:
            analyzeMetadataCreate(astNode);
            break;
          case TOK_PROPERTY:
            analyzePropertyCreate(astNode);
            break;
          default:
            break;
        }
        return;
      case TOK_DROP:
        switch (astNode.getChild(0).getType()) {
          case TOK_USER:
          case TOK_ROLE:
            analyzeAuthorDrop(astNode);
            break;
          default:
            break;
        }
        return;
      case TOK_GRANT:
        analyzeAuthorGrant(astNode);
        return;
      case TOK_GRANT_WATERMARK_EMBEDDING:
        analyzeWatermarkEmbedding(astNode, SQLConstant.TOK_GRANT_WATERMARK_EMBEDDING);
        return;
      case TOK_REVOKE_WATERMARK_EMBEDDING:
        analyzeWatermarkEmbedding(astNode, SQLConstant.TOK_REVOKE_WATERMARK_EMBEDDING);
        return;
      case TOK_REVOKE:
        analyzeAuthorRevoke(astNode);
        return;
      case TOK_LOAD:
        analyzeDataLoad(astNode);
        return;
      case TOK_QUERY:
        // for TqlParser.TOK_QUERY might appear in both query and insert
        // command. Thus, do
        // nothing and call analyze() with children nodes recursively.
        initializedOperator = new QueryOperator(SQLConstant.TOK_QUERY);
        break;
      case TOK_LIST:
        analyzeList(astNode);
        return;
      case TOK_LIMIT:
        analyzeLimit(astNode);
        return;
      case TOK_SLIMIT:
        analyzeSlimit(astNode);
        return;
      case TOK_SOFFSET:
        analyzeSoffset(astNode);
        return;
      case TOK_TTL:
        analyzeTTL(astNode);
        return;
      case TOK_GROUPBY_DEVICE:
        ((QueryOperator) initializedOperator).setGroupByDevice(true);
        return;
      default:
        throw new QueryProcessException("Not supported TqlParser type " + token.getText());
    }
    for (Node node : astNode.getChildren()) {
      analyze((AstNode) node);
    }
  }

  private void analyzeTTL(AstNode astNode) throws QueryProcessException {
    int tokenType = astNode.getChild(0).getToken().getType();
    switch (tokenType) {
      case TOK_SET:
        analyzeSetTTL(astNode);
        break;
      case TOK_UNSET:
        analyzeUnsetTTL(astNode);
        break;
      case TOK_SHOW:
        analyzeShowTTL(astNode);
        break;
      default:
        throw new QueryProcessException("Not supported TSParser type in TTL:" + tokenType);
    }
  }

  private void analyzeSetTTL(AstNode astNode) {
    String path = parsePath(astNode.getChild(1)).getFullPath();
    long dataTTL;
    dataTTL = Long.parseLong(astNode.getChild(2).getText());
    SetTTLOperator operator = new SetTTLOperator(SQLConstant.TOK_SET);
    initializedOperator = operator;
    operator.setStorageGroup(path);
    operator.setDataTTL(dataTTL);
  }

  private void analyzeUnsetTTL(AstNode astNode) {
    String path = parsePath(astNode.getChild(1)).getFullPath();
    SetTTLOperator operator = new SetTTLOperator(SQLConstant.TOK_UNSET);
    initializedOperator = operator;
    operator.setStorageGroup(path);
  }

  private void analyzeShowTTL(AstNode astNode) {
    List<String> storageGroups = new ArrayList<>();
    for (int i = 1; i < astNode.getChildCount(); i++) {
      Path path = parsePath(astNode.getChild(i));
      storageGroups.add(path.getFullPath());
    }
    initializedOperator = new ShowTTLOperator(storageGroups);
  }

  private void analyzeSlimit(AstNode astNode) throws LogicalOperatorException {
    AstNode unit = astNode.getChild(0);
    int seriesLimit;
    try {
      seriesLimit = Integer.parseInt(unit.getText().trim());
    } catch (NumberFormatException e) {
      throw new LogicalOperatorException("SLIMIT <SN>: SN should be Int32.");
    }
    if (seriesLimit <= 0) {
      // seriesLimit is ensured to be a non negative integer after the lexical examination,
      // and seriesLimit is further required to be a positive integer here.
      throw new LogicalOperatorException(
          "SLIMIT <SN>: SN must be a positive integer and can not be zero.");
    }
    ((QueryOperator) initializedOperator).setSeriesLimit(seriesLimit);
  }

  private void analyzeSoffset(AstNode astNode) throws LogicalOperatorException {
    AstNode unit = astNode.getChild(0);
    try {
      // NOTE seriesOffset is ensured to be a non negative integer after the lexical examination.
      ((QueryOperator) initializedOperator)
          .setSeriesOffset(Integer.parseInt(unit.getText().trim()));
    } catch (NumberFormatException e) {
      throw new LogicalOperatorException("SOFFSET <SOFFSETValue>: SOFFSETValue should be Int32.");
    }
  }

  private void analyzeLimit(AstNode astNode) throws LogicalOperatorException {
    AstNode unit = astNode.getChild(0);
    int rowsLimit;
    try {
      rowsLimit = Integer.parseInt(unit.getText().trim());
    } catch (NumberFormatException e) {
      throw new LogicalOperatorException("LIMIT <N>: N should be Int32.");
    }
    if (rowsLimit <= 0) {
      // rowsLimit is ensured to be a non negative integer after the lexical examination,
      // and rowsLimit is further required to be a positive integer here.
      throw new LogicalOperatorException(
          "LIMIT <N>: N must be a positive integer and can not be zero.");
    }
  }

  private void analyzeList(AstNode astNode) {
    int childrenSize = astNode.getChildren().size();
    if (childrenSize == 1) {
      // list users or roles
      analyzeSimpleList(astNode);
    } else if (childrenSize == 3) {
      // list privileges of user/role, roles of a user, users of a role
      analyzeComplexList(astNode);
    }
  }

  private void analyzeSimpleList(AstNode astNode) {
    int tokenType = astNode.getChild(0).getType();
    if (tokenType == TOK_USER) {
      // list all users
      initializedOperator = new AuthorOperator(SQLConstant.TOK_LIST,
          AuthorOperator.AuthorType.LIST_USER);
    } else if (tokenType == TqlParser.TOK_ROLE) {
      // list all roles
      initializedOperator = new AuthorOperator(SQLConstant.TOK_LIST,
          AuthorOperator.AuthorType.LIST_ROLE);
    }
  }

  private void analyzeComplexList(AstNode astNode) {
    int tokenType = astNode.getChild(1).getType();
    if (tokenType == TOK_USER) {
      // list user privileges on seriesPath
      AuthorOperator operator = new AuthorOperator(SQLConstant.TOK_LIST,
          AuthorOperator.AuthorType.LIST_USER_PRIVILEGE);
      initializedOperator = operator;
      operator.setUserName(astNode.getChild(1).getChild(0).getText());
      operator.setNodeNameList(parsePath(astNode.getChild(2)));
    } else if (tokenType == TOK_ROLE) {
      // list role privileges on seriesPath
      AuthorOperator operator = new AuthorOperator(SQLConstant.TOK_LIST,
          AuthorOperator.AuthorType.LIST_ROLE_PRIVILEGE);
      initializedOperator = operator;
      operator.setRoleName(astNode.getChild(1).getChild(0).getText());
      operator.setNodeNameList(parsePath(astNode.getChild(2)));
    } else if (tokenType == TOK_ALL) {
      tokenType = astNode.getChild(0).getType();
      if (tokenType == TOK_PRIVILEGES) {
        tokenType = astNode.getChild(2).getType();
        if (tokenType == TOK_USER) {
          // list all privileges of a user
          AuthorOperator operator = new AuthorOperator(SQLConstant.TOK_LIST,
              AuthorOperator.AuthorType.LIST_USER_PRIVILEGE);
          initializedOperator = operator;
          operator.setUserName(astNode.getChild(2).getChild(0).getText());
        } else if (tokenType == TOK_ROLE) {
          // list all privileges of a role
          AuthorOperator operator = new AuthorOperator(SQLConstant.TOK_LIST,
              AuthorOperator.AuthorType.LIST_ROLE_PRIVILEGE);
          initializedOperator = operator;
          operator.setRoleName(astNode.getChild(2).getChild(0).getText());
        }
      } else {
        tokenType = astNode.getChild(2).getType();
        if (tokenType == TOK_USER) {
          // list all roles of a user
          AuthorOperator operator = new AuthorOperator(SQLConstant.TOK_LIST,
              AuthorOperator.AuthorType.LIST_USER_ROLES);
          initializedOperator = operator;
          operator.setUserName(astNode.getChild(2).getChild(0).getText());
        } else if (tokenType == TOK_ROLE) {
          // list all users of a role
          AuthorOperator operator = new AuthorOperator(SQLConstant.TOK_LIST,
              AuthorOperator.AuthorType.LIST_ROLE_USERS);
          initializedOperator = operator;
          operator.setRoleName(astNode.getChild(2).getChild(0).getText());
        }
      }
    }
  }


  private void analyzePropertyCreate(AstNode astNode) {
    PropertyOperator propertyOperator = new PropertyOperator(SQLConstant.TOK_PROPERTY_CREATE,
        PropertyOperator.PropertyType.ADD_TREE);
    propertyOperator.setPropertyPath(new Path(astNode.getChild(0).getChild(0).getText()));
    initializedOperator = propertyOperator;
  }

  private void analyzePropertyAddLabel(AstNode astNode) {
    PropertyOperator propertyOperator = new PropertyOperator(SQLConstant.TOK_PROPERTY_ADD_LABEL,
        PropertyOperator.PropertyType.ADD_PROPERTY_LABEL);
    Path propertyLabel = parsePropertyAndLabel(astNode, 0);
    propertyOperator.setPropertyPath(propertyLabel);
    initializedOperator = propertyOperator;
  }

  private void analyzePropertyDeleteLabel(AstNode astNode) {
    PropertyOperator propertyOperator = new PropertyOperator(SQLConstant.TOK_PROPERTY_DELETE_LABEL,
        PropertyOperator.PropertyType.DELETE_PROPERTY_LABEL);
    Path propertyLabel = parsePropertyAndLabel(astNode, 0);
    propertyOperator.setPropertyPath(propertyLabel);
    initializedOperator = propertyOperator;
  }

  private Path parsePropertyAndLabel(AstNode astNode, int startIndex) {
    String label = astNode.getChild(startIndex).getChild(0).getText();
    String property = astNode.getChild(startIndex + 1).getChild(0).getText();
    return new Path(new String[]{property, label});
  }

  private void analyzePropertyLink(AstNode astNode) {
    PropertyOperator propertyOperator = new PropertyOperator(SQLConstant.TOK_PROPERTY_LINK,
        PropertyOperator.PropertyType.ADD_PROPERTY_TO_METADATA);
    Path metaPath = parsePath(astNode.getChild(0));
    propertyOperator.setMetadataPath(metaPath);
    Path propertyLabel = parsePropertyAndLabel(astNode, 1);
    propertyOperator.setPropertyPath(propertyLabel);
    initializedOperator = propertyOperator;
  }

  private void analyzePropertyUnLink(AstNode astNode) {
    PropertyOperator propertyOperator = new PropertyOperator(SQLConstant.TOK_PROPERTY_UNLINK,
        PropertyOperator.PropertyType.DEL_PROPERTY_FROM_METADATA);
    Path metaPath = parsePath(astNode.getChild(0));
    propertyOperator.setMetadataPath(metaPath);
    Path propertyLabel = parsePropertyAndLabel(astNode, 1);
    propertyOperator.setPropertyPath(propertyLabel);
    initializedOperator = propertyOperator;
  }

  private void analyzeMetadataCreate(AstNode astNode) throws MetadataException {
    Path series = parsePath(astNode.getChild(0));
    AstNode paramNode = astNode.getChild(1);
    String dataType = paramNode.getChild(0).getChild(0).getText().toUpperCase();
    String encodingType = paramNode.getChild(1).getChild(0).getText().toUpperCase();
    String compressor;
    int offset = 2;
    if (paramNode.getChildren().size() > offset
        && paramNode.getChild(offset).getToken().getText().equals("TOK_COMPRESSOR")) {
      compressor = cascadeChildrenText(paramNode.getChild(offset).getChild(0)).toUpperCase();
      offset++;
    } else {
      compressor = TSFileDescriptor.getInstance().getConfig().getCompressor().toUpperCase();
    }
    checkMetadataArgs(dataType, encodingType, compressor);
    Map<String, String> props = new HashMap<>(paramNode.getChildCount() - offset + 1, 1);
    while (offset < paramNode.getChildCount()) {
      AstNode node = paramNode.getChild(offset++);
      props.put(node.getChild(0).getText().toLowerCase(), cascadeChildrenText(node.getChild(1)));
    }
    CreateTimeSeriesOperator createTimeSeriesOperator = new CreateTimeSeriesOperator(
        SQLConstant.TOK_METADATA_CREATE);
    createTimeSeriesOperator.setPath(series);
    createTimeSeriesOperator.setDataType(TSDataType.valueOf(dataType));
    createTimeSeriesOperator.setEncoding(TSEncoding.valueOf(encodingType));
    createTimeSeriesOperator.setProps(props);
    createTimeSeriesOperator.setCompressor(CompressionType.valueOf(compressor));
    initializedOperator = createTimeSeriesOperator;
  }

  private void analyzeMetadataDelete(AstNode astNode) {
    List<Path> deletePaths = new ArrayList<>();
    for (int i = 0; i < astNode.getChild(0).getChildCount(); i++) {
      deletePaths.add(parsePath(astNode.getChild(0).getChild(i)));
    }
    DeleteTimeSeriesOperator deleteTimeSeriesOperator = new DeleteTimeSeriesOperator(
        SQLConstant.TOK_METADATA_DELETE);
    deleteTimeSeriesOperator.setDeletePathList(deletePaths);
    initializedOperator = deleteTimeSeriesOperator;
  }

  private void analyzeMetadataSetFileLevel(AstNode astNode) {
    SetStorageGroupOperator setStorageGroupOperator = new SetStorageGroupOperator(
        SQLConstant.TOK_METADATA_SET_FILE_LEVEL);
    Path path = parsePath(astNode.getChild(0).getChild(0));
    setStorageGroupOperator.setPath(path);
    initializedOperator = setStorageGroupOperator;
  }

  private void analyzeMetaDataDeleteFileLevel(AstNode astNode) {
    List<Path> deletePaths = new ArrayList<>();
    for (int i = 0; i < astNode.getChild(0).getChildCount(); i++) {
      deletePaths.add(parsePath(astNode.getChild(0).getChild(i)));
    }
    DeleteStorageGroupOperator deleteStorageGroupOperator = new DeleteStorageGroupOperator(
        SQLConstant.TOK_METADATA_DELETE_FILE_LEVEL);
    deleteStorageGroupOperator.setDeletePathList(deletePaths);
    initializedOperator = deleteStorageGroupOperator;
  }

  private void analyzeInsert(AstNode astNode) throws QueryProcessException {
    InsertOperator insertOp = new InsertOperator(SQLConstant.TOK_INSERT);
    initializedOperator = insertOp;
    analyzeSelectedPath(astNode.getChild(0));
    long timestamp;
    try {
      AstNode timeValue = astNode.getChild(2).getChild(0);
      if (timeValue.getType() == TOK_DATETIME) {
        timestamp = parseTimeFormat(cascadeChildrenText(timeValue));
      } else {
        timestamp = Long.parseLong(astNode.getChild(2).getChild(0).getText());
      }
    } catch (NumberFormatException e) {
      throw new LogicalOperatorException(
          "Need a long value in insert clause, but given:" + astNode.getChild(2).getChild(0)
              .getText());
    }
    if (astNode.getChild(1).getChildCount() != astNode.getChild(2).getChildCount()) {
      throw new QueryProcessException(
          "number of measurement is NOT EQUAL TO the number of values");
    }
    insertOp.setTime(timestamp);
    String[] measurementList = new String[astNode.getChild(1).getChildCount() - 1];
    for (int i = 1; i < astNode.getChild(1).getChildCount(); i++) {
      String measurement = astNode.getChild(1).getChild(i).getText();
      if (measurement.contains("\"") || measurement.contains("\'")) {
        measurement = measurement.substring(1, measurement.length() - 1);
      }
      measurementList[i - 1] = measurement;
    }
    insertOp.setMeasurementList(measurementList);

    AstNode valueKey = astNode.getChild(2);
    String[] valueList = new String[valueKey.getChildCount() - 1];
    for (int i = 1; i < valueKey.getChildCount(); i++) {
      AstNode node = valueKey.getChild(i);
      valueList[i - 1] = cascadeChildrenText(node);
    }
    insertOp.setValueList(valueList);
  }

  private void analyzeUpdate(AstNode astNode) throws LogicalOperatorException {
    if (astNode.getChildCount() > 3) {
      throw new LogicalOperatorException("UPDATE clause doesn't support multi-update yet.");
    }
    UpdateOperator updateOp = new UpdateOperator(SQLConstant.TOK_UPDATE);
    initializedOperator = updateOp;
    FromOperator fromOp = new FromOperator(TOK_FROM);
    fromOp.addPrefixTablePath(parsePath(astNode.getChild(0)));
    updateOp.setFromOperator(fromOp);
    SelectOperator selectOp = new SelectOperator(TOK_SELECT);
    selectOp.addSelectPath(parsePath(astNode.getChild(1).getChild(0)));
    updateOp.setSelectOperator(selectOp);
    updateOp.setValue(astNode.getChild(1).getChild(1).getText());
    analyzeWhere(astNode.getChild(2));
  }

  private void analyzeDelete(AstNode astNode) throws LogicalOperatorException {
    initializedOperator = new DeleteDataOperator(SQLConstant.TOK_DELETE);
    SelectOperator selectOp = new SelectOperator(TqlParser.TOK_SELECT);
    int selChildCount = astNode.getChildCount() - 1;
    for (int i = 0; i < selChildCount; i++) {
      AstNode child = astNode.getChild(i);
      if (child.getType() != TOK_PATH) {
        throw new LogicalOperatorException(
            "children FROM clause except last one must all be seriesPath like root.a.b, actual:"
                + child.getText());
      }
      Path tablePath = parsePath(child);
      selectOp.addSelectPath(tablePath);
    }
    ((SFWOperator) initializedOperator).setSelectOperator(selectOp);
    analyzeWhere(astNode.getChild(selChildCount));
    long deleteTime = parseDeleteTimeFilter((DeleteDataOperator) initializedOperator);
    ((DeleteDataOperator) initializedOperator).setTime(deleteTime);
  }

  /**
   * for delete command, time should only have an end time.
   *
   * @param operator delete logical plan
   */
  private long parseDeleteTimeFilter(DeleteDataOperator operator) throws LogicalOperatorException {
    FilterOperator filterOperator = operator.getFilterOperator();
    if (!(filterOperator.isLeaf())) {
      throw new LogicalOperatorException(
          "For delete command, where clause must be like : time < XXX or time <= XXX");
    }
    if (filterOperator.getTokenIntType() != LESSTHAN
        && filterOperator.getTokenIntType() != LESSTHANOREQUALTO) {
      throw new LogicalOperatorException(
          "For delete command, where clause must be like : time < XXX or time <= XXX");
    }
    long time = Long.parseLong(((BasicFunctionOperator) filterOperator).getValue());
    if (filterOperator.getTokenIntType() == LESSTHAN) {
      time = time - 1;
    }
    return time;
  }

  private void analyzeFrom(AstNode node) throws LogicalOperatorException {
    int selChildCount = node.getChildCount();
    FromOperator from = new FromOperator(SQLConstant.TOK_FROM);
    for (int i = 0; i < selChildCount; i++) {
      AstNode child = node.getChild(i);
      if (child.getType() != TOK_PATH) {
        throw new LogicalOperatorException(
            "children FROM clause must all be seriesPath like root.a.b, actual:" + child.getText());
      }
      Path tablePath = parsePath(child);
      from.addPrefixTablePath(tablePath);
    }
    ((SFWOperator) initializedOperator).setFromOperator(from);
  }

  private void analyzeSelectedPath(AstNode astNode) throws LogicalOperatorException {
    int tokenIntType = astNode.getType();
    SelectOperator selectOp = new SelectOperator(TOK_SELECT);
    if (tokenIntType == TOK_SELECT) {
      int selChildCount = astNode.getChildCount();
      for (int i = 0; i < selChildCount; i++) {
        AstNode child = astNode.getChild(i);
        if (child.getChild(0).getType() == TOK_AGGREGATE) {
          AstNode cluster = child.getChild(0);
          AstNode pathChild = cluster.getChild(0);
          Path selectPath = parsePath(pathChild);
          String aggregation = cluster.getChild(1).getText();
          selectOp.addClusterPath(selectPath, aggregation);
        } else {
          Path selectPath = parsePath(child);
          selectOp.addSelectPath(selectPath);
        }
      }
    } else if (tokenIntType == TOK_PATH) {
      Path selectPath = parsePath(astNode);
      selectOp.addSelectPath(selectPath);
    } else {
      throw new LogicalOperatorException(
          "Children SELECT clause must all be seriesPath like root.a.b, actual:" + astNode.dump());
    }
    ((SFWOperator) initializedOperator).setSelectOperator(selectOp);
  }

  private void analyzeWhere(AstNode astNode) throws LogicalOperatorException {
    if (astNode.getType() != TOK_WHERE) {
      throw new LogicalOperatorException(
          "Given node is not WHERE! please check whether SQL statement is correct.");
    }
    if (astNode.getChildCount() != 1) {
      throw new LogicalOperatorException("Where clause has" + astNode.getChildCount()
          + " child, please check whether SQL grammar is correct.");
    }
    FilterOperator whereOp = new FilterOperator(SQLConstant.TOK_WHERE);
    AstNode child = astNode.getChild(0);
    analyzeWhere(child, child.getType(), whereOp);
    ((SFWOperator) initializedOperator).setFilterOperator(whereOp.getChildren().get(0));
  }

  private void analyzeWhere(AstNode ast, int tokenIntType, FilterOperator filterOp)
      throws LogicalOperatorException {
    int childCount = ast.getChildCount();
    switch (tokenIntType) {
      case OPERATOR_NOT:
        if (childCount != 1) {
          throw new LogicalOperatorException(
              "Parsing where clause failed: NOT operator requires one param");
        }
        FilterOperator notOp = new FilterOperator(SQLConstant.KW_NOT);
        filterOp.addChildOperator(notOp);
        AstNode childAstNode = ast.getChild(0);
        int childNodeTokenType = childAstNode.getToken().getType();
        analyzeWhere(childAstNode, childNodeTokenType, notOp);
        break;
      case OPERATOR_AND:
      case OPERATOR_OR:
        if (childCount != 2) {
          throw new LogicalOperatorException(
              "Parsing where clause failed! node has " + childCount + " parameter.");
        }
        FilterOperator binaryOp = new FilterOperator(
            TqlParserConstant.getTSTokenIntType(tokenIntType));
        filterOp.addChildOperator(binaryOp);
        for (int i = 0; i < childCount; i++) {
          childAstNode = ast.getChild(i);
          childNodeTokenType = childAstNode.getToken().getType();
          analyzeWhere(childAstNode, childNodeTokenType, binaryOp);
        }
        break;
      case OPERATOR_LT:
      case OPERATOR_LTE:
      case OPERATOR_EQ:
//      case TqlParser.EQUAL_NS:
      case OPERATOR_GT:
      case OPERATOR_GTE:
      case OPERATOR_NEQ:
        Pair<Path, String> pair = parseLeafNode(ast);
        BasicFunctionOperator basic = new BasicFunctionOperator(
            TqlParserConstant.getTSTokenIntType(tokenIntType),
            pair.left, pair.right);
        filterOp.addChildOperator(basic);
        break;
      default:
        throw new LogicalOperatorException(String.valueOf(tokenIntType), "");
    }
  }

  private void analyzeGroupBy(AstNode astNode) throws LogicalOperatorException {
    SelectOperator selectOp = ((QueryOperator) initializedOperator).getSelectOperator();

    if (selectOp.getSuffixPaths().size() != selectOp.getAggregations().size()) {
      throw new LogicalOperatorException(
          "Group by must bind each seriesPath with an aggregation function");
    }
    ((QueryOperator) initializedOperator).setGroupBy(true);
    int childCount = astNode.getChildCount();

    // parse timeUnit
    long value = parseTokenTime(astNode.getChild(0));
    ((QueryOperator) initializedOperator).setUnit(value);

    // parse show intervals
    AstNode intervalsNode = astNode.getChild(childCount - 1);
    int intervalCount = intervalsNode.getChildCount();
    List<Pair<Long, Long>> intervals = new ArrayList<>();
    AstNode intervalNode;
    long startTime;
    long endTime;
    for (int i = 0; i < intervalCount; i++) {
      intervalNode = intervalsNode.getChild(i);
      AstNode startNode = intervalNode.getChild(0);
      if (startNode.getType() == TOK_DATETIME) {
        startTime = parseTokenTime(startNode);
      } else {
        startTime = Long.parseLong(startNode.getText());
      }
      AstNode endNode = intervalNode.getChild(1);
      if (endNode.getType() == TOK_DATETIME) {
        endTime = parseTokenTime(endNode);
      } else {
        endTime = Long.parseLong(endNode.getText());
      }
      intervals.add(new Pair<>(startTime, endTime));
    }

    ((QueryOperator) initializedOperator).setIntervals(intervals);

    // parse time origin
    long originTime;
    if (childCount == 3) {
      AstNode originNode = astNode.getChild(1).getChild(0);
      if (originNode.getType() == TOK_DATETIME) {
        originTime = parseTokenTime(originNode);
      } else {
        originTime = Long.parseLong(originNode.getText());
      }
    } else {
      originTime = parseTimeFormat(SQLConstant.START_TIME_STR);
    }
    ((QueryOperator) initializedOperator).setOrigin(originTime);
  }

  /**
   * analyze fill type clause.
   *
   * <P>PreviousClause : PREVIOUS COMMA < ValidPreviousTime > LinearClause : LINEAR COMMA <
   * ValidPreviousTime > COMMA < ValidBehindTime >
   */
  private void analyzeFill(AstNode node) throws LogicalOperatorException {
    FilterOperator filterOperator = ((SFWOperator) initializedOperator).getFilterOperator();
    if (!filterOperator.isLeaf() || filterOperator.getTokenIntType() != SQLConstant.EQUAL) {
      throw new LogicalOperatorException("Only \"=\" can be used in fill function");
    }

    Map<TSDataType, IFill> fillTypes = new EnumMap<>(TSDataType.class);
    int childNum = node.getChildCount();
    for (int i = 0; i < childNum; i++) {
      AstNode childNode = node.getChild(i);
      TSDataType dataType = parseTypeNode(childNode.getChild(0));
      AstNode fillTypeNode = childNode.getChild(1);
      switch (fillTypeNode.getType()) {
        case TOK_LINEAR:
          checkTypeFill(dataType, TOK_LINEAR);
          if (fillTypeNode.getChildCount() == 2) {
            long beforeRange = parseTimeUnit(fillTypeNode.getChild(0));
            long afterRange = parseTimeUnit(fillTypeNode.getChild(1));
            fillTypes.put(dataType, new LinearFill(beforeRange, afterRange));
          } else if (fillTypeNode.getChildCount() == 0) {
            fillTypes.put(dataType, new LinearFill(-1, -1));
          } else {
            throw new LogicalOperatorException(
                "Linear fill type must have 0 or 2 valid time ranges");
          }
          break;
        case TOK_PREVIOUS:
          checkTypeFill(dataType, TOK_PREVIOUS);
          if (fillTypeNode.getChildCount() == 1) {
            long preRange = parseTimeUnit(fillTypeNode.getChild(0));
            fillTypes.put(dataType, new PreviousFill(preRange));
          } else if (fillTypeNode.getChildCount() == 0) {
            fillTypes.put(dataType, new PreviousFill(-1));
          } else {
            throw new LogicalOperatorException(
                "Previous fill type must have 0 or 1 valid time range");
          }
          break;
        default:
          break;
      }
    }

    ((QueryOperator) initializedOperator).setFillTypes(fillTypes);
    ((QueryOperator) initializedOperator).setFill(true);
  }

  private void checkTypeFill(TSDataType dataType, int type) throws LogicalOperatorException {
    switch (dataType) {
      case INT32:
      case INT64:
      case FLOAT:
      case DOUBLE:
        if (type != TOK_LINEAR && type != TOK_PREVIOUS) {
          throw new LogicalOperatorException(dataType.toString(),
              String.format("type %s cannot use %s fill function", dataType,
                  TqlParser.tokenNames[type]));
        }
        return;
      case BOOLEAN:
      case TEXT:
        if (type != TOK_PREVIOUS) {
          throw new LogicalOperatorException(dataType.toString(),
              String.format("type %s cannot use %s fill function", dataType,
                  TqlParser.tokenNames[type]));
        }
        return;
      default:
        break;
    }
  }

  /**
   * parse datatype node.
   */
  private TSDataType parseTypeNode(AstNode typeNode) throws LogicalOperatorException {
    String type = typeNode.getText().toLowerCase();
    switch (type) {
      case "int32":
        return TSDataType.INT32;
      case "int64":
        return TSDataType.INT64;
      case "float":
        return TSDataType.FLOAT;
      case "double":
        return TSDataType.DOUBLE;
      case "boolean":
        return TSDataType.BOOLEAN;
      case "text":
        return TSDataType.TEXT;
      default:
        throw new LogicalOperatorException(type, "");
    }
  }

  private long parseTimeUnit(AstNode node) throws LogicalOperatorException {
    long timeInterval = parseTokenDuration(node);
    if (timeInterval <= 0) {
      throw new LogicalOperatorException("Interval must more than 0.");
    }
    return timeInterval;
  }

  private Pair<Path, String> parseLeafNode(AstNode node) throws LogicalOperatorException {
    if (node.getChildCount() != 2) {
      throw new LogicalOperatorException();
    }
    AstNode col = node.getChild(0);
    if (col.getType() != TOK_PATH) {
      throw new LogicalOperatorException();
    }
    Path seriesPath = parsePath(col);
    AstNode rightKey = node.getChild(1);
    String seriesValue;
    if (rightKey.getChild(0).getType() == TqlParser.TOK_DATETIME) {
      if (!seriesPath.equals(SQLConstant.RESERVED_TIME)) {
        throw new LogicalOperatorException(seriesPath.toString(), "Date can only be used to time");
      }
      seriesValue = parseTokenTime(rightKey.getChild(0)) + "";
    } else if (rightKey.getType() == TqlParser.TOK_DATE_EXPR) {
      seriesValue = parseTokenDataExpression(rightKey.getChild(0)) + "";
    } else {
      seriesValue = cascadeChildrenText(rightKey);
    }
    return new Pair<>(seriesPath, seriesValue);
  }

  /**
   * parse time expression, which is addition and subtraction expression of duration time, now() or
   * DataTimeFormat time. <p> eg. now() + 1d - 2h </p>
   */
  private Long parseTokenDataExpression(AstNode astNode) throws LogicalOperatorException {
    if (astNode.getType() == TqlParser.PLUS) {
      return parseTokenDataExpression(astNode.getChild(0)) + parseTokenDataExpression(
          astNode.getChild(1));
    } else if (astNode.getType() == TqlParser.MINUS) {
      return parseTokenDataExpression(astNode.getChild(0)) - parseTokenDataExpression(
          astNode.getChild(1));
    } else {
      return parseTokenTime(astNode);
    }
  }

  /**
   * parse a time token, which can be duration time or now() or DataTimeFormat time.
   */
  private Long parseTokenTime(AstNode astNode) throws LogicalOperatorException {
    if (astNode.getType() == TqlParser.TOK_DURATION) {
      return parseTokenDuration(astNode);
    }
    return parseTimeFormat(cascadeChildrenText((astNode)));
  }

  /**
   * parse duration node to time value.
   *
   * @param astNode represent duration string like: 12d8m9ns, 1y1mo, etc.
   * @return time in milliseconds, microseconds, or nanoseconds depending on the profile
   */
  private Long parseTokenDuration(AstNode astNode) {
    String durationStr = cascadeChildrenText(astNode);
    String timestampPrecision = IoTDBDescriptor.getInstance().getConfig().getTimestampPrecision();

    long total = 0;
    long tmp = 0;
    for (int i = 0; i < durationStr.length(); i++) {
      char ch = durationStr.charAt(i);
      if (Character.isDigit(ch)) {
        tmp *= 10;
        tmp += (ch - '0');
      } else {
        String unit = durationStr.charAt(i) + "";
        // This is to identify units with two letters.
        if (i + 1 < durationStr.length() && !Character.isDigit(durationStr.charAt(i + 1))) {
          i++;
          unit += durationStr.charAt(i);
        }
        total += DatetimeUtils
            .convertDurationStrToLong(tmp, unit.toLowerCase(), timestampPrecision);
        tmp = 0;
      }
    }
    return total;
  }

  private String cascadeChildrenText(AstNode astNode) {
    StringContainer sc = new StringContainer();
    for (Node n : astNode.getChildren()) {
      sc.addTail(((AstNode) n).getText());
    }
    return sc.toString();
  }

  /**
   * function for parsing time format.
   */
  long parseTimeFormat(String timestampStr) throws LogicalOperatorException {
    if (timestampStr == null || timestampStr.trim().equals("")) {
      throw new LogicalOperatorException("Input timestamp cannot be empty");
    }
    if (timestampStr.equalsIgnoreCase(SQLConstant.NOW_FUNC)) {
      return System.currentTimeMillis();
    }
    try {
      return DatetimeUtils.convertDatetimeStrToLong(timestampStr, zoneId);
    } catch (Exception e) {
      throw new LogicalOperatorException(timestampStr,
          "Input like yyyy-MM-dd HH:mm:ss, yyyy-MM-ddTHH:mm:ss or refer to user document for more info.");
    }
  }

  private Path parsePath(AstNode node) {
    int childCount = node.getChildCount();
    String[] path;

    if (childCount == 1 && node.getChild(0).getType() == TOK_ROOT) {
      AstNode childNode = node.getChild(0);
      childCount = childNode.getChildCount();
      path = new String[childCount + 1];
      path[0] = SQLConstant.ROOT;
      for (int i = 0; i < childCount; i++) {
        path[i + 1] = childNode.getChild(i).getText();
      }
    } else {
      path = new String[childCount];
      for (int i = 0; i < childCount; i++) {
        path[i] = node.getChild(i).getText();
      }
    }
    return new Path(new StringContainer(path, TsFileConstant.PATH_SEPARATOR));
  }

  private String removeStringQuote(String src) throws IllegalASTFormatException {
    if (src.length() < 3 || src.charAt(0) != '\'' || src.charAt(src.length() - 1) != '\'') {
      throw new IllegalASTFormatException("remove string", "error format for string with quote: ",
          src);
    }
    return src.substring(1, src.length() - 1);
  }

  private void analyzeDataLoad(AstNode astNode) throws IllegalASTFormatException {
    int childCount = astNode.getChildCount();
    // node seriesPath should have more than one level and first node level must
    // be root
    // if (childCount < 3 ||
    // !SQLConstant.ROOT.equals(astNode.getChild(1).getText().toLowerCase()))
    if (childCount < 3 || !SQLConstant.ROOT.equals(astNode.getChild(1).getText())) {
      throw new IllegalASTFormatException(DATA_LOAD_COMMAND, "Child count < 3\n", astNode.dump());
    }
    String csvPath = astNode.getChild(0).getText();
    if (csvPath.length() < 3 || csvPath.charAt(0) != '\''
        || csvPath.charAt(csvPath.length() - 1) != '\'') {
      throw new IllegalASTFormatException(DATA_LOAD_COMMAND, "Error format csvPath: ", csvPath);
    }
    StringContainer sc = new StringContainer(TsFileConstant.PATH_SEPARATOR);
    sc.addTail(SQLConstant.ROOT);
    for (int i = 2; i < childCount; i++) {
      // String pathNode = astNode.getChild(i).getText().toLowerCase();
      String pathNode = astNode.getChild(i).getText();
      sc.addTail(pathNode);
    }
    initializedOperator = new LoadDataOperator(SQLConstant.TOK_DATALOAD,
        csvPath.substring(1, csvPath.length() - 1),
        sc.toString());
  }

  private void analyzeAuthorCreate(AstNode astNode) throws IllegalASTFormatException {
    int childCount = astNode.getChildCount();
    AuthorOperator authorOperator;
    if (childCount == 2) {
      // create user
      authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_CREATE,
          AuthorOperator.AuthorType.CREATE_USER);
      authorOperator.setUserName(astNode.getChild(0).getChild(0).getText());
      authorOperator.setPassWord(removeStringQuote(astNode.getChild(1).getChild(0).getText()));
    } else if (childCount == 1) {
      // create role
      authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_CREATE,
          AuthorOperator.AuthorType.CREATE_ROLE);
      authorOperator.setRoleName(astNode.getChild(0).getChild(0).getText());
    } else {
      throw new IllegalASTFormatException(INCORRECT_AUTHOR_COMMAND);
    }
    initializedOperator = authorOperator;
  }

  private void analyzeAuthorAlter(AstNode astNode) throws IllegalASTFormatException {
    int childCount = astNode.getChildCount();
    AuthorOperator authorOperator;
    if (childCount == 1) {
      authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_UPDATE_USER,
          AuthorOperator.AuthorType.UPDATE_USER);
      AstNode user = astNode.getChild(0);
      if (user.getChildCount() != 2) {
        throw new IllegalASTFormatException(UPDATE_PASSWORD_COMMAND);
      }
      authorOperator.setUserName(user.getChild(0).getText());
      authorOperator.setNewPassword(removeStringQuote(user.getChild(1).getText()));
    } else {
      throw new IllegalASTFormatException(UPDATE_PASSWORD_COMMAND);
    }
    initializedOperator = authorOperator;
  }

  private void analyzeAuthorDrop(AstNode astNode) throws IllegalASTFormatException {
    int childCount = astNode.getChildCount();
    AuthorOperator authorOperator;
    if (childCount == 1) {
      // drop user or role
      switch (astNode.getChild(0).getType()) {
        case TOK_USER:
          authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_DROP,
              AuthorOperator.AuthorType.DROP_USER);
          authorOperator.setUserName(astNode.getChild(0).getChild(0).getText());
          break;
        case TOK_ROLE:
          authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_DROP,
              AuthorOperator.AuthorType.DROP_ROLE);
          authorOperator.setRoleName(astNode.getChild(0).getChild(0).getText());
          break;
        default:
          throw new IllegalASTFormatException(INCORRECT_AUTHOR_COMMAND);
      }
    } else {
      throw new IllegalASTFormatException(INCORRECT_AUTHOR_COMMAND);
    }
    initializedOperator = authorOperator;
  }

  private void analyzeWatermarkEmbedding(AstNode astNode, int tokenIntType) {
    int childCount = astNode.getChildCount();

    List<String> users = new ArrayList<>();
    for (int i = 0; i < childCount; i++) {
      String user = astNode.getChild(i).getText();
      users.add(user);
    }
    initializedOperator = new DataAuthOperator(tokenIntType, users);
  }

  private void analyzeAuthorGrant(AstNode astNode) throws IllegalASTFormatException {
    int childCount = astNode.getChildCount();
    AuthorOperator authorOperator;
    if (childCount == 2) {
      // grant role to user
      authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_GRANT,
          AuthorOperator.AuthorType.GRANT_ROLE_TO_USER);
      authorOperator.setRoleName(astNode.getChild(0).getChild(0).getText());
      authorOperator.setUserName(astNode.getChild(1).getChild(0).getText());
    } else if (childCount == 3) {
      AstNode privilegesNode = astNode.getChild(1);
      String[] privileges = new String[privilegesNode.getChildCount()];
      for (int i = 0; i < privileges.length; i++) {
        privileges[i] = removeStringQuote(privilegesNode.getChild(i).getText());
      }
      Path nodePath = parsePath(astNode.getChild(2));
      if (astNode.getChild(0).getType() == TOK_USER) {
        // grant user
        authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_GRANT,
            AuthorOperator.AuthorType.GRANT_USER);
        authorOperator.setUserName(astNode.getChild(0).getChild(0).getText());
        authorOperator.setPrivilegeList(privileges);
        authorOperator.setNodeNameList(nodePath);
      } else if (astNode.getChild(0).getType() == TOK_ROLE) {
        // grant role
        authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_GRANT,
            AuthorOperator.AuthorType.GRANT_ROLE);
        authorOperator.setRoleName(astNode.getChild(0).getChild(0).getText());
        authorOperator.setPrivilegeList(privileges);
        authorOperator.setNodeNameList(nodePath);
      } else {
        throw new IllegalASTFormatException(INCORRECT_AUTHOR_COMMAND);
      }
    } else {
      throw new IllegalASTFormatException(INCORRECT_AUTHOR_COMMAND);
    }
    initializedOperator = authorOperator;
  }

  private void analyzeAuthorRevoke(AstNode astNode) throws IllegalASTFormatException {
    int childCount = astNode.getChildCount();
    AuthorOperator authorOperator;
    if (childCount == 2) {
      // revoke role to user
      authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_REVOKE,
          AuthorOperator.AuthorType.REVOKE_ROLE_FROM_USER);
      authorOperator.setRoleName(astNode.getChild(0).getChild(0).getText());
      authorOperator.setUserName(astNode.getChild(1).getChild(0).getText());
    } else if (childCount == 3) {
      AstNode privilegesNode = astNode.getChild(1);
      String[] privileges = new String[privilegesNode.getChildCount()];
      for (int i = 0; i < privileges.length; i++) {
        privileges[i] = removeStringQuote(privilegesNode.getChild(i).getText());
      }
      Path nodePath = parsePath(astNode.getChild(2));
      if (astNode.getChild(0).getType() == TOK_USER) {
        // revoke user
        authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_REVOKE,
            AuthorOperator.AuthorType.REVOKE_USER);
        authorOperator.setUserName(astNode.getChild(0).getChild(0).getText());
        authorOperator.setPrivilegeList(privileges);
        authorOperator.setNodeNameList(nodePath);
      } else if (astNode.getChild(0).getType() == TOK_ROLE) {
        // revoke role
        authorOperator = new AuthorOperator(SQLConstant.TOK_AUTHOR_REVOKE,
            AuthorOperator.AuthorType.REVOKE_ROLE);
        authorOperator.setRoleName(astNode.getChild(0).getChild(0).getText());
        authorOperator.setPrivilegeList(privileges);
        authorOperator.setNodeNameList(nodePath);
      } else {
        throw new IllegalASTFormatException(INCORRECT_AUTHOR_COMMAND);
      }
    } else {
      throw new IllegalASTFormatException(INCORRECT_AUTHOR_COMMAND);
    }
    initializedOperator = authorOperator;
  }

  private void checkMetadataArgs(String dataType, String encoding, String compressor)
      throws MetadataException {
//    final String rle = "RLE";
//    final String plain = "PLAIN";
//    final String ts2Diff = "TS_2DIFF";
//    final String bitmap = "BITMAP";
//    final String gorilla = "GORILLA";
    TSDataType tsDataType;
    TSEncoding tsEncoding;
    if (dataType == null) {
      throw new MetadataException("data type cannot be null");
    }

    try {
      tsDataType = TSDataType.valueOf(dataType);
    } catch (Exception e) {
      throw new MetadataException(String.format("data type %s not support", dataType));
    }

    if (encoding == null) {
      throw new MetadataException("encoding type cannot be null");
    }

    try {
      tsEncoding = TSEncoding.valueOf(encoding);
    } catch (Exception e) {
      throw new MetadataException(String.format("encoding %s is not support", encoding));
    }

    try {
      CompressionType.valueOf(compressor);
    } catch (Exception e) {
      throw new MetadataException(String.format("compressor %s is not support", compressor));
    }

    checkDataTypeEncoding(tsDataType, tsEncoding);
  }

  private void checkDataTypeEncoding(TSDataType tsDataType, TSEncoding tsEncoding)
      throws MetadataException {
    boolean throwExp = false;
    switch (tsDataType) {
      case BOOLEAN:
        if (!(tsEncoding.equals(TSEncoding.RLE) || tsEncoding.equals(TSEncoding.PLAIN))) {
          throwExp = true;
        }
        break;
      case INT32:
      case INT64:
        if (!(tsEncoding.equals(TSEncoding.RLE) || tsEncoding.equals(TSEncoding.PLAIN)
            || tsEncoding.equals(TSEncoding.TS_2DIFF))) {
          throwExp = true;
        }
        break;
      case FLOAT:
      case DOUBLE:
        if (!(tsEncoding.equals(TSEncoding.RLE) || tsEncoding.equals(TSEncoding.PLAIN)
            || tsEncoding.equals(TSEncoding.TS_2DIFF) || tsEncoding.equals(TSEncoding.GORILLA))) {
          throwExp = true;
        }
        break;
      case TEXT:
        if (!tsEncoding.equals(TSEncoding.PLAIN)) {
          throwExp = true;
        }
        break;
      default:
        throwExp = true;
    }
    if (throwExp) {
      throw new MetadataException(
          String.format("encoding %s does not support %s", tsEncoding, tsDataType));
    }
  }
}
