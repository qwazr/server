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

import com.qwazr.utils.AnnotationsUtils;
import com.qwazr.utils.StringUtils;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.HttpMethodSecurityInfo;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.SecurityInfo;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.ServletSecurityInfo;
import io.undertow.servlet.api.TransportGuaranteeType;
import org.apache.commons.lang3.SystemUtils;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.servlet.ServletProperties;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.MultipartConfigElement;
import javax.servlet.Servlet;
import javax.servlet.annotation.HttpConstraint;
import javax.servlet.annotation.HttpMethodConstraint;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Build a deployment descriptor and add defaultMultipartConfig concept
 */
public class ServletContextBuilder extends DeploymentInfo {

	final String jmxName;
	MultipartConfigElement defaultMultipartConfig;
	final LinkedHashSet<String> endPoints;

	ServletContextBuilder(ClassLoader classLoader, String contextPath, String defaultEncoding, String contextName,
			String jmxName) {
		setClassLoader(classLoader);
		setContextPath(contextPath == null ? "/" : contextPath);
		setDefaultEncoding(defaultEncoding == null ? "UTF-8" : defaultEncoding);
		setDeploymentName(contextName);
		this.jmxName = jmxName;
		this.endPoints = new LinkedHashSet<>();
	}

	public ServletContextBuilder setDefaultMultipartConfig(final MultipartConfigElement defaultMultipartConfig) {
		this.defaultMultipartConfig = defaultMultipartConfig;
		return this;
	}

	public ServletContextBuilder setDefaultMultipartConfig(String location, long maxFileSize, long maxRequestSize,
			int fileSizeThreshold) {
		return setDefaultMultipartConfig(new MultipartConfigElement(
				StringUtils.isEmpty(location) ? SystemUtils.getJavaIoTmpDir().getAbsolutePath() : location, maxFileSize,
				maxRequestSize, fileSizeThreshold));
	}

	@Override
	public ServletContextBuilder addServlet(final ServletInfo servletInfo) {
		if (servletInfo.getMultipartConfig() == null)
			servletInfo.setMultipartConfig(defaultMultipartConfig);
		super.addServlet(servletInfo);
		if (servletInfo.getMappings() != null)
			endPoints.addAll(servletInfo.getMappings());
		return this;
	}

	ServletContextBuilder addServlet(final ServletInfo servletInfo, final Consumer<ServletInfo> servletInfoHook) {
		if (servletInfoHook != null)
			servletInfoHook.accept(servletInfo);
		return addServlet(servletInfo);
	}

	public ServletContextBuilder servlet(final ServletInfo servlet) {
		addServlet(Objects.requireNonNull(servlet, "The ServletInfo object is null"));
		return this;
	}

	public <T extends Servlet> ServletContextBuilder servlet(final String name, final Class<T> servletClass,
			final String... urlPatterns) {
		return servlet(name, servletClass, null, urlPatterns);
	}

	static <T extends Servlet> ServletInfo servletInfo(final String name, final Class<T> servletClass,
			final GenericFactory<T> instanceFactory) {
		return instanceFactory == null ? new ServletInfo(name, servletClass) : new ServletInfo(name, servletClass,
				instanceFactory);
	}

