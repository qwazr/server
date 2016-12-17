/**
 * Copyright 2016 Emmanuel Keller / QWAZR
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
package com.qwazr.server.test;

import com.qwazr.server.GenericServer;
import com.qwazr.server.WelcomeShutdownService;
import com.qwazr.server.configuration.ServerConfiguration;
import org.apache.commons.lang3.RandomStringUtils;

import java.io.IOException;

public class SimpleServer {

	public final static String CONTEXT_ATTRIBUTE_TEST = "test";

	public final String contextAttribute = RandomStringUtils.randomAlphanumeric(5);

	public final GenericServer server;

	public SimpleServer() throws IOException {
		final ServerConfiguration.Builder config = ServerConfiguration.of();
		final GenericServer.Builder builder = GenericServer.of(config.build())
				.contextAttribute(CONTEXT_ATTRIBUTE_TEST, contextAttribute)
				.webService(WelcomeShutdownService.class);
		server = builder.build();
	}

}
