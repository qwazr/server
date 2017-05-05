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

import com.qwazr.server.configuration.ServerConfiguration;
import com.qwazr.utils.AnnotationsUtils;
import com.qwazr.utils.CollectionsUtils;
import com.qwazr.utils.StringUtils;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpHandler;
import io.undertow.server.session.SessionListener;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.FilterMappingInfo;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.LoginConfig;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.ServletSecurityInfo;
import io.undertow.servlet.api.SessionPersistenceManager;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.MultipartConfigElement;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
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
	final private ClassLoader classLoader;
	final private ServletContainer servletContainer;

	final private Map<String, Object> contextAttributes;
	final private Collection<Class<?>> webServices;
	final private Collection<String> webServiceNames;
	final private Collection<String> webServicePaths;
	final private IdentityManagerProvider identityManagerProvider;
	final private HostnameAuthenticationMechanism.PrincipalResolver hostnamePrincipalResolver;
	final private Collection<ConnectorStatisticsMXBean> connectorsStatistics;

	final private Collection<Listener> startedListeners;
	final private Collection<Listener> shutdownListeners;

	final private Collection<Undertow> undertows;
	final private Collection<DeploymentManager> deploymentManagers;

	final private ServerConfiguration configuration;

	final private Collection<ServletInfo> servletInfos;
	final private Collection<FilterInfo> filterInfos;
	final private Collection<FilterMappingInfo> filterMappingInfos;
	final private Collection<ListenerInfo> listenerInfos;
	final private SessionPersistenceManager sessionPersistenceManager;
	final private SessionListener sessionListener;
	final private Logger servletAccessLogger;
	final private Logger restAccessLogger;
	final private MultipartConfigElement defaultMultipartConfig;

	final private UdpServerThread udpServer;

	static final private Logger LOGGER = LoggerFactory.getLogger(GenericServer.class);

	private GenericServer(final Builder builder) throws IOException {

		this.configuration = builder.configuration;
		this.classLoader =
				builder.classLoader == null ? Thread.currentThread().getContextClassLoader() : builder.classLoader;
		this.executorService =
				builder.executorService == null ? Executors.newCachedThreadPool() : builder.executorService;
		this.servletContainer = Servlets.newContainer();

		builder.contextAttribute(this);

		this.contextAttributes = new LinkedHashMap<>(builder.contextAttributes);
		this.webServices = CollectionsUtils.copyIfNotEmpty(builder.webServices, ArrayList::new);
		this.webServiceNames = CollectionsUtils.copyIfNotEmpty(builder.webServiceNames, ArrayList::new);
		this.webServicePaths = CollectionsUtils.copyIfNotEmpty(builder.webServicePaths, ArrayList::new);
		this.undertows = new ArrayList<>();
		this.deploymentManagers = new ArrayList<>();
		this.identityManagerProvider = builder.identityManagerProvider;
		this.hostnamePrincipalResolver = builder.hostnamePrincipalResolver;
		this.servletInfos = CollectionsUtils.copyIfNotEmpty(builder.servletInfos, ArrayList::new);
		this.filterInfos = CollectionsUtils.copyIfNotEmpty(builder.filterInfos, ArrayList::new);
		this.filterMappingInfos = CollectionsUtils.copyIfNotEmpty(builder.filterMappingInfos, ArrayList::new);
		this.listenerInfos = CollectionsUtils.copyIfNotEmpty(builder.listenerInfos, ArrayList::new);
		this.sessionPersistenceManager = builder.sessionPersistenceManager;
		this.sessionListener = builder.sessionListener;
		this.servletAccessLogger = builder.servletAccessLogger;
		this.restAccessLogger = builder.restAccessLogger;
		this.defaultMultipartConfig =
				builder.defaultMultipart == null ? DEFAULT_MULTIPART_CONFIG : builder.defaultMultipart;
		this.udpServer = buildUdpServer(builder, configuration);
		this.startedListeners = CollectionsUtils.copyIfNotEmpty(builder.startedListeners, ArrayList::new);
		this.shutdownListeners = CollectionsUtils.copyIfNotEmpty(builder.shutdownListeners, ArrayList::new);
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

	/**
	 * Returns the named attribute. The method checks the type of the object.
	 *
	 * @param context
	 * @param name
	 * @param type
	 * @param <T>
	 * @return
	 */
	public static <T> T getContextAttribute(final ServletContext context, final String name, final Class<T> type) {
		final Object object = context.getAttribute(name);
		if (object == null)
			return null;
		if (!object.getClass().isAssignableFrom(type))
			throw new RuntimeException(
					"Wrong returned type: " + object.getClass().getName() + " - Expected: " + type.getName());
		return type.cast(object);
	}

	/**
	 * Returns an attribute where the name of the attribute in the name of the class
	 *
	 * @param context
	 * @param cls
	 * @param <T>
	 * @return
	 */
	public static <T> T getContextAttribute(final ServletContext context, final Class<T> cls) {
		return getContextAttribute(context, cls.getName(), cls);
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
		if (identityManagerProvider == null)
			return null;
		return identityManagerProvider.getIdentityManager(
				connector == null || connector.realm == null ? null : connector.realm);
	}

	private void startHttpServer(final ServerConfiguration.WebConnector connector, final DeploymentInfo deploymentInfo,
			final Logger accessLogger, final String jmxName)
			throws IOException, ServletException, OperationsException, MBeanException {

		contextAttributes.forEach(deploymentInfo::addServletContextAttribute);

		if (deploymentInfo.getIdentityManager() != null && !StringUtils.isEmpty(connector.authentication)) {
			if (hostnamePrincipalResolver != null)
				HostnameAuthenticationMechanism.register(deploymentInfo, hostnamePrincipalResolver);
			final LoginConfig loginConfig = Servlets.loginConfig(connector.realm);
			for (String authmethod : StringUtils.split(connector.authentication, ','))
				loginConfig.addLastAuthMethod(authmethod);
			deploymentInfo.setLoginConfig(loginConfig);
		}

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
					ServletApplication.getDeploymentInfo(servletInfos, identityManager, filterInfos, filterMappingInfos,
							listenerInfos, sessionPersistenceManager, sessionListener, classLoader,
							defaultMultipartConfig), servletAccessLogger, "WEBAPP");
		}

		// Launch the jaxrs application if any
		if (webServices != null && !webServices.isEmpty()) {
			final IdentityManager identityManager = getIdentityManager(configuration.webServiceConnector);
			startHttpServer(configuration.webServiceConnector,
					RestApplication.getDeploymentInfo(identityManager, classLoader), restAccessLogger, "WEBSERVICE");
		}

		if (shutdownHook)
			Runtime.getRuntime().addShutdownHook(new Thread(this::stopAll));

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

	final static MultipartConfigElement DEFAULT_MULTIPART_CONFIG =
			new MultipartConfigElement(SystemUtils.getJavaIoTmpDir().getAbsolutePath());

	public interface IdentityManagerProvider {

		IdentityManager getIdentityManager(String realm) throws IOException;

	}

	public static Builder of(ServerConfiguration config, ExecutorService executorService, ClassLoader classLoader) {
		return new Builder(config, executorService, classLoader);
	}

	public static Builder of(ServerConfiguration config, ExecutorService executorService) {
		return of(config, executorService, null);
	}

	public static Builder of(ServerConfiguration config) {
		return of(config, null);
	}

	public static class Builder {

		final ServerConfiguration configuration;
		final ExecutorService executorService;
		final ClassLoader classLoader;

		Map<String, Object> contextAttributes;
		Collection<Class<?>> webServices;
		Collection<String> webServicePaths;
		Collection<String> webServiceNames;
		Collection<UdpServerThread.PacketListener> packetListeners;
		Collection<ServletInfo> servletInfos;
		Collection<FilterInfo> filterInfos;
		Collection<FilterMappingInfo> filterMappingInfos;

		Collection<ListenerInfo> listenerInfos;
		Collection<ServletSecurityInfo> servletSecurityInfos;

		SessionPersistenceManager sessionPersistenceManager;
		SessionListener sessionListener;
		Logger servletAccessLogger;
		Logger restAccessLogger;
		GenericServer.IdentityManagerProvider identityManagerProvider;
		HostnameAuthenticationMechanism.PrincipalResolver hostnamePrincipalResolver;

		Collection<GenericServer.Listener> startedListeners;
		Collection<GenericServer.Listener> shutdownListeners;

		MultipartConfigElement defaultMultipart;

		private Builder(final ServerConfiguration configuration, final ExecutorService executorService,
				final ClassLoader classLoader) {
			this.configuration = configuration;
			this.executorService = executorService;
			this.classLoader = classLoader;
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
			if (webServices == null)
				webServices = new LinkedHashSet<>();
			webServices.add(webService);
			if (webServiceNames == null)
				webServiceNames = new LinkedHashSet<>();
			webServiceNames.add(serviceName.value());
			final Path path = AnnotationsUtils.getFirstAnnotation(webService, Path.class);
			if (path != null) {
				if (webServicePaths == null)
					webServicePaths = new LinkedHashSet<>();
				webServicePaths.add(path.value());
			}
			return this;
		}

		public Builder packetListener(final UdpServerThread.PacketListener packetListener) {
			if (packetListeners == null)
				packetListeners = new LinkedHashSet<>();
			this.packetListeners.add(packetListener);
			return this;
		}

		public Builder contextAttribute(final String name, final Object object) {
			Objects.requireNonNull(name, "The name of the context attribute is null");
			Objects.requireNonNull(object, "The context attribute " + name + " is null");
			if (contextAttributes == null)
				contextAttributes = new LinkedHashMap<>();
			contextAttributes.put(name, object);
			return this;
		}

		public Builder contextAttribute(final Object object) {
			Objects.requireNonNull(object, "The context attribute object is null");
			return contextAttribute(object.getClass().getName(), object);
		}

		public Builder servlet(final ServletInfo servlet) {
			Objects.requireNonNull(servlet, "The ServletInfo object is null");
			if (servletInfos == null)
				servletInfos = new LinkedHashSet<>();
			servletInfos.add(servlet);
			return this;
		}

		public <T extends Servlet> Builder servlet(final String name, final Class<T> servletClass,
				final String... urlPatterns) {
			return servlet(name, servletClass, null, urlPatterns);
		}

		public <T extends Servlet> Builder servlet(final String name, final Class<T> servletClass,
				final GenericFactory<T> instanceFactory, final String... urlPatterns) {
			return servlet(ServletInfoBuilder.servlet(name, servletClass, instanceFactory, urlPatterns));
		}

		public Builder servlet(final Class<? extends Servlet> servletClass, final String... urlPatterns) {
			return servlet(null, servletClass, urlPatterns);
		}

		public Builder jaxrs(final String name, final Class<? extends Application> applicationClass) {
			return servlet(ServletInfoBuilder.jaxrs(name, applicationClass));
		}

		public Builder jaxrs(final Class<? extends Application> applicationClass) {
			return jaxrs(null, applicationClass);
		}

		public Builder jaxrs(final String name, final ApplicationBuilder applicationBuilder) {
			return servlet(ServletInfoBuilder.jaxrs(name, applicationBuilder));
		}

		public Builder jaxrs(final ApplicationBuilder applicationBuilder) {
			return jaxrs(null, applicationBuilder);
		}

		public Builder filter(final FilterInfo filter) {
			Objects.requireNonNull(filter, "The FilterInfo object is null");
			if (filterInfos == null)
				filterInfos = new LinkedHashSet<>();
			filterInfos.add(filter);
			return this;
		}

		public <T extends Filter> Builder filter(final String name, final Class<T> filterClass,
				final GenericFactory<T> instanceFactory) {
			FilterInfoBuilder.filter(name, filterClass, instanceFactory, this);
			return this;
		}

		public Builder filter(final String name, final Class<? extends Filter> filterClass) {
			return filter(name, filterClass, null);
		}

		public Builder filter(final Class<? extends Filter> filterClass) {
			return filter(null, filterClass);
		}

		public Builder filterMapping(final FilterMappingInfo filterMappingInfo) {
			Objects.requireNonNull(filterMappingInfo, "The FilterMappingInfo object is null");
			if (filterMappingInfos == null)
				filterMappingInfos = new LinkedHashSet<>();
			filterMappingInfos.add(filterMappingInfo);
			return this;
		}

		public Builder urlFilterMapping(final String filterName, final String urlMapping,
				final DispatcherType dispatcher) {
			return filterMapping(
					new FilterMappingInfo(filterName, FilterMappingInfo.MappingType.URL, urlMapping, dispatcher));
		}

		public Builder servletFilterMapping(final String filterName, final String servletName,
				final DispatcherType dispatcher) {
			return filterMapping(
					new FilterMappingInfo(filterName, FilterMappingInfo.MappingType.SERVLET, servletName, dispatcher));
		}

		public Builder servletSecurity(final ServletSecurityInfo servletSecurity) {
			Objects.requireNonNull(servletSecurity, "The servletSecurity object is null");
			if (servletSecurityInfos == null)
				servletSecurityInfos = new LinkedHashSet<>();
			servletSecurityInfos.add(servletSecurity);
			return this;
		}

		public Builder listener(final ListenerInfo listener) {
			Objects.requireNonNull(listener, "The ListenerInfo object is null");
			if (listenerInfos == null)
				listenerInfos = new LinkedHashSet<>();
			listenerInfos.add(listener);
			return this;
		}

		public Builder startedListener(final GenericServer.Listener listener) {
			Objects.requireNonNull(listener, "The GenericServer.Listener object is null");
			if (startedListeners == null)
				startedListeners = new LinkedHashSet<>();
			startedListeners.add(listener);
			return this;
		}

		public Builder shutdownListener(final GenericServer.Listener listener) {
			Objects.requireNonNull(listener, "The GenericServer.Listener object is null");
			if (shutdownListeners == null)
				shutdownListeners = new LinkedHashSet<>();
			shutdownListeners.add(listener);
			return this;
		}

		public Builder sessionPersistenceManager(final SessionPersistenceManager manager) {
			sessionPersistenceManager = manager;
			return this;
		}

		public Builder identityManagerProvider(final GenericServer.IdentityManagerProvider provider) {
			identityManagerProvider = provider;
			return this;
		}

		public Builder hostnamePrincipalResolver(
				final HostnameAuthenticationMechanism.PrincipalResolver hostnamePrincipalResolver) {
			this.hostnamePrincipalResolver = hostnamePrincipalResolver;
			return this;
		}

		public Builder sessionListener(final SessionListener listener) {
			sessionListener = listener;
			return this;
		}

		public Builder servletAccessLogger(final Logger logger) {
			servletAccessLogger = logger;
			return this;
		}

		public Builder restAccessLogger(final Logger logger) {
			restAccessLogger = logger;
			return this;
		}

		public Builder defaultMultipartConfig(String location, long maxFileSize, long maxRequestSize,
				int fileSizeThreshold) {
			defaultMultipart = new MultipartConfigElement(
					StringUtils.isEmpty(location) ? SystemUtils.getJavaIoTmpDir().getAbsolutePath() : location,
					maxFileSize, maxRequestSize, fileSizeThreshold);
			return this;
		}

	}
}
