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
package org.apache.iotdb.db.exception.qp;

/**
 * This exception is thrown while meeting error in parsing ast tree to generate logical operator.
 */
public class IllegalASTFormatException extends QueryProcessorException {

  private static final long serialVersionUID = -8987915911329315588L;

  public IllegalASTFormatException(String msg) {
    super(msg);
  }

  public IllegalASTFormatException(Throwable e) {
    super(e);
  }

  public IllegalASTFormatException(String message, Throwable cause) {
    super(message, cause);
  }
}
