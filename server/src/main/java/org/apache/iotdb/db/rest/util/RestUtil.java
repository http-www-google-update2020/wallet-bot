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
package org.apache.iotdb.db.rest.util;

import org.apache.iotdb.db.conf.IoTDBConstant;
import org.apache.iotdb.db.rest.filter.AuthenticationFilter;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestUtil {

  private static final Logger logger = LoggerFactory.getLogger(RestUtil.class);

  private RestUtil() {}

  public static ServletContextHandler getRestContextHandler() {
    ServletContextHandler ctx =
        new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
    ResourceConfig jerseyConfig = new ResourceConfig();
    jerseyConfig.register(AuthenticationFilter.class);
    ctx.setContextPath("/");
    jerseyConfig.packages("org.apache.iotdb.db.rest.controller");
    ServletHolder jerseyServletHolder = new ServletHolder(new ServletContainer(jerseyConfig));
    ctx.addServlet(jerseyServletHolder, "/rest/*");
    ctx.setWelcomeFiles(new String[]{"index.html"});
    ServletHolder staticHolder = new ServletHolder("default", DefaultServlet.class);
    String webPath = System.getProperty(IoTDBConstant.IOTDB_HOME, null);
    if(webPath == null) {
      webPath = System.getProperty("user.dir");
    }
    logger.debug(webPath);
    staticHolder.setInitParameter("resourceBase","file://" + webPath+ "/web/build");
    staticHolder.setInitParameter("dirAllowed","true");
    ctx.addServlet(staticHolder, "/");
    return ctx;
  }

  public static Server getJettyServer(ServletContextHandler handler, int port) {
    Server server = new Server(port);
    ErrorHandler errorHandler = new ErrorHandler();
    errorHandler.setShowStacks(true);
    errorHandler.setServer(server);
    server.addBean(errorHandler);
    server.setHandler(handler);
    return server;
  }

}
