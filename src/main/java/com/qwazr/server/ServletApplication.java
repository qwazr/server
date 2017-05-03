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
import io.undertow.servlet.api.FilterMappingInfo;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.SecurityInfo;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.SessionPersistenceManager;

import java.util.Collection;

class ServletApplication {

	static DeploymentInfo getDeploymentInfo(final Collection<ServletInfo> servletInfos,
			final IdentityManager identityManager, final Collection<FilterInfo> filterInfos,
			final Collection<FilterMappingInfo> filterMappingInfos, final Collection<ListenerInfo> listenersInfos,
			final SessionPersistenceManager sessionPersistenceManager, final SessionListener sessionListener,
			final ClassLoader classLoader) throws ClassNotFoundException, InstantiationException {

		final DeploymentInfo deploymentInfo =
				Servlets.deployment().setClassLoader(classLoader).setContextPath("/").setDefaultEncoding("UTF-8")
						.setDeploymentName(ServletApplication.class.getName());

		if (sessionPersistenceManager != null)
			deploymentInfo.setSessionPersistenceManager(sessionPersistenceManager);

		if (identityManager != null)
			deploymentInfo.setIdentityManager(identityManager);

		if (servletInfos != null) {
			deploymentInfo.addServlets(servletInfos);
			for (ServletInfo servletInfo : servletInfos) {
				if (ServletInfoBuilder.isJaxRsAuthentication(servletInfo)) {
					deploymentInfo.addSecurityConstraint(Servlets.securityConstraint().setEmptyRoleSemantic(
							SecurityInfo.EmptyRoleSemantic.AUTHENTICATE).addWebResourceCollection(
							Servlets.webResourceCollection().addUrlPatterns(servletInfo.getMappings())));
				}
			}
		}

		if (filterInfos != null)
			deploymentInfo.addFilters(filterInfos);
		if (filterMappingInfos != null) {
			filterMappingInfos.forEach((filterMappingInfo) -> {
				switch (filterMappingInfo.getMappingType()) {
				case URL:
					deploymentInfo
							.addFilterUrlMapping(filterMappingInfo.getFilterName(), filterMappingInfo.getMapping(),
												 filterMappingInfo.getDispatcher());
					break;
				case SERVLET:
					deploymentInfo.addFilterServletNameMapping(filterMappingInfo.getFilterName(),
															   filterMappingInfo.getMapping(),
															   filterMappingInfo.getDispatcher());
					break;
				}
			});
		}

		if (listenersInfos != null)
			deploymentInfo.addListeners(listenersInfos);
		if (sessionListener != null)
			deploymentInfo.addSessionListener(sessionListener);
		return deploymentInfo;
	}

}
