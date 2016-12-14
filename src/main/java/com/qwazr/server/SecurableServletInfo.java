/**
 * Copyright 2015-2016 Emmanuel Keller / QWAZR
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

import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.ServletInfo;

import javax.servlet.Servlet;

public class SecurableServletInfo extends ServletInfo {

	private boolean secure;

	public SecurableServletInfo(final String name, final Class<? extends Servlet> servletClass) {
		super(name, servletClass);
	}

	public SecurableServletInfo(final String name, Class<? extends Servlet> servletClass,
			final InstanceFactory<? extends Servlet> instanceFactory) {
		super(name, servletClass, instanceFactory);
	}

	public ServletInfo setSecure(boolean secure) {
		this.secure = secure;
		return this;
	}

	public boolean isSecure() {
		return secure;
	}

	public static SecurableServletInfo servlet(final String name, final Class<? extends Servlet> servletClass) {
		return new SecurableServletInfo(name, servletClass);
	}

	public static SecurableServletInfo servlet(final String name, final Class<? extends Servlet> servletClass,
			final InstanceFactory<? extends Servlet> instanceFactory) {
		return new SecurableServletInfo(name, servletClass, instanceFactory);
	}

}
