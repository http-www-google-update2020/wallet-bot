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
package org.apache.iotdb.cluster.qp.task;

import com.alipay.sofa.jraft.entity.PeerId;
import java.util.concurrent.CountDownLatch;
import org.apache.iotdb.cluster.entity.Server;
import org.apache.iotdb.cluster.rpc.raft.request.BasicRequest;
import org.apache.iotdb.cluster.rpc.raft.response.BasicResponse;

public abstract class QPTask {

  /**
   * QPTask response
   */
  protected BasicResponse response;

  /**
   * QPTask request
   */
  protected BasicRequest request;

  /**
   * The target peer of this task
   */
  protected PeerId targetNode;

  /**
   * Whether it's a synchronization task or not.
   */
  boolean isSyncTask;

  /**
   * Count down latch for sub-tasks
   */
  CountDownLatch taskCountDownLatch;

  /**
   * Num of sub-task
   */
  private int taskNum;

  /**
   * Describe task type
   */
  TaskState taskState;

  /**
   * Describe task type
   */
  TaskType taskType;

  /**
   * Server instance
   */
  protected Server server = Server.getInstance();

  public QPTask(boolean isSyncTask, int taskNum, TaskState taskState, TaskType taskType) {
    this.isSyncTask = isSyncTask;
    this.taskNum = taskNum;
    this.taskCountDownLatch = new CountDownLatch(taskNum);
    this.taskState = taskState;
    this.taskType = taskType;
  }

  /**
   * Process response
   *
   * @param basicResponse response from receiver
   */
  public abstract void receive(BasicResponse basicResponse);

  public boolean isSyncTask() {
    return isSyncTask;
  }

  public void setSyncTask(boolean syncTask) {
    isSyncTask = syncTask;
  }

  public CountDownLatch getTaskCountDownLatch() {
    return taskCountDownLatch;
  }

  public void resetTask() {
    this.taskCountDownLatch = new CountDownLatch(taskNum);
  }

  public TaskState getTaskState() {
    return taskState;
  }

  public void setTaskState(TaskState taskState) {
    this.taskState = taskState;
  }


  public BasicResponse getResponse() {
    return response;
  }

  public void setResponse(BasicResponse response) {
    this.response = response;
  }

  public BasicRequest getRequest() {
    return request;
  }

  public void setRequest(BasicRequest request) {
    this.request = request;
  }

  public enum TaskState {

    /**
     * Initial state
     */
    INITIAL,

    /**
     * Redirect leader
     */
    REDIRECT,

    /**
     * Task finish
     */
    FINISH,

    /**
     * Occur exception in remote node
     */
    EXCEPTION,

    /**
     * Can not connect to remote node
     */
    RAFT_CONNECTION_EXCEPTION
  }

  public enum TaskType {

    /**
     * Single task
     */
    SINGLE,

    /**
     * Batch task
     */
    BATCH
  }

  /**
   * Wait until task is finished.
   */
  public void await() throws InterruptedException {
    this.taskCountDownLatch.await();
  }

  public abstract void shutdown();

  public PeerId getTargetNode() {
    return targetNode;
  }

  public void setTargetNode(PeerId targetNode) {
    this.targetNode = targetNode;
  }
}
