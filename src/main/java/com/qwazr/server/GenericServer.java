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

import com.qwazr.server.configuration.ServerConfiguration;
import com.qwazr.utils.AnnotationsUtils;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpHandler;
import io.undertow.server.session.SessionListener;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.SessionPersistenceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.servlet.ServletException;
import javax.ws.rs.Path;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

final public class GenericServer {

	final private ExecutorService executorService;
	final private ServletContainer servletContainer;

	final private Map<String, Object> contextAttributes;
	final private Collection<Class<?>> webServices;
	final private Collection<String> webServiceNames;
	final private Collection<String> webServicePaths;
	final private IdentityManagerProvider identityManagerProvider;
	final private Collection<ConnectorStatisticsMXBean> connectorsStatistics;

	final private Collection<Listener> startedListeners;
	final private Collection<Listener> shutdownListeners;

	final private Collection<Undertow> undertows;
	final private Collection<DeploymentManager> deploymentManagers;

	final private ServerConfiguration configuration;

	final private Collection<SecurableServletInfo> servletInfos;
	final private Map<String, FilterInfo> filterInfos;
	final private Collection<ListenerInfo> listenerInfos;
	final private SessionPersistenceManager sessionPersistenceManager;
	final private SessionListener sessionListener;
	final private Logger servletAccessLogger;
	final private Logger restAccessLogger;

	final private UdpServerThread udpServer;

	static final private Logger LOGGER = LoggerFactory.getLogger(GenericServer.class);

	private GenericServer(final Builder builder) throws IOException {

		this.configuration = builder.configuration;

		this.executorService =
				builder.executorService == null ? Executors.newCachedThreadPool() : builder.executorService;
		this.servletContainer = Servlets.newContainer();

		builder.contextAttribute(this);

		this.contextAttributes = new LinkedHashMap<>(builder.contextAttributes);
		this.webServices = builder.webServices.isEmpty() ? null : new ArrayList<>(builder.webServices);
		this.webServiceNames = builder.webServiceNames.isEmpty() ? null : new ArrayList<>(builder.webServiceNames);
		this.webServicePaths = builder.webServicePaths.isEmpty() ? null : new ArrayList<>(builder.webServicePaths);
		this.undertows = new ArrayList<>();
		this.deploymentManagers = new ArrayList<>();
		this.identityManagerProvider = builder.identityManagerProvider;
		this.servletInfos = builder.servletInfos.isEmpty() ? null : new ArrayList<>(builder.servletInfos);
		this.filterInfos = builder.filterInfos.isEmpty() ? null : new LinkedHashMap<>(builder.filterInfos);
		this.listenerInfos = builder.listenerInfos.isEmpty() ? null : new ArrayList<>(builder.listenerInfos);
		this.sessionPersistenceManager = builder.sessionPersistenceManager;
		this.sessionListener = builder.sessionListener;
		this.servletAccessLogger = builder.servletAccessLogger;
		this.restAccessLogger = builder.restAccessLogger;
		this.udpServer = buildUdpServer(builder, configuration);
		this.startedListeners = builder.startedListeners.isEmpty() ? null : new ArrayList<>(builder.startedListeners);
		this.shutdownListeners =
				builder.shutdownListeners.isEmpty() ? null : new ArrayList<>(builder.shutdownListeners);
		this.connectorsStatistics = new ArrayList<>();

	}

	public void forEachWebServices(final Consumer<Class<?>> consumer) {
		if (webServices != null)
			webServices.forEach(consumer::accept);
	}

	public void forEachServicePath(final Consumer<String> consumer) {
		if (webServicePaths != null)
			webServicePaths.forEach(consumer::accept);
	}

	public Collection<String> getWebServiceNames() {
		return webServiceNames;
	}

	private static UdpServerThread buildUdpServer(final Builder builder, final ServerConfiguration configuration)
			throws IOException {

		if (builder.packetListeners == null || builder.packetListeners.isEmpty())
			return null;

		if (configuration.multicastConnector.address != null && configuration.multicastConnector.port != -1)
			return new UdpServerThread(configuration.multicastConnector.address, configuration.multicastConnector.port,
					builder.packetListeners);
		else
			return new UdpServerThread(
					new InetSocketAddress(configuration.listenAddress, configuration.webServiceConnector.port),
					builder.packetListeners);
	}