	public ServletContextBuilder servlet(final String name, final Class<? extends Servlet> servletClass,
			final GenericFactory instanceFactory, final String... urlPatterns) {

		final ServletInfo servletInfo;

		// WebServlet annotation
		final WebServlet webServlet = AnnotationsUtils.getFirstAnnotation(servletClass, WebServlet.class);
		if (webServlet != null) {

			servletInfo = servletInfo(StringUtils.isEmpty(name) ? webServlet.name() : name, servletClass,
					instanceFactory);
			servletInfo.setLoadOnStartup(webServlet.loadOnStartup());
			servletInfo.setAsyncSupported(webServlet.asyncSupported());

			servletInfo.addMappings(webServlet.value());
			servletInfo.addMappings(webServlet.urlPatterns());

			for (WebInitParam webInitParam : webServlet.initParams())
				servletInfo.addInitParam(webInitParam.name(), webInitParam.value());

		} else
			servletInfo = servletInfo(StringUtils.isEmpty(name) ? servletClass.getName() : name, servletClass,
					instanceFactory);

		if (urlPatterns != null && urlPatterns.length > 0)
			servletInfo.addMappings(urlPatterns);

		// ServletSecurity
		final ServletSecurity servletSecurity = AnnotationsUtils.getFirstAnnotation(servletClass,
				ServletSecurity.class);
		if (servletSecurity != null) {

			final ServletSecurityInfo servletSecurityInfo = new ServletSecurityInfo();

			// HttpConstraint
			final HttpConstraint httpConstraint = servletSecurity.value();
			servletSecurityInfo.setEmptyRoleSemantic(get(httpConstraint.value()));
			servletSecurityInfo.addRolesAllowed(httpConstraint.rolesAllowed());
			servletSecurityInfo.setTransportGuaranteeType(get(httpConstraint.transportGuarantee()));

			// HttpMethodConstraints
			for (final HttpMethodConstraint httpMethodConstraints : servletSecurity.httpMethodConstraints()) {

				final HttpMethodSecurityInfo httpMethodSecurityInfo = new HttpMethodSecurityInfo();
				httpMethodSecurityInfo.setMethod(httpMethodConstraints.value());
				httpMethodSecurityInfo.setEmptyRoleSemantic(get(httpMethodConstraints.emptyRoleSemantic()));
				httpMethodSecurityInfo.addRolesAllowed(httpMethodConstraints.rolesAllowed());
				httpMethodSecurityInfo.setTransportGuaranteeType(get(httpMethodConstraints.transportGuarantee()));

				servletSecurityInfo.addHttpMethodSecurityInfo(httpMethodSecurityInfo);
			}

			servletInfo.setServletSecurityInfo(servletSecurityInfo);
		}

		final MultipartConfig multipartConfig = AnnotationsUtils.getFirstAnnotation(servletClass,
				MultipartConfig.class);
		if (multipartConfig != null) {
			final String location = StringUtils.isEmpty(multipartConfig.location()) ?
					SystemUtils.getJavaIoTmpDir().getAbsolutePath() :
					multipartConfig.location();
			servletInfo.setMultipartConfig(new MultipartConfigElement(location, multipartConfig.maxFileSize(),
					multipartConfig.maxRequestSize(), multipartConfig.fileSizeThreshold()));
		}

		addServlet(servletInfo);
		return this;
	}

	private static SecurityInfo.EmptyRoleSemantic get(ServletSecurity.EmptyRoleSemantic emptyRoleSemantic) {
		switch (emptyRoleSemantic) {
		case PERMIT:
			return SecurityInfo.EmptyRoleSemantic.PERMIT;
		case DENY:
			return SecurityInfo.EmptyRoleSemantic.DENY;
		}
		return null;
	}

	private static TransportGuaranteeType get(final ServletSecurity.TransportGuarantee transportGuarantee) {
		switch (transportGuarantee) {
		case CONFIDENTIAL:
			return TransportGuaranteeType.CONFIDENTIAL;
		case NONE:
			return TransportGuaranteeType.NONE;
		}
		return null;
	}

	public ServletContextBuilder servlet(final Class<? extends Servlet> servletClass, final String... urlPatterns) {
		return servlet(null, servletClass, urlPatterns);
	}

	public ServletContextBuilder servlets(final Class<? extends Servlet>... servletClasses) {
		if (servletClasses != null)
			for (Class<? extends Servlet> servletClass : servletClasses)
				servlet(servletClass);
		return this;
	}

	public ServletContextBuilder jaxrs(final String name, final Class<? extends Application> applicationClass,
			final Consumer<ServletInfo> servletInfoHook) {
		final ServletInfo servletInfo = new ServletInfo(StringUtils.isEmpty(name) ? applicationClass.getName() : name,
				ServletContainer.class).addInitParam(ServletProperties.JAXRS_APPLICATION_CLASS,
				applicationClass.getName());
		final ApplicationPath path = AnnotationsUtils.getFirstAnnotation(applicationClass, ApplicationPath.class);
		if (path != null)
			servletInfo.addMapping(path.value());
		servletInfo.setAsyncSupported(true).setLoadOnStartup(1);
		addServlet(servletInfo, servletInfoHook);
		return this;
	}

