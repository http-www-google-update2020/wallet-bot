# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

import sys
import struct

# If you generate IoTDB python library manually, add it to your python path

#for example, if you run compile.sh, you can use the following code:
# sys.path.append("../target")

#if you use maven to compile the thrift api, just use the follwoing code:
sys.path.append("../../thrift/target/generated-sources-python")

from thrift.protocol import TBinaryProtocol, TCompactProtocol
from thrift.transport import TSocket, TTransport

from iotdb.rpc.TSIService import Client, TSCreateTimeseriesReq, TSInsertRecordReq, \
    TSInsertTabletReq, TSExecuteStatementReq, TSOpenSessionReq, TSQueryDataSet, \
    TSFetchResultsReq, TSCloseOperationReq, \
    TSCloseSessionReq
from iotdb.rpc.ttypes import TSProtocolVersion, TSFetchMetadataReq

TSDataType = {
    'BOOLEAN': 0,
    'INT32': 1,
    'INT64': 2,
    'FLOAT': 3,
    'DOUBLE': 4,
    'TEXT': 5
}

TSEncoding = {
    'PLAIN': 0,
    'PLAIN_DICTIONARY': 1,
    'RLE': 2,
    'DIFF': 3,
    'TS_2DIFF': 4,
    'BITMAP': 5,
    'GORILLA': 6,
    'REGULAR': 7
}

Compressor = {
    'UNCOMPRESSED': 0,
    'SNAPPY': 1,
    'GZIP': 2,
    'LZO': 3,
    'SDT': 4,
    'PAA': 5,
    'PLA': 6
}


class Enum:
    def __init__(self):
        pass


MetaQueryTypes = Enum()
MetaQueryTypes.CATALOG_COLUMN = "COLUMN"
MetaQueryTypes.CATALOG_TIMESERIES = "SHOW_TIMESERIES"
MetaQueryTypes.CATALOG_STORAGE_GROUP = "SHOW_STORAGE_GROUP"
MetaQueryTypes.CATALOG_DEVICES = "SHOW_DEVICES"
MetaQueryTypes.CATALOG_CHILD_PATHS = "SHOW_CHILD_PATHS"


# used to do `and` operation with bitmap to judge whether the value is null
flag = 0x80

INT32_BYTE_LEN = 4
BOOL_BYTE_LEN = 1
INT64_BYTE_LEN = 8
FLOAT_BYTE_LEN = 4
DOUBLE_BYTE_LEN = 8


