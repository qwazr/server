/**
 * Copyright 2015-2017 Emmanuel Keller / QWAZR
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
import com.qwazr.utils.ClassLoaderUtils;
import com.qwazr.utils.StringUtils;
import io.undertow.servlet.api.HttpMethodSecurityInfo;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.SecurityInfo;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.ServletSecurityInfo;
import io.undertow.servlet.api.TransportGuaranteeType;
import org.apache.commons.lang3.SystemUtils;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.servlet.ServletProperties;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.servlet.MultipartConfigElement;
import javax.servlet.Servlet;
import javax.servlet.annotation.HttpConstraint;
import javax.servlet.annotation.HttpMethodConstraint;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.util.Collection;
import java.util.Map;

class ServletInfoBuilder {

	static <T extends Servlet> ServletInfo of(final String name, final Class<T> servletClass,
			final GenericFactory<T> instanceFactory) {
		return instanceFactory == null ?
				new ServletInfo(name, servletClass) :
				new ServletInfo(name, servletClass, instanceFactory);
	}

	static ServletInfo servlet(final String name, final Class<? extends Servlet> servletClass,
			final GenericFactory instanceFactory, final String... urlPatterns) {

		final ServletInfo servletInfo;

		// WebServlet annotation
		final WebServlet webServlet = AnnotationsUtils.getFirstAnnotation(servletClass, WebServlet.class);
		if (webServlet != null) {

			servletInfo = of(StringUtils.isEmpty(name) ? webServlet.name() : name, servletClass, instanceFactory);
			servletInfo.setLoadOnStartup(webServlet.loadOnStartup());
			servletInfo.setAsyncSupported(webServlet.asyncSupported());

			servletInfo.addMappings(webServlet.value());
			servletInfo.addMappings(webServlet.urlPatterns());
			if (urlPatterns != null)
				servletInfo.addMappings(urlPatterns);

			for (WebInitParam webInitParam : webServlet.initParams())
				servletInfo.addInitParam(webInitParam.name(), webInitParam.value());

		} else
			servletInfo = of(StringUtils.isEmpty(name) ? servletClass.getName() : name, servletClass, instanceFactory);

		// ServletSecurity
		final ServletSecurity servletSecurity =
				AnnotationsUtils.getFirstAnnotation(servletClass, ServletSecurity.class);
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

		final MultipartConfig multipartConfig =
				AnnotationsUtils.getFirstAnnotation(servletClass, MultipartConfig.class);
		if (multipartConfig != null) {
			final String location = StringUtils.isEmpty(multipartConfig.location()) ?
					SystemUtils.getJavaIoTmpDir().getAbsolutePath() :
					multipartConfig.location();
			servletInfo.setMultipartConfig(new MultipartConfigElement(location, multipartConfig.maxFileSize(),
					multipartConfig.maxRequestSize(), multipartConfig.fileSizeThreshold()));
		}

		return servletInfo;
	}

	static ServletInfo jaxrs(String name, Class<? extends Application> applicationClass) {
		final ServletInfo servletInfo =
				new ServletInfo(StringUtils.isEmpty(name) ? applicationClass.getName() : name, ServletContainer.class)
						.addInitParam(ServletProperties.JAXRS_APPLICATION_CLASS, applicationClass.getName());
		final ApplicationPath path = AnnotationsUtils.getFirstAnnotation(applicationClass, ApplicationPath.class);
		if (path != null)
			servletInfo.addMapping(path.value());
		return servletInfo.setAsyncSupported(true).setLoadOnStartup(1);
	}

	static ServletInfo jaxrs(String name, final ApplicationBuilder applicationBuilder) {
		final JaxRsServlet jaxRsServlet = new JaxRsServlet(applicationBuilder.build());
		final ServletInfo servletInfo = new ServletInfo(
				StringUtils.isEmpty(name) ? applicationBuilder.getClass() + "@" + applicationBuilder.hashCode() : name,
				jaxRsServlet.getClass());
		servletInfo.setInstanceFactory(new GenericFactory.FromInstance<>(jaxRsServlet));
		servletInfo.addMappings(applicationBuilder.applicationPaths).setAsyncSupported(true).setLoadOnStartup(1);
		return servletInfo;
	}

	static boolean isJaxRsAuthentication(final ServletInfo servletInfo)
			throws ClassNotFoundException, InstantiationException {

		// Check if this is a JaxRsServlet
		final InstanceFactory instanceFactory = servletInfo.getInstanceFactory();
		if (instanceFactory != null && instanceFactory instanceof GenericFactory.FromInstance) {
			final Object instance = instanceFactory.createInstance().getInstance();
			if (instance != null && instance instanceof JaxRsServlet)
				return isJaxRsAuthentication(((JaxRsServlet) instance).resourceConfig);
		}

		final Class<? extends Servlet> servletClass = servletInfo.getServletClass();
		if (servletClass == null)
			return false;

		// Check a generic ServletContainer
		if (servletClass.isAssignableFrom(ServletContainer.class)) {
			final Map<String, String> initParams = servletInfo.getInitParams();
			if (initParams != null) {
				final String classList = initParams.get("jersey.config.server.provider.classnames");
				if (!StringUtils.isEmpty(classList)) {
					final String[] classes = StringUtils.split(classList, " ,");
					for (String clazz : classes) {
						Class<?> cl = ClassLoaderUtils.findClass(clazz);
						if (isJaxRsAuthentication(cl))
							return true;
					}
				}
				final String appClass = initParams.get(ServletProperties.JAXRS_APPLICATION_CLASS);
				if (!StringUtils.isEmpty(appClass))
					if (isJaxRsAuthentication(ClassLoaderUtils.findClass(appClass)))
						return true;
			}
		}

		return isJaxRsAuthentication(servletClass);
	}

	private static boolean isJaxRsAuthentication(ResourceConfig configuration) {
		final Collection<Class<?>> classes = configuration.getClasses();
		if (classes != null) {
			for (Class<?> clazz : classes)
				if (isJaxRsAuthentication(clazz))
					return true;
		}
		final Collection<Object> singletons = configuration.getSingletons();
		if (singletons != null) {
			for (Object singleton : singletons)
				if (singleton != null && isJaxRsAuthentication(singleton.getClass()))
					return true;
		}
		return false;
	}

	private static boolean isJaxRsAuthentication(Class<?> clazz) {
		return clazz.isAnnotationPresent(RolesAllowed.class) || clazz.isAnnotationPresent(PermitAll.class) ||
				clazz.isAnnotationPresent(DenyAll.class) || clazz.isAnnotationPresent(ServletSecurity.class);
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

}
