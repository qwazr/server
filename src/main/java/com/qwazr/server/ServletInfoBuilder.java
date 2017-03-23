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

import com.qwazr.utils.StringUtils;
import io.undertow.servlet.api.HttpMethodSecurityInfo;
import io.undertow.servlet.api.SecurityInfo;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.ServletSecurityInfo;
import io.undertow.servlet.api.TransportGuaranteeType;
import org.glassfish.jersey.servlet.ServletContainer;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.servlet.Servlet;
import javax.servlet.annotation.HttpConstraint;
import javax.servlet.annotation.HttpMethodConstraint;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import java.util.Map;

public class ServletInfoBuilder {

	public static ServletInfo of(final String name, final Class<? extends Servlet> servletClass) {
		final ServletInfo servletInfo;

		// WebServlet annotation
		if (servletClass.isAnnotationPresent(WebServlet.class)) {

			final WebServlet webServlet = servletClass.getAnnotation(WebServlet.class);
			servletInfo = new ServletInfo(StringUtils.isEmpty(name) ?
					StringUtils.isEmpty(webServlet.name()) ? servletClass.getName() : webServlet.name() :
					name, servletClass);
			servletInfo.setLoadOnStartup(webServlet.loadOnStartup());
			servletInfo.setAsyncSupported(webServlet.asyncSupported());

			servletInfo.addMappings(webServlet.value());
			servletInfo.addMappings(webServlet.urlPatterns());

			for (WebInitParam webInitParam : webServlet.initParams())
				servletInfo.addInitParam(webInitParam.name(), webInitParam.value());

		} else
			servletInfo = new ServletInfo(StringUtils.isEmpty(name) ? servletClass.getName() : name, servletClass);

		// ServletSecurity
		if (servletClass.isAnnotationPresent(ServletSecurity.class)) {

			final ServletSecurity servletSecurity = servletClass.getAnnotation(ServletSecurity.class);
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
		return servletInfo;
	}

	static boolean isJaxRsAuthentication(final ClassLoader classLoader, final ServletInfo servletInfo)
			throws ClassNotFoundException {
		Class<? extends Servlet> servletClass = servletInfo.getServletClass();
		if (servletClass == null)
			return false;
		if (servletClass.isAssignableFrom(ServletContainer.class)) {
			final Map<String, String> initParams = servletInfo.getInitParams();
			if (initParams != null) {
				final String classList = initParams.get("jersey.config.server.provider.classnames");
				if (!StringUtils.isEmpty(classList)) {
					final String[] classes = StringUtils.split(classList, " ,");
					for (String clazz : classes) {
						Class<?> cl = classLoader.loadClass(clazz);
						if (isJaxRsAuthentication(cl))
							return true;
					}
				}
			}
		}
		return isJaxRsAuthentication(servletClass);
	}

	static boolean isJaxRsAuthentication(Class<?> clazz) {
		return clazz.isAnnotationPresent(RolesAllowed.class) || clazz.isAnnotationPresent(PermitAll.class)
				|| clazz.isAnnotationPresent(DenyAll.class);
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
