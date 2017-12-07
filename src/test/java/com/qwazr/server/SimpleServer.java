/*
 * Copyright 2016-2017 Emmanuel Keller / QWAZR
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qwazr.server;

import com.qwazr.server.configuration.ServerConfiguration;
import com.qwazr.utils.LoggerUtils;
import com.qwazr.utils.RandomUtils;
import io.undertow.servlet.api.SessionPersistenceManager;

import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class SimpleServer implements BaseServer {

	public final static String CONTEXT_ATTRIBUTE_TEST = "test";

	public final String contextAttribute = RandomUtils.alphanumeric(5);

	private GenericServer server;

	private final Logger accessLogger = LoggerUtils.getLogger(SimpleServer.class);
	public final LogHandler logHandler = new LogHandler();

	SimpleServer(SessionPersistenceManager sessionManager) throws IOException {

		GenericServerBuilder builder = GenericServer.of(ServerConfiguration.of().build())
				.contextAttribute(CONTEXT_ATTRIBUTE_TEST, contextAttribute);

		accessLogger.addHandler(logHandler);
		builder.webAppAccessLogger(accessLogger);
		builder.webServiceAccessLogger(accessLogger);

		builder.getWebAppContext().servlet(SimpleServlet.class, "test_bis").filter(SimpleFilter.class);

		builder.getWebServiceContext()
				.jaxrs(ApplicationBuilder.of("/*")
						.classes(RestApplication.JSON_CLASSES)
						.loadServices()
						.singletons(new WelcomeShutdownService(), new ErrorService()));

		if (sessionManager != null)
			builder.sessionPersistenceManager(sessionManager);

		server = builder.build();
	}

	SimpleServer() throws IOException {
		this(null);
	}

	@Override
	public GenericServer getServer() {
		return server;
	}

	public class LogHandler extends Handler {

		public LogRecord lastRecord;

		@Override
		public void publish(LogRecord record) {
			lastRecord = record;
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() throws SecurityException {
		}
	}

}
