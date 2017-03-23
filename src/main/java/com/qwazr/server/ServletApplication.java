/**
 * s * Copyright 2014-2016 Emmanuel Keller / QWAZR
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

import io.undertow.security.idm.IdentityManager;
import io.undertow.server.session.SessionListener;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.SecurityInfo;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.SessionPersistenceManager;

import javax.servlet.DispatcherType;
import java.util.Collection;
import java.util.Map;

class ServletApplication {

	static DeploymentInfo getDeploymentInfo(final Collection<ServletInfo> servletInfos,
			final IdentityManager identityManager, final Map<String, FilterInfo> filterInfos,
			final Collection<ListenerInfo> listenersInfos, final SessionPersistenceManager sessionPersistenceManager,
			final SessionListener sessionListener, final ClassLoader classLoader) throws ClassNotFoundException {

		final DeploymentInfo deploymentInfo = Servlets.deployment()
				.setClassLoader(Thread.currentThread().getContextClassLoader())
				.setContextPath("/")
				.setDefaultEncoding("UTF-8")
				.setDeploymentName(ServletApplication.class.getName());

		if (sessionPersistenceManager != null)
			deploymentInfo.setSessionPersistenceManager(sessionPersistenceManager);

		if (identityManager != null)
			deploymentInfo.setIdentityManager(identityManager);

		if (servletInfos != null) {
			for (ServletInfo servletInfo : servletInfos) {
				deploymentInfo.addServlet(servletInfo);
				if (identityManager != null && ServletInfoBuilder.isJaxRsAuthentication(classLoader, servletInfo)) {
					deploymentInfo.addSecurityConstraint(Servlets.securityConstraint()
							.setEmptyRoleSemantic(SecurityInfo.EmptyRoleSemantic.AUTHENTICATE)
							.addWebResourceCollection(
									Servlets.webResourceCollection().addUrlPatterns(servletInfo.getMappings())));
				}
			}
		}

		if (filterInfos != null) {
			filterInfos.forEach((path, filterInfo) -> {
				deploymentInfo.addFilter(filterInfo);
				deploymentInfo.addFilterUrlMapping(filterInfo.getName(), path, DispatcherType.REQUEST);
			});
		}
		if (listenersInfos != null)
			deploymentInfo.addListeners(listenersInfos);
		if (sessionListener != null)
			deploymentInfo.addSessionListener(sessionListener);
		return deploymentInfo;
	}

}
