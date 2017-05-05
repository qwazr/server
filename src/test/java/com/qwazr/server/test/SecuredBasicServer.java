/**
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
package com.qwazr.server.test;

import com.qwazr.server.ApplicationBuilder;
import com.qwazr.server.BaseServer;
import com.qwazr.server.GenericServer;
import com.qwazr.server.MemoryIdentityManager;
import com.qwazr.server.WelcomeShutdownService;
import com.qwazr.server.configuration.ServerConfiguration;
import org.apache.commons.lang3.RandomStringUtils;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import java.io.IOException;

public class SecuredBasicServer implements BaseServer {

	public final static String CONTEXT_ATTRIBUTE_TEST = "test";

	public final String contextAttribute = RandomStringUtils.randomAlphanumeric(5);

	public final String basicUsername = RandomStringUtils.randomAlphanumeric(8);

	public final String basicPassword = RandomStringUtils.randomAlphanumeric(12);

	public final String realm = RandomStringUtils.randomAlphanumeric(6);

	private GenericServer server;

	public SecuredBasicServer() throws IOException {
		final MemoryIdentityManager identityManager = new MemoryIdentityManager();
		identityManager.addBasic(basicUsername, basicUsername, basicPassword, "secured");
		server = GenericServer.of(ServerConfiguration.of().webAppAuthentication("BASIC").webAppRealm(realm).build())
				.contextAttribute(CONTEXT_ATTRIBUTE_TEST, contextAttribute)
				.identityManagerProvider(realm -> identityManager)
				.webService(WelcomeShutdownService.class)
				.servlet(SimpleServlet.class)
				.servlet(SecuredServlet.class)
				.jaxrs(TestJaxRsAppAuth.class)
				.jaxrs(new ApplicationBuilder("/jaxrs-app-auth-singletons/*").classes(RolesAllowedDynamicFeature.class)
						.singletons(new TestJaxRsAppAuth.ServiceAuth()))
				.build();
	}

	@Override
	public GenericServer getServer() {
		return server;
	}

}
