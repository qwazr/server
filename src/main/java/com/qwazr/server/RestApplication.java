/**
 * Copyright 2014-2016 Emmanuel Keller / QWAZR
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

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.qwazr.utils.json.JacksonConfig;
import io.undertow.security.idm.IdentityManager;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.SecurityInfo;
import io.undertow.servlet.api.ServletInfo;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.glassfish.jersey.servlet.ServletContainer;

import javax.servlet.ServletContext;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generic RestApplication
 */
public class RestApplication extends Application {

	@Context
	private ServletContext context;

	protected <T> T getContextAttribute(final Class<T> clazz) {
		return context == null || clazz == null ? null : (T) context.getAttribute(clazz.getName());
	}

	public static class WithoutAuth extends RestApplication {

		@Override
		public Set<Class<?>> getClasses() {
			final Set<Class<?>> classes = new LinkedHashSet<>();
			classes.add(JacksonConfig.class);
			classes.add(JacksonJsonProvider.class);
			classes.add(JsonMappingExceptionMapper.class);
			// Get the service from the generic server instance
			final GenericServer server = getContextAttribute(GenericServer.class);
			if (server != null)
				server.forEachWebServices(classes::add);
			return classes;
		}
	}

	public static class WithAuth extends WithoutAuth {

		public Set<Class<?>> getClasses() {
			Set<Class<?>> classes = super.getClasses();
			classes.add(RolesAllowedDynamicFeature.class);
			return classes;
		}
	}

	static DeploymentInfo getDeploymentInfo(final IdentityManager identityManager) {
		final DeploymentInfo deploymentInfo = Servlets.deployment()
				.setClassLoader(RestApplication.class.getClassLoader())
				.setContextPath("/")
				.setDeploymentName("REST");
		final Class<? extends Application> applicationClass =
				identityManager == null ? WithoutAuth.class : WithAuth.class;
		deploymentInfo.addServlets(
				new ServletInfo("REST", ServletContainer.class).addInitParam("javax.ws.rs.Application",
						applicationClass.getName()).setAsyncSupported(true).addMapping("/*"));
		if (identityManager != null) {
			deploymentInfo.setIdentityManager(identityManager);
			deploymentInfo.addSecurityConstraint(Servlets.securityConstraint()
					.setEmptyRoleSemantic(SecurityInfo.EmptyRoleSemantic.AUTHENTICATE)
					.addWebResourceCollection(Servlets.webResourceCollection().addUrlPattern("/*")));
		}
		return deploymentInfo;
	}

}