def is_null(bitmap_bytes, rowNum):
    bitmap = bitmap_bytes[rowNum // 8]
    shift = rowNum % 8
    return ((flag >> shift) & bitmap) == 0


def convertQueryDataSet(queryDataSet, dataTypeList):
    time_bytes = queryDataSet.time
    value_bytes_list = queryDataSet.valueList
    bitmap_list = queryDataSet.bitmapList
    row_count = len(time_bytes) // 8
    time_unpack_str = '>' + str(row_count) + 'q'
    records = []
    times = struct.unpack(time_unpack_str, time_bytes)
    for i in range(row_count):
        records.append([times[i]])

    for i in range(len(dataTypeList)):
        value_type = dataTypeList[i]
        value_bytes = value_bytes_list[i]
        bitmap = bitmap_list[i]
        # the actual number of value
        if value_type == 'BOOLEAN':
            value_count = len(value_bytes) // BOOL_BYTE_LEN
            values_list = struct.unpack('>' + str(value_count) + '?', value_bytes)
        elif value_type == 'INT32':
            value_count = len(value_bytes) // INT32_BYTE_LEN
            values_list = struct.unpack('>' + str(value_count) + 'i', value_bytes)
        elif value_type == 'INT64':
            value_count = len(value_bytes) // INT64_BYTE_LEN
            values_list = struct.unpack('>' + str(value_count) + 'q', value_bytes)
        elif value_type == 'FLOAT':
            value_count = len(value_bytes) // FLOAT_BYTE_LEN
            values_list = struct.unpack('>' + str(value_count) + 'f', value_bytes)
        elif value_type == 'DOUBLE':
            value_count = len(value_bytes) // DOUBLE_BYTE_LEN
            values_list = struct.unpack('>' + str(value_count) + 'd', value_bytes)
        elif value_type == 'TEXT':
            values_list = []

        # current index for value in values_list
        value_index = 0
        for j in range(row_count):
            if is_null(bitmap, j):
                records[j].append('null')
            else:
                if value_type != 'TEXT':
                    records[j].append(values_list[value_index])
                else:
                    size = value_bytes[:4]
                    value_bytes = value_bytes[4:]
                    size = struct.unpack('>i', size)[0]
                    records[j].append(value_bytes[:size])
                    value_bytes = value_bytes[size:]
                value_index += 1

    return records

def valueListToBytes(values, dataTypes):
    valueByte = bytearray();
    for value, dataType in enumerate(values, dataTypes):
        pass
    return valueByte


if __name__ == '__main__':
    ip = "127.0.0.1"
    port = "6667"
    username = 'root'
    password = 'root'
    # Make socket
    transport = TSocket.TSocket(ip, port)

    # Buffering is critical. Raw sockets are very slow
    transport = TTransport.TFramedTransport(transport)

    # Wrap in a protocol
    # use TCompactProtocol if the server enable thrift compression,
    # otherwise use TBinaryProtocol
    protocol = TBinaryProtocol.TBinaryProtocol(transport)

    # Create a client to use the protocol encoder
    client = Client(protocol)

    # Connect!
    transport.open()

    # Authentication
    clientProtocol = TSProtocolVersion.IOTDB_SERVICE_PROTOCOL_V3
    resp = client.openSession(TSOpenSessionReq(client_protocol=clientProtocol,
                                               username=username,
                                               password=password))
    if resp.serverProtocolVersion != clientProtocol:
        print('Inconsistent protocol, server version: %d, client version: %d'
              % (resp.serverProtocolVersion, clientProtocol))
        if resp.serverProtocolVersion > clientProtocol:
          exit()
    sessionId = resp.sessionId

    # This is necessary for resource control
    stmtId = client.requestStatementId(sessionId)

    # create a storage group
    status = client.setStorageGroup(sessionId, "root.group1")
    print(status.message)

    # create timeseries
    status = client.createTimeseries(TSCreateTimeseriesReq(sessionId,
                                                           "root.group1.s1",
                                                           TSDataType['INT64'],
                                                           TSEncoding['PLAIN'],
                                                           Compressor['UNCOMPRESSED']))
    print(status.message)
    status = client.createTimeseries(TSCreateTimeseriesReq(sessionId,
                                                           "root.group1.s2",
                                                           TSDataType['INT32'],
                                                           TSEncoding['PLAIN'],
                                                           Compressor['UNCOMPRESSED']))
    print(status.message)
    status = client.createTimeseries(TSCreateTimeseriesReq(sessionId,
                                                           "root.group1.s3",
                                                           TSDataType['DOUBLE'],
                                                           TSEncoding['PLAIN'],
                                                           Compressor['UNCOMPRESSED']))
    print(status.message)
    status = client.createTimeseries(TSCreateTimeseriesReq(sessionId,
                                                           "root.group1.s4",
                                                           TSDataType['FLOAT'],
                                                           TSEncoding['PLAIN'],
                                                           Compressor['UNCOMPRESSED']))
    print(status.message)
    status = client.createTimeseries(TSCreateTimeseriesReq(sessionId,
                                                           "root.group1.s5",
                                                           TSDataType['BOOLEAN'],
                                                           TSEncoding['PLAIN'],
                                                           Compressor['UNCOMPRESSED']))
    print(status.message)
    status = client.createTimeseries(TSCreateTimeseriesReq(sessionId,
                                                           "root.group1.s6",
                                                           TSDataType['TEXT'],
                                                           TSEncoding['PLAIN'],
                                                           Compressor['UNCOMPRESSED']))
    print(status.message)

    deviceId = "root.group1"
    measurements = ["s1", "s2", "s3", "s4", "s5", "s6"]

    # insert a single row
    values = [1, 11, 1.1, 11.1, True, "\'text0\'"]
    dataTypes = [TSDataType['INT64'], TSDataType['INT32'], TSDataType['DOUBLE'],
                 TSDataType['FLOAT'], TSDataType['BOOLEAN'], TSDataType['TEXT']]
    
    value_pack_str = '>hqhihdhfh?hi' + str(len(values[5])) + 's'
    encoding = 'utf-8'
    valueByte = bytearray()
    
    valueByte.extend(struct.pack(value_pack_str,dataTypes[0], values[0],
                                             dataTypes[1], values[1],
                                             dataTypes[2], values[2],
                                             dataTypes[3], values[3],
                                             dataTypes[4], values[4],
                                             dataTypes[5], len(values[5]), bytes(values[5], encoding)))
    timestamp = 1
    
    status = client.insertRecord(TSInsertRecordReq(sessionId, deviceId, measurements, valueByte, timestamp))
    print(status.message)

    # insert multiple rows, this interface is more efficient
    values = bytearray()
    times = bytearray()

    rowCnt = 3

    # the first 3 belong to 's1', the second 3 belong to 's2'... the last 3
    # belong to 's6'
    # to transfer a string, you must first send its length and then its bytes
    # (like the last 3 'i7s'). Text values should start and end with ' or ".
    # IoTDB use big endian in rpc
    value_pack_str = '>3q3i3d3f3bi7si7si7s'
    time_pack_str = '>3q'
    encoding = 'utf-8'
    values.extend(struct.pack(value_pack_str, 2, 3, 4, 22, 33, 44, 2.2, 3.3,
                              4.4, 22.2, 33.3, 44.4, True, True, False,
                              len(bytes('\'text1\'', encoding)),
                              bytes('\'text1\'', encoding),
                              len(bytes('\'text2\'', encoding)),
                              bytes('\'text2\'', encoding),
                              len(bytes('\'text3\'', encoding)),
                              bytes('\'text3\'', encoding)))
    # warning: the data in batch must be sorted by time
    times.extend(struct.pack(time_pack_str, 2, 3, 4))
    resp = client.insertTablet(TSInsertTabletReq(sessionId,deviceId,
                                                  measurements, values,
                                                  times, dataTypes, rowCnt))
    status = resp.code
    print(status)

    # execute deletion (or other statements)
    resp = client.executeStatement(TSExecuteStatementReq(sessionId, "DELETE FROM "
                                                            "root.group1 where time < 2", stmtId))
    status = resp.status
    print(status.message)

    # query the data
    stmt = "SELECT * FROM root.group1"
    fetchSize = 2
    # this is also for resource control, make sure different queries will not use the same id at the same time
    resp = client.executeQueryStatement(TSExecuteStatementReq(sessionId, stmt, stmtId))
    # headers
    dataTypeList = resp.dataTypeList
    print(resp.columns)
    print(dataTypeList)

    status = resp.status
    print(status.message)

    queryId = resp.queryId
    while True:
        rst = client.fetchResults(TSFetchResultsReq(sessionId, stmt, fetchSize,
                                                    queryId, True)).queryDataSet
        records = convertQueryDataSet(rst, dataTypeList)
        if len(records) == 0:
            break
        for record in records:
            print(record)

    # do not forget to close it when a query is over
    closeReq = TSCloseOperationReq(sessionId)
    closeReq.queryId = queryId
    client.closeOperation(closeReq)

    # query metadata
    metaReq = TSFetchMetadataReq(sessionId=sessionId, type=MetaQueryTypes.CATALOG_DEVICES)
    print(client.fetchMetadata(metaReq).status)

    metaReq = TSFetchMetadataReq(sessionId=sessionId,
                                 type=MetaQueryTypes.CATALOG_TIMESERIES,
                                 columnPath='root')
    #print(client.fetchMetadata(metaReq).timeseriesList)

    metaReq = TSFetchMetadataReq(sessionId=sessionId,
                                 type=MetaQueryTypes.CATALOG_CHILD_PATHS,
                                 columnPath='root')
    #print(client.fetchMetadata(metaReq).childPaths)

    metaReq = TSFetchMetadataReq(sessionId=sessionId, type=MetaQueryTypes.CATALOG_STORAGE_GROUP)
    #print(client.fetchMetadata(metaReq).storageGroups)

    metaReq = TSFetchMetadataReq(sessionId=sessionId,
                                 type=MetaQueryTypes.CATALOG_COLUMN,
                                 columnPath='root.group1.s1')
    print(client.fetchMetadata(metaReq).dataType)

    # and do not forget to close the session before exiting
    client.closeSession(TSCloseSessionReq(sessionId))