	private synchronized void start(final Undertow undertow) {
		// start the server
		undertow.start();
		undertows.add(undertow);
	}

	public synchronized void stopAll() {

		if (LOGGER.isInfoEnabled())
			LOGGER.info("The server is stopping...");

		executeListener(shutdownListeners);

		if (udpServer != null) {
			try {
				udpServer.shutdown();
			} catch (InterruptedException e) {
				if (LOGGER.isWarnEnabled())
					LOGGER.warn(e.getMessage(), e);
			}
		}

		for (DeploymentManager manager : deploymentManagers) {
			try {
				if (manager.getState() == DeploymentManager.State.STARTED)
					manager.stop();
				if (manager.getState() == DeploymentManager.State.DEPLOYED)
					manager.undeploy();
			} catch (ServletException e) {
				if (LOGGER.isWarnEnabled())
					LOGGER.warn("Cannot stop the manager: " + e.getMessage(), e);
			}
		}

		undertows.forEach(Undertow::stop);

		executorService.shutdown();
		try {
			executorService.awaitTermination(2, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			if (LOGGER.isWarnEnabled())
				LOGGER.warn(e.getMessage(), e);
		}

		if (LOGGER.isInfoEnabled())
			LOGGER.info("The server is stopped.");
	}

	private IdentityManager getIdentityManager(final ServerConfiguration.WebConnector connector) throws IOException {
		if (identityManagerProvider == null || connector == null || connector.realm == null)
			return null;
		return identityManagerProvider.getIdentityManager(connector.realm);
	}

	private void startHttpServer(final ServerConfiguration.WebConnector connector, final DeploymentInfo deploymentInfo,
			final Logger accessLogger, final String jmxName)
			throws IOException, ServletException, OperationsException, MBeanException {

		contextAttributes.forEach(deploymentInfo::addServletContextAttribute);

		if (deploymentInfo.getIdentityManager() != null)
			deploymentInfo.setLoginConfig(Servlets.loginConfig("BASIC", connector.realm));

		final DeploymentManager manager = servletContainer.addDeployment(deploymentInfo);
		manager.deploy();

		if (LOGGER.isInfoEnabled())
			LOGGER.info("Start the connector " + configuration.listenAddress + ":" + connector.port);

		HttpHandler httpHandler = manager.start();
		final LogMetricsHandler logMetricsHandler =
				new LogMetricsHandler(httpHandler, accessLogger, configuration.listenAddress, connector.port, jmxName);
		deploymentManagers.add(manager);
		httpHandler = logMetricsHandler;

		final Undertow.Builder servletBuilder = Undertow.builder()
				.addHttpListener(connector.port, configuration.listenAddress)
				.setServerOption(UndertowOptions.NO_REQUEST_TIMEOUT, 10000)
				.setHandler(httpHandler);
		start(servletBuilder.build());

		// Register MBeans
		final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		final Hashtable<String, String> props = new Hashtable<>();
		props.put("type", "connector");
		props.put("name", jmxName);
		final ObjectName name = new ObjectName("com.qwazr.server." + this.hashCode(), props);
		mbs.registerMBean(logMetricsHandler, name);
		connectorsStatistics.add(logMetricsHandler);
	}

	/**
	 * Call this method to start the server
	 *
	 * @throws IOException      if any IO error occur
	 * @throws ServletException if the servlet configuration failed
	 */
	final public void start(boolean shutdownHook)
			throws IOException, ServletException, ReflectiveOperationException, OperationsException, MBeanException {

		if (LOGGER.isInfoEnabled())
			LOGGER.info("The server is starting...");

		if (LOGGER.isInfoEnabled())
			LOGGER.info("Data directory sets to: " + configuration.dataDirectory);

		java.util.logging.Logger.getLogger("").setLevel(Level.WARNING);

		if (!configuration.dataDirectory.exists())
			throw new IOException("The data directory does not exists: " + configuration.dataDirectory);
		if (!configuration.dataDirectory.isDirectory())
			throw new IOException("The data directory path is not a directory: " + configuration.dataDirectory);

		if (LOGGER.isInfoEnabled())
			LOGGER.info("Data directory sets to: " + configuration.dataDirectory);

		if (udpServer != null)
			udpServer.checkStarted();

		// Launch the servlet application if any
		if (servletInfos != null && !servletInfos.isEmpty()) {
			final IdentityManager identityManager = getIdentityManager(configuration.webAppConnector);
			startHttpServer(configuration.webAppConnector,
					ServletApplication.getDeploymentInfo(servletInfos, identityManager, filterInfos, listenerInfos,
							sessionPersistenceManager, sessionListener), servletAccessLogger, "WEBAPP");
		}

		// Launch the jaxrs application if any
		if (webServices != null && !webServices.isEmpty()) {
			final IdentityManager identityManager = getIdentityManager(configuration.webServiceConnector);
			startHttpServer(configuration.webServiceConnector, RestApplication.getDeploymentInfo(identityManager),
					restAccessLogger, "WEBSERVICE");
		}

		if (shutdownHook) {
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					stopAll();
				}
			});
		}

		executeListener(startedListeners);

		if (LOGGER.isInfoEnabled())
			LOGGER.info("The server started successfully.");
	}

	public Collection<ConnectorStatisticsMXBean> getConnectorsStatistics() {
		return connectorsStatistics;
	}

	public interface Listener {

		void accept(GenericServer server);
	}

	private void executeListener(final Collection<Listener> listeners) {
		if (listeners == null)
			return;
		listeners.forEach(listener -> {
			try {
				listener.accept(this);
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}
		});
	}

	public interface IdentityManagerProvider {

		IdentityManager getIdentityManager(String realm) throws IOException;

	}

	public static Builder of(ServerConfiguration config, ExecutorService executorService) {
		return new Builder(config, executorService);
	}

	public static class Builder {

		final ServerConfiguration configuration;
		final ExecutorService executorService;
		final Map<String, Object> contextAttributes;
		final Collection<Class<?>> webServices;
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

		private Builder(final ServerConfiguration configuration, final ExecutorService executorService) {
			this.configuration = configuration;
			this.executorService = executorService;
			contextAttributes = new LinkedHashMap<>();
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

		public ServerConfiguration getConfiguration() {
			return configuration;
		}

		public GenericServer build() throws IOException {
			return new GenericServer(this);
		}

		public Builder webService(final Class<?> webService) {
			final ServiceName serviceName = AnnotationsUtils.getFirstAnnotation(webService, ServiceName.class);
			Objects.requireNonNull(serviceName, "The ServiceName annotation is missing for " + webService);
			webServices.add(webService);
			webServiceNames.add(serviceName.value());
			final Path path = AnnotationsUtils.getFirstAnnotation(webService, Path.class);
			if (path != null && path.value() != null)
				webServicePaths.add(path.value());
			return this;
		}

		public Builder packetListener(final UdpServerThread.PacketListener packetListener) {
			this.packetListeners.add(packetListener);
			return this;
		}

		public Builder contextAttribute(final String name, final Object object) {
			Objects.requireNonNull(object, "The context attribute " + name + " is null");
			this.contextAttributes.put(name, object);
			return this;
		}

		public Builder contextAttribute(final Object object) {
			Objects.requireNonNull(object, "The context attribute object is null");
			return contextAttribute(object.getClass().getName(), object);
		}

		public Builder servlet(final SecurableServletInfo servlet) {
			this.servletInfos.add(servlet);
			return this;
		}

		public Builder filter(final String path, final FilterInfo filter) {
			this.filterInfos.put(path, filter);
			return this;
		}

		public Builder listener(final ListenerInfo listener) {
			this.listenerInfos.add(listener);
			return this;
		}

		public Builder startedListener(final GenericServer.Listener listener) {
			this.startedListeners.add(listener);
			return this;
		}

		public Builder shutdownListener(final GenericServer.Listener listener) {
			this.shutdownListeners.add(listener);
			return this;
		}

		public Builder sessionPersistenceManager(final SessionPersistenceManager manager) {
			this.sessionPersistenceManager = manager;
			return this;
		}

		public Builder identityManagerProvider(final GenericServer.IdentityManagerProvider provider) {
			this.identityManagerProvider = provider;
			return this;
		}

		public Builder sessionListener(final SessionListener sessionListener) {
			this.sessionListener = sessionListener;
			return this;
		}

		public Builder servletAccessLogger(final Logger logger) {
			this.servletAccessLogger = logger;
			return this;
		}

		public Builder restAccessLogger(final Logger logger) {
			this.restAccessLogger = logger;
			return this;
		}

	}
}
