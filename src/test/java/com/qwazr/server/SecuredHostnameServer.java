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
package com.qwazr.server;

import com.qwazr.server.configuration.ServerConfiguration;
import org.apache.commons.lang3.RandomStringUtils;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import java.io.IOException;

public class SecuredHostnameServer implements BaseServer {

	public final static String CONTEXT_ATTRIBUTE_TEST = "test";

	public final String contextAttribute = RandomStringUtils.randomAlphanumeric(5);

	public final String externalUsername = RandomStringUtils.randomAlphanumeric(8);

	public final String realm = RandomStringUtils.randomAlphanumeric(6);

	public final HostnameAuthenticationMechanism.MapPrincipalResolver principalResolver;

	private GenericServer server;

	public SecuredHostnameServer() throws IOException {
		final MemoryIdentityManager identityManager = new MemoryIdentityManager();
		identityManager.addExternal(externalUsername, externalUsername, "secured");
		principalResolver = new HostnameAuthenticationMechanism.MapPrincipalResolver();
		server = GenericServer.of(
				ServerConfiguration.of().webAppAuthentication("HOSTNAME,BASIC").webAppRealm(realm).build())
				.contextAttribute(CONTEXT_ATTRIBUTE_TEST, contextAttribute)
				.identityManagerProvider(realm -> identityManager)
				.hostnamePrincipalResolver(principalResolver)
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
