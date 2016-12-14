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

import com.qwazr.utils.AnnotationsUtils;
import io.undertow.server.session.SessionListener;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.SessionPersistenceManager;
import org.slf4j.Logger;

import javax.ws.rs.Path;
import java.util.*;

public class ServerBuilder {

	final Collection<Class<? extends ServiceInterface>> webServices;
	final Collection<String> webServicePaths;
	final Collection<String> webServiceNames;
	final Collection<UdpServerThread.PacketListener> packetListeners;
	final Collection<SecurableServletInfo> servletInfos;
	final Collection<ServletInfo> securedServlets;
	final Map<String, FilterInfo> filterInfos;
	final Collection<ListenerInfo> listenerInfos;

	SessionPersistenceManager sessionPersistenceManager;
	SessionListener sessionListener;
	Logger servletAccessLogger;
	Logger restAccessLogger;
	GenericServer.IdentityManagerProvider identityManagerProvider;
	final Collection<GenericServer.Listener> startedListeners;
	final Collection<GenericServer.Listener> shutdownListeners;

	ServerBuilder() {
		webServices = new LinkedHashSet<>();
		webServicePaths = new LinkedHashSet<>();
		webServiceNames = new LinkedHashSet<>();
		packetListeners = new LinkedHashSet<>();
		servletInfos = new LinkedHashSet<>();
		securedServlets = new HashSet<>();
		filterInfos = new LinkedHashMap<>();
		listenerInfos = new LinkedHashSet<>();
		sessionPersistenceManager = null;
		identityManagerProvider = null;
		sessionListener = null;
		servletAccessLogger = null;
		restAccessLogger = null;
		startedListeners = new LinkedHashSet<>();
		shutdownListeners = new LinkedHashSet<>();
	}

	public void registerWebService(final Class<? extends ServiceInterface> webService) {
		final ServiceName serviceName = AnnotationsUtils.getFirstAnnotation(webService, ServiceName.class);
		Objects.requireNonNull(serviceName, "The ServiceName annotation is missing for " + webService);
		webServices.add(webService);
		webServiceNames.add(serviceName.value());
		final Path path = AnnotationsUtils.getFirstAnnotation(webService, Path.class);
		if (path != null && path.value() != null)
			webServicePaths.add(path.value());
	}

	public void registerPacketListener(final UdpServerThread.PacketListener packetListener) {
		this.packetListeners.add(packetListener);
	}

	public void registerServlet(final SecurableServletInfo servlet) {
		this.servletInfos.add(servlet);
	}

	public void registerFilter(final String path, final FilterInfo filter) {
		this.filterInfos.put(path, filter);
	}

	public void registerListener(final ListenerInfo listener) {
		this.listenerInfos.add(listener);
	}

	public void registerStartedListener(final GenericServer.Listener listener) {
		this.startedListeners.add(listener);
	}

	public void registerShutdownListener(final GenericServer.Listener listener) {
		this.shutdownListeners.add(listener);
	}

	public void setSessionPersistenceManager(final SessionPersistenceManager manager) {
		this.sessionPersistenceManager = manager;
	}

	public void setIdentityManagerProvider(final GenericServer.IdentityManagerProvider provider) {
		this.identityManagerProvider = provider;
	}

	public void setSessionListener(final SessionListener sessionListener) {
		this.sessionListener = sessionListener;
	}

	public void setServletAccessLogger(final Logger logger) {
		this.servletAccessLogger = logger;
	}

	public void setRestAccessLogger(final Logger logger) {
		this.restAccessLogger = logger;
	}

}
