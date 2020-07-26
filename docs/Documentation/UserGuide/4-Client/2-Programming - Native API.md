<!--

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at
    
        http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.

-->

# Chapter 4: Client
# Programming - Native API
## Usage

### Dependencies

* JDK >= 1.8
* Maven >= 3.1

### How to install in local maven repository

In root directory:
> mvn clean install -pl session -am -DskipTests

### Using IoTDB Native API with Maven

```
<dependencies>
    <dependency>
      <groupId>org.apache.iotdb</groupId>
      <artifactId>iotdb-session</artifactId>
      <version>0.10.0</version>
    </dependency>
</dependencies>
```


### Examples with Native API

Here we show the commonly used interfaces and their parameters in the Native API:

#### Run the Native API

* Initialize a Session

  	Session(String host, int port)

  	Session(String host, String port, String username, String password)

  	Session(String host, int port, String username, String password)

* Open a Session

  ​	Session.open()

* Close a Session

  ​	Session.close()

#### Operations

* Set storage group

  ​	TSStatus setStorageGroup(String storageGroupId)

* Delete one or several storage groups

  ​	TSStatus deleteStorageGroup(String storageGroup)
  
  	TSStatus deleteStorageGroups(List<String> storageGroups)

* Create one timeseries under a existing storage group

  ​	TSStatus createTimeseries(String path, TSDataType dataType, TSEncoding encoding, CompressionType compressor)

* Delete one or several timeseries

  ​	TSStatus deleteTimeseries(String path)
  
  	TSStatus deleteTimeseries(List<String> paths)

* Delete one or several timeseries before a certain timestamp

  ​	TSStatus deleteData(String path, long time)
  
  	TSStatus deleteData(List<String> paths, long time)
 
* Insert data into existing timeseries in batch
 
   ​	TSStatus insertInBatch(List<String> deviceIds, List<Long> times, List<List<String>> measurementsList, List<List<String>> valuesList)

* Insert data into existing timeseries

  ​	TSStatus insert(String deviceId, long time, List<String> measurements, List<String> values)

* Batch insertion into timeseries

  ​	TSExecuteBatchStatementResp insertBatch(RowBatch rowBatch)
  
* Test Insert data into existing timeseries in batch. This method NOT insert data into database and server just return after accept the request, this method should be used to test other time cost in client
 
   ​	TSStatus testInsertInBatch(List<String> deviceIds, List<Long> times, List<List<String>> measurementsList, List<List<String>> valuesList)

* Insert data into existing timeseries. This method NOT insert data into database and server just return after accept the request, this method should be used to test other time cost in client

  ​	TSStatus testInsert(String deviceId, long time, List<String> measurements, List<String> values)

* Batch insertion into timeseries. This method NOT insert data into database and server just return after accept the request, this method should be used to test other time cost in client

  ​	TSExecuteBatchStatementResp testInsertBatch(RowBatch rowBatch)

#### Sample code

To get more information of the following interfaces, please view session/src/main/java/org/apache/iotdb/session/Session.java

The sample code of using these interfaces is in example/session/src/main/java/org/apache/iotdb/SessionExample.java，which provides an example of how to open an IoTDB session, execute a batch insertion.