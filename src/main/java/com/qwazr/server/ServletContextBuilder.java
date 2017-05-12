/**
 * s * Copyright 2015-2017 Emmanuel Keller / QWAZR
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

import com.qwazr.utils.StringUtils;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.FilterMappingInfo;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.SecurityInfo;
import io.undertow.servlet.api.ServletInfo;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.SystemUtils;

import javax.servlet.MultipartConfigElement;
import java.util.Collection;
import java.util.LinkedHashSet;

public class ServletContextBuilder {

	final ClassLoader classLoader;
	final String contextPath;
	final String defaultEncoding;
	final String contextName;
	MultipartConfigElement defaultMultipartConfig;
	Collection<ServletInfo> servletInfos;
	Collection<FilterMappingInfo> filterMappingInfos;
	Collection<FilterInfo> filterInfos;
	Collection<ListenerInfo> listenerInfos;

	ServletContextBuilder(ClassLoader classLoader, String contextPath, String defaultEncoding, String contextName) {
		this.classLoader = classLoader;
		this.contextPath = contextPath;
		this.defaultEncoding = defaultEncoding;
		this.contextName = contextName;
	}

	public ServletContextBuilder defaultMultipartConfig(final MultipartConfigElement defaultMultipartConfig) {
		this.defaultMultipartConfig = defaultMultipartConfig;
		return this;
	}

	public ServletContextBuilder defaultMultipartConfig(String location, long maxFileSize, long maxRequestSize,
			int fileSizeThreshold) {
		return defaultMultipartConfig(new MultipartConfigElement(
				StringUtils.isEmpty(location) ? SystemUtils.getJavaIoTmpDir().getAbsolutePath() : location, maxFileSize,
				maxRequestSize, fileSizeThreshold));
	}

	public ServletContextBuilder servlet(final ServletInfo servlet) {
		if (servletInfos == null)
			servletInfos = new LinkedHashSet<>();
		servletInfos.add(servlet);
		return this;
	}

	public ServletContextBuilder listener(final ListenerInfo listener) {
		if (listenerInfos == null)
			listenerInfos = new LinkedHashSet<>();
		listenerInfos.add(listener);
		return this;
	}

	public ServletContextBuilder filter(final FilterInfo filter) {
		if (filterInfos == null)
			filterInfos = new LinkedHashSet<>();
		filterInfos.add(filter);
		return this;
	}

	public ServletContextBuilder filterMapping(final FilterMappingInfo filterMappingInfo) {
		if (filterMappingInfos == null)
			filterMappingInfos = new LinkedHashSet<>();
		filterMappingInfos.add(filterMappingInfo);
		return this;
	}

	DeploymentInfo build() throws InstantiationException, ClassNotFoundException {

		if (CollectionUtils.isEmpty(servletInfos))
			return null;

		final DeploymentInfo deploymentInfo = Servlets.deployment().
				setClassLoader(classLoader).
				setContextPath(contextPath == null ? "/" : contextPath).
				setDefaultEncoding(defaultEncoding == null ? "UTF-8" : defaultEncoding).
				setDeploymentName(contextName);

		if (listenerInfos != null)
			deploymentInfo.addListeners(listenerInfos);

		if (servletInfos != null) {

			if (defaultMultipartConfig != null) {
				servletInfos.forEach(servletInfo -> {
					if (servletInfo.getMultipartConfig() == null)
						servletInfo.setMultipartConfig(defaultMultipartConfig);
				});
			}

			deploymentInfo.addServlets(servletInfos);
			for (final ServletInfo servletInfo : servletInfos) {
				if (ServletInfoBuilder.isJaxRsAuthentication(servletInfo)) {
					deploymentInfo.addSecurityConstraint(Servlets.securityConstraint()
							.setEmptyRoleSemantic(SecurityInfo.EmptyRoleSemantic.AUTHENTICATE)
							.addWebResourceCollection(
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
					deploymentInfo.addFilterUrlMapping(filterMappingInfo.getFilterName(),
							filterMappingInfo.getMapping(), filterMappingInfo.getDispatcher());
					break;
				case SERVLET:
					deploymentInfo.addFilterServletNameMapping(filterMappingInfo.getFilterName(),
							filterMappingInfo.getMapping(), filterMappingInfo.getDispatcher());
					break;
				}
			});
		}

		return deploymentInfo;
	}
}
