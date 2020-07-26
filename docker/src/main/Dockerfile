#
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

#This file allow you compile the IoTDB from source codes.

FROM openjdk:11-jre-slim
RUN apt update \
  # procps is for `free` command
  && apt install wget unzip lsof procps maven thrift-compiler=0.9.1-2.1 -y \
  && wget https://github.com/apache/incubator-iotdb/archive/rel/0.8.zip \
  && unzip 0.8.zip \
  && rm 0.8.zip \
  && cd incubator-iotdb-rel-0.8 \
  && mvn package -pl server,client -am -Papache-release -DskipTests -Dthrift.download-url="http://www.apache.org/licenses/LICENSE-2.0.txt" -Dthrift.exec.absolute.path="/usr/bin/thrift" \
  && cd target/ \
  && unzip apache-iotdb-0.8.1-SNAPSHOT-incubating-bin.zip \
  && mkdir /iotdb \
  && mv apache-iotdb-0.8.1-SNAPSHOT-incubating/* /iotdb/ \
  && cd ../../ \
  && mvn clean \
  && ls -lh ~/.m2 \
  && rm -rf ~/.m2 \
  && rm -rf /incubator-iotdb-rel-0.8 \
  && sed -i '119d' /iotdb/conf/logback.xml \
  && apt remove wget maven unzip thrift-compiler -y \
  && apt autoremove -y \
  && apt purge --auto-remove -y \
  && apt clean -y
EXPOSE 6667
EXPOSE 31999
VOLUME /iotdb/data
VOLUME /iotdb/logs
#ENTRYPOINT ["/iotdb/bin/start-server.sh"]
