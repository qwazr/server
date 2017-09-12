/*
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
import com.qwazr.server.logs.AccessLogger;
import com.qwazr.server.logs.LogMetricsHandler;
import com.qwazr.utils.CollectionsUtils;
import com.qwazr.utils.LoggerUtils;
import com.qwazr.utils.StringUtils;
import com.qwazr.utils.reflection.ConstructorParameters;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.LoginConfig;
import io.undertow.servlet.api.ServletContainer;
import org.apache.commons.lang3.SystemUtils;

import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GenericServer {

	final private ExecutorService executorService;
	final private ServletContainer servletContainer;
	final private ServletContextBuilder webAppContext;
	final private ServletContextBuilder webServiceContext;

	final private Map<String, Object> contextAttributes;
	final private IdentityManagerProvider identityManagerProvider;
	final private HostnameAuthenticationMechanism.PrincipalResolver hostnamePrincipalResolver;
	final private Collection<ConnectorStatisticsMXBean> connectorsStatistics;

	final private Collection<Listener> startedListeners;
	final private Collection<Listener> shutdownListeners;

	final private Collection<Undertow> undertows;
	final private Collection<DeploymentManager> deploymentManagers;

	final private ServerConfiguration configuration;

	final private AccessLogger webAppAccessLogger;
	final private AccessLogger webServiceAccessLogger;

	final private Set<String> webAppEndPoints;
	final private Set<String> webServiceEndPoints;

	final private UdpServerThread udpServer;

	final private Collection<ObjectName> registeredObjectNames;

	static final private Logger LOGGER = LoggerUtils.getLogger(GenericServer.class);

	GenericServer(final GenericServerBuilder builder)
			throws IOException, ClassNotFoundException, InstantiationException {

		this.configuration = builder.configuration;
		this.executorService =
				builder.executorService == null ? Executors.newCachedThreadPool() : builder.executorService;
		this.servletContainer = Servlets.newContainer();
		this.webAppContext = builder.webAppContext;
		this.webServiceContext = builder.webServiceContext;
		this.webAppEndPoints = webAppContext == null ? null : Collections.unmodifiableSet(webAppContext.endPoints);
		this.webServiceEndPoints =
				webServiceContext == null ? null : Collections.unmodifiableSet(webServiceContext.endPoints);
		builder.contextAttribute(this);
		this.contextAttributes = new LinkedHashMap<>(builder.contextAttributes);
		this.undertows = new ArrayList<>();
		this.deploymentManagers = new ArrayList<>();
		this.identityManagerProvider = builder.identityManagerProvider;
		this.hostnamePrincipalResolver = builder.hostnamePrincipalResolver;
		this.webAppAccessLogger = builder.webAppAccessLogger;
		this.webServiceAccessLogger = builder.webServiceAccessLogger;
		this.udpServer = buildUdpServer(builder, configuration);
		this.startedListeners = CollectionsUtils.copyIfNotEmpty(builder.startedListeners, ArrayList::new);
		this.shutdownListeners = CollectionsUtils.copyIfNotEmpty(builder.shutdownListeners, ArrayList::new);
		this.connectorsStatistics = new ArrayList<>();
		this.registeredObjectNames = new LinkedHashSet<>();
	}

	/**
	 * Returns the named attribute. The method checks the type of the object.
	 *
	 * @param context the context to request
	 * @param name    the name of the attribute
	 * @param type    the expected type
	 * @param <T>     the expected object
	 * @return the expected object
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
	 * @param context the context to request
	 * @param cls     the type of the object
	 * @param <T>     the expected object
	 * @return the expected object
	 */
	public static <T> T getContextAttribute(final ServletContext context, final Class<T> cls) {
		return getContextAttribute(context, cls.getName(), cls);
	}

	Set<String> getWebServiceEndPoints() {
		return webServiceEndPoints;
	}

	Set<String> getWebAppEndPoints() {
		return webAppEndPoints;
	}

	private static UdpServerThread buildUdpServer(final GenericServerBuilder builder,
			final ServerConfiguration configuration) throws IOException {

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

		LOGGER.info("The server is stopping...");

		executeListener(shutdownListeners, false);

		if (udpServer != null) {
			try {
				udpServer.shutdown();
			} catch (InterruptedException e) {
				LOGGER.log(Level.WARNING, e.getMessage(), e);
			}
		}

		for (DeploymentManager manager : deploymentManagers) {
			try {
				if (manager.getState() == DeploymentManager.State.STARTED)
					manager.stop();
				if (manager.getState() == DeploymentManager.State.DEPLOYED)
					manager.undeploy();
			} catch (ServletException e) {
				LOGGER.log(Level.WARNING, e, () -> "Cannot stop the manager: " + e.getMessage());
			}
		}

		undertows.forEach(Undertow::stop);

		executorService.shutdown();
		try {
			executorService.awaitTermination(2, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			LOGGER.log(Level.WARNING, e, e::getMessage);
		}

		// Unregister MBeans
		if (registeredObjectNames != null && !registeredObjectNames.isEmpty()) {
			final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
			for (ObjectName objectName : registeredObjectNames) {
				try {
					mbs.unregisterMBean(objectName);
				} catch (InstanceNotFoundException | MBeanRegistrationException e) {
					LOGGER.log(Level.WARNING, e, e::getMessage);
				}
			}
			registeredObjectNames.clear();
		}

		LOGGER.info("The server is stopped.");
	}

	private IdentityManager getIdentityManager(final ServerConfiguration.WebConnector connector) throws IOException {
		if (identityManagerProvider == null)
			return null;
		return identityManagerProvider.getIdentityManager(
				connector == null || connector.realm == null ? null : connector.realm);
	}

	private final static AtomicInteger serverCounter = new AtomicInteger();

	private void startHttpServer(final ServerConfiguration.WebConnector connector, final ServletContextBuilder context,
			final AccessLogger accessLogger) throws IOException, ServletException, OperationsException, MBeanException {

		if (context == null || context.getServlets().isEmpty())
			return;

		context.setIdentityManager(getIdentityManager(connector));

		contextAttributes.forEach(context::addServletContextAttribute);

		if (context.getIdentityManager() != null && !StringUtils.isEmpty(connector.authentication)) {
			if (hostnamePrincipalResolver != null)
				HostnameAuthenticationMechanism.register(context, hostnamePrincipalResolver);
			final LoginConfig loginConfig = Servlets.loginConfig(connector.realm);
			for (String authmethod : StringUtils.split(connector.authentication, ','))
				loginConfig.addLastAuthMethod(authmethod);
			context.setLoginConfig(loginConfig);
		}

		final DeploymentManager manager = servletContainer.addDeployment(context);
		manager.deploy();

		LOGGER.info(() -> "Start the connector " + configuration.listenAddress + ":" + connector.port);

		HttpHandler httpHandler = manager.start();
		final LogMetricsHandler logMetricsHandler =
				new LogMetricsHandler(httpHandler, configuration.listenAddress, connector.port, context.jmxName,
						accessLogger);
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
		props.put("name", context.jmxName);
		final ObjectName name =
				new ObjectName("com.qwazr.server." + serverCounter.incrementAndGet() + "." + context.jmxName, props);
		mbs.registerMBean(logMetricsHandler, name);
		registeredObjectNames.add(name);
		connectorsStatistics.add(logMetricsHandler);
	}

	/**
	 * Call this method to start the server
	 *
	 * @param shutdownHook pass true to install the StopAll method as Runtime shutdown hook
	 * @throws IOException                  if any IO error occurs
	 * @throws ServletException             if the servlet configuration failed
	 * @throws ReflectiveOperationException if a class instanciation failed
	 * @throws JMException                  if any JMX error occurs
	 */
	final public void start(boolean shutdownHook)
			throws IOException, ServletException, ReflectiveOperationException, JMException {

		LOGGER.info("The server is starting...");
		LOGGER.info(() -> "Data directory sets to: " + configuration.dataDirectory);

		java.util.logging.Logger.getLogger("").setLevel(Level.WARNING);

		if (!configuration.dataDirectory.exists())
			throw new IOException("The data directory does not exists: " + configuration.dataDirectory);
		if (!configuration.dataDirectory.isDirectory())
			throw new IOException("The data directory path is not a directory: " + configuration.dataDirectory);

		if (udpServer != null)
			udpServer.checkStarted();

		// Launch the applications/connector
		startHttpServer(configuration.webAppConnector, webAppContext, webAppAccessLogger);
		startHttpServer(configuration.webServiceConnector, webServiceContext, webServiceAccessLogger);

		if (shutdownHook)
			Runtime.getRuntime().addShutdownHook(new Thread(this::stopAll));

		executeListener(startedListeners, false);

		LOGGER.info("The server started successfully.");
	}

	public Collection<ConnectorStatisticsMXBean> getConnectorsStatistics() {
		return connectorsStatistics;
	}

	public interface Listener {

		void accept(GenericServer server);
	}

	private void executeListener(final Collection<Listener> listeners, final boolean silent) {
		if (listeners == null)
			return;
		listeners.forEach(listener -> {
			try {
				listener.accept(this);
			} catch (Exception e) {
				if (!silent)
					throw ServerException.of("Listeners failure", e);
				else
					LOGGER.log(Level.SEVERE, e, e::getMessage);
			}
		});
	}

	final static MultipartConfigElement DEFAULT_MULTIPART_CONFIG =
			new MultipartConfigElement(SystemUtils.getJavaIoTmpDir().getAbsolutePath());

	public interface IdentityManagerProvider {

		IdentityManager getIdentityManager(String realm) throws IOException;

	}

	public static GenericServerBuilder of(ServerConfiguration config, ExecutorService executorService,
			ClassLoader classLoader, ConstructorParameters constructorParameters) {
		return new GenericServerBuilder(config, executorService, classLoader, constructorParameters);
	}

	public static GenericServerBuilder of(ServerConfiguration config, ExecutorService executorService,
			ClassLoader classLoader) {
		return of(config, executorService, classLoader, null);
	}

	public static GenericServerBuilder of(ServerConfiguration config, ExecutorService executorService) {
		return of(config, executorService, null);
	}

	public static GenericServerBuilder of(ServerConfiguration config) {
		return of(config, null);
	}

}