	public ServletContextBuilder jaxrs(final String name, final Class<? extends Application> applicationClass) {
		return jaxrs(name, applicationClass, null);
	}

	public ServletContextBuilder jaxrs(final Class<? extends Application> applicationClass,
			final Consumer<ServletInfo> servletInfoHook) {
		return jaxrs(null, applicationClass, servletInfoHook);
	}

	public ServletContextBuilder jaxrs(final Class<? extends Application> applicationClass) {
		return jaxrs(null, applicationClass);
	}

	public ServletContextBuilder jaxrs(final String name, final ApplicationBuilder applicationBuilder,
			final Consumer<ServletInfo> servletInfoHook) {
		final JaxRsServlet jaxRsServlet = new JaxRsServlet(applicationBuilder.build());
		final ServletInfo servletInfo = new ServletInfo(
				StringUtils.isEmpty(name) ? applicationBuilder.getClass() + "@" + applicationBuilder.hashCode() : name,
				jaxRsServlet.getClass(), new GenericFactory.FromInstance<>(jaxRsServlet));
		if (applicationBuilder.applicationPaths != null)
			servletInfo.addMappings(applicationBuilder.applicationPaths);
		servletInfo.setAsyncSupported(true).setLoadOnStartup(1);
		return addServlet(servletInfo, servletInfoHook);
	}

	public ServletContextBuilder jaxrs(String name, final ApplicationBuilder applicationBuilder) {
		return jaxrs(name, applicationBuilder, null);
	}

	public ServletContextBuilder jaxrs(final ApplicationBuilder applicationBuilder,
			final Consumer<ServletInfo> servletInfoHook) {
		return jaxrs(null, applicationBuilder, servletInfoHook);
	}

	public ServletContextBuilder jaxrs(final ApplicationBuilder applicationBuilder) {
		return jaxrs(null, applicationBuilder);
	}

	public ServletContextBuilder filter(final FilterInfo filter) {
		addFilter(Objects.requireNonNull(filter, "The FilterInfo object is null"));
		return this;
	}

	public <T extends Filter> ServletContextBuilder filter(String filterName, Class<T> filterClass,
			GenericFactory<T> instanceFactory) {

		// WebServlet annotation
		final WebFilter webFilter = AnnotationsUtils.getFirstAnnotation(filterClass, WebFilter.class);
		if (webFilter != null)
			if (filterName == null || filterName.isEmpty())
				filterName = webFilter.filterName();

		final FilterInfo filterInfo = instanceFactory == null ?
				new FilterInfo(filterName, filterClass) :
				new FilterInfo(filterName, filterClass, instanceFactory);

		if (webFilter != null) {
			for (WebInitParam webInitParam : webFilter.initParams())
				filterInfo.addInitParam(webInitParam.name(), webInitParam.value());

			for (String urlPattern : webFilter.urlPatterns())
				for (DispatcherType dispatcherType : webFilter.dispatcherTypes())
					urlFilterMapping(filterName, urlPattern, dispatcherType);

			for (String servletName : webFilter.servletNames())
				for (DispatcherType dispatcherType : webFilter.dispatcherTypes())
					servletFilterMapping(filterName, servletName, dispatcherType);

		}
		return filter(filterInfo);
	}

	public ServletContextBuilder filter(final String name, final Class<? extends Filter> filterClass) {
		return filter(name, filterClass, null);
	}

	public ServletContextBuilder filter(final Class<? extends Filter> filterClass) {
		return filter(null, filterClass);
	}

	public ServletContextBuilder urlFilterMapping(final String filterName, final String urlMapping,
			final DispatcherType dispatcher) {
		addFilterUrlMapping(filterName, urlMapping, dispatcher);
		return this;
	}

	public ServletContextBuilder servletFilterMapping(final String filterName, final String servletName,
			final DispatcherType dispatcher) {
		addFilterServletNameMapping(filterName, servletName, dispatcher);
		return this;
	}

	public ServletContextBuilder listener(final ListenerInfo listener) {
		addListener(Objects.requireNonNull(listener, "The ListenerInfo object is null"));
		return this;
	}

}
