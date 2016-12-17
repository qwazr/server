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
 **/
package com.qwazr.server.configuration;

import com.qwazr.utils.StringUtils;
import org.apache.commons.io.filefilter.AndFileFilter;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.net.util.SubnetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

public class ServerConfiguration implements ConfigurationProperties {

	private final static Logger LOGGER = LoggerFactory.getLogger(ServerConfiguration.class);

	private final Map<Object, Object> properties;

	public final File dataDirectory;
	public final File tempDirectory;

	public final Set<File> etcDirectories;
	public final FileFilter etcFileFilter;

	public final String publicAddress;
	public final String listenAddress;

	public final WebConnector webAppConnector;
	public final WebConnector webServiceConnector;
	public final WebConnector multicastConnector;

	public final Set<String> masters;
	public final Set<String> groups;

	public ServerConfiguration(final String... args) throws IOException {
		this(System.getenv(), System.getProperties(), argsToMap(args));
	}

	protected ServerConfiguration(final Map<?, ?>... propertiesMaps) throws IOException {

		// Merge the maps.
		properties = new HashMap<>();
		if (propertiesMaps != null) {
			for (Map<?, ?> props : propertiesMaps)
				if (props != null)
					props.forEach((key, value) -> properties.put(key.toString(), value));
		}

		//Set the data directory
		dataDirectory = getDataDirectory(getStringProperty(QWAZR_DATA, null));
		if (dataDirectory == null)
			throw new IOException("The data directory has not been set.");
		if (!dataDirectory.exists())
			throw new IOException("The data directory does not exists: " + dataDirectory.getAbsolutePath());
		if (!dataDirectory.isDirectory())
			throw new IOException("The data directory is not a directory: " + dataDirectory.getAbsolutePath());

		//Set the temp directory
		tempDirectory = getTempDirectory(dataDirectory, getStringProperty(QWAZR_TEMP, null));
		if (!tempDirectory.exists())
			tempDirectory.mkdirs();
		if (!dataDirectory.exists())
			throw new IOException("The temp directory does not exists: " + tempDirectory.getAbsolutePath());
		if (!dataDirectory.isDirectory())
			throw new IOException("The temp directory is not a directory: " + tempDirectory.getAbsolutePath());

		//Set the configuration directories
		etcDirectories = getEtcDirectories(getStringProperty(QWAZR_ETC_DIR, null));
		etcFileFilter = buildEtcFileFilter(getStringProperty(QWAZR_ETC, null));

		//Set the listen address
		listenAddress = findListenAddress(getStringProperty(LISTEN_ADDR, null));

		//Set the public address
		publicAddress = findPublicAddress(getStringProperty(PUBLIC_ADDR, null), this.listenAddress);

		//Set the connectors
		webAppConnector = new WebConnector(publicAddress, getIntegerProperty(WEBAPP_PORT, null), 9090,
				getStringProperty(WEBAPP_REALM, null));
		webServiceConnector = new WebConnector(publicAddress, getIntegerProperty(WEBSERVICE_PORT, null), 9091,
				getStringProperty(WEBSERVICE_REALM, null));
		multicastConnector =
				new WebConnector(getStringProperty(MULTICAST_ADDR, null), getIntegerProperty(MULTICAST_PORT, null),
						9091, null);

		// Collect the master address.
		final LinkedHashSet<String> set = new LinkedHashSet<>();
		try {
			findMatchingAddress(getStringProperty(QWAZR_MASTERS, null), set);
		} catch (SocketException e) {
			LOGGER.warn("Failed in extracting IP information. No master server is configured.");
		}
		this.masters = set.isEmpty() ? null : Collections.unmodifiableSet(set);

		this.groups = buildSet(getStringProperty(QWAZR_GROUPS, null), ",; \t", true);
	}

	public Collection<File> getEtcFiles() {
		// List the configuration files
		if (etcDirectories == null)
			return null;
		final Set<File> etcFiles = new LinkedHashSet<>();
		etcDirectories.forEach(dir -> {
			final File[] files = etcFileFilter == null ? dir.listFiles() : dir.listFiles(etcFileFilter);
			if (files != null)
				for (File file : files)
					etcFiles.add(file);
		});
		return etcFiles;
	}

	public String getStringProperty(final String propName, final String defaultValue) {
		final Object o = properties.get(propName);
		return o == null ? defaultValue : o.toString();
	}

	public Integer getIntegerProperty(final String propName, final Integer defaultValue) {
		final Object o = properties.get(propName);
		if (o == null)
			return defaultValue;
		if (o instanceof Number)
			return ((Number) o).intValue();
		return Integer.parseInt(o.toString());
	}

	protected static void fillStringListProperty(final String value, final String separatorChars, final boolean trim,
			final Consumer<String> consumer) {
		if (value == null)
			return;
		final String[] parts = StringUtils.split(value, separatorChars);
		for (String part : parts)
			if (part != null)
				consumer.accept(trim ? part.trim() : part);
	}

	protected static Set<String> buildSet(final String value, final String separatorChars, final boolean trim) {
		if (value == null || value.isEmpty())
			return null;
		final HashSet<String> set = new HashSet<>();
		fillStringListProperty(value, separatorChars, trim, set::add);
		return Collections.unmodifiableSet(set);
	}

	private static File getDataDirectory(final String dataDir) {
		//Set the data directory
		return StringUtils.isEmpty(dataDir) ? new File(System.getProperty("user.dir")) : new File(dataDir);
	}

	private static File getTempDirectory(final File dataDir, final String value) {
		return value == null || value.isEmpty() ? new File(dataDir, "tmp") : new File(value);
	}

	private static Set<File> getEtcDirectories(final String value) {
		final Set<File> set = new LinkedHashSet<>();
		fillStringListProperty(value == null ? "etc" : value, File.pathSeparator, true, part -> {
			// By design relative path are relative to the working directory
			final File etcFile = new File(part);
			set.add(etcFile);
			LOGGER.info("Configuration (ETC) directory: " + etcFile.getAbsolutePath());
		});
		return Collections.unmodifiableSet(set);
	}

	private static FileFilter buildEtcFileFilter(final String etcFilter) {
		if (StringUtils.isEmpty(etcFilter))
			return FileFileFilter.FILE;
		final String[] array = StringUtils.split(etcFilter, ',');
		if (array == null || array.length == 0)
			return FileFileFilter.FILE;
		return new AndFileFilter(FileFileFilter.FILE, new ConfigurationFileFilter(array));
	}

	public static class WebConnector {

		public final String address;
		public final String realm;
		public final int port;
		public final String addressPort;

		private WebConnector(final String address, final Integer port, final int defaulPort, final String realm) {
			this.address = address;
			this.realm = realm;
			this.port = port == null ? defaulPort : port;
			this.addressPort = this.address == null ? null : this.address + ":" + this.port;
		}

	}

	/**
	 * Manage that kind of pattern:
	 * 192.168.0.0/16,172.168.0.0/16
	 * 192.168.0.0/16
	 * 10.3.12.12
	 *
	 * @param addressPattern
	 * @return
	 * @throws SocketException
	 */
	private static void findMatchingAddress(final String addressPattern, final Collection<String> collect)
			throws SocketException {
		final String[] patterns = StringUtils.split(addressPattern, ",; ");
		if (patterns == null)
			return;
		for (String pattern : patterns) {
			if (pattern == null)
				continue;
			pattern = pattern.trim();
			if (!pattern.contains("/")) {
				collect.add(pattern);
				continue;
			}
			final SubnetUtils.SubnetInfo subnet = pattern.contains("/") ? new SubnetUtils(pattern).getInfo() : null;
			final Enumeration<NetworkInterface> enumInterfaces = NetworkInterface.getNetworkInterfaces();
			while (enumInterfaces != null && enumInterfaces.hasMoreElements()) {
				final NetworkInterface ifc = enumInterfaces.nextElement();
				if (!ifc.isUp())
					continue;
				final Enumeration<InetAddress> enumAddresses = ifc.getInetAddresses();
				while (enumAddresses != null && enumAddresses.hasMoreElements()) {
					final InetAddress inetAddress = enumAddresses.nextElement();
					if (!(inetAddress instanceof Inet4Address))
						continue;
					final String address = inetAddress.getHostAddress();
					if (subnet != null && subnet.isInRange(address) || address.equals(pattern))
						collect.add(address);
				}
			}
		}
	}

	private final static String DEFAULT_LISTEN_ADDRESS = "0.0.0.0";

	private static String findListenAddress(final String addressPattern) {
		if (StringUtils.isEmpty(addressPattern))
			return DEFAULT_LISTEN_ADDRESS;
		try {
			final ArrayList<String> list = new ArrayList<>();
			findMatchingAddress(addressPattern, list);
			return list.isEmpty() ? DEFAULT_LISTEN_ADDRESS : list.get(0);
		} catch (SocketException e) {
			LOGGER.warn("Failed in extracting IP informations. Listen address set to default (" + DEFAULT_LISTEN_ADDRESS
					+ ")", e);
			return DEFAULT_LISTEN_ADDRESS;
		}
	}

	private final static String DEFAULT_PUBLIC_ADDRESS = "localhost";

	private static String getLocalHostAddress() {
		try {
			return InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			LOGGER.warn("Cannot extract the address of the localhost.", e);
			return DEFAULT_PUBLIC_ADDRESS;
		}
	}

	private static String findPublicAddress(final String addressPattern, final String listenAddress) {
		if (StringUtils.isEmpty(addressPattern))
			return StringUtils.isEmpty(listenAddress) || DEFAULT_LISTEN_ADDRESS.equals(listenAddress) ?
					getLocalHostAddress() :
					listenAddress;
		try {
			final ArrayList<String> list = new ArrayList<>();
			findMatchingAddress(addressPattern, list);
			return list.isEmpty() ? DEFAULT_PUBLIC_ADDRESS : list.get(0);
		} catch (SocketException e) {
			final String addr = StringUtils.isEmpty(listenAddress) || DEFAULT_LISTEN_ADDRESS.equals(listenAddress) ?
					DEFAULT_PUBLIC_ADDRESS :
					listenAddress;
			LOGGER.warn("Failed in extracting IP informations. Public address set to default (" + addr + ")", e);
			return addr;
		}
	}

	private static Map<String, String> argsToMapPrefix(final String prefix, final String... args) {
		final HashMap<String, String> props = new HashMap<>();
		if (args == null || args.length == 0)
			return props;
		final Integer prefixLength = prefix == null || prefix.isEmpty() ? null : prefix.length();
		for (String arg : args) {
			if (arg == null || arg.isEmpty())
				continue;
			if (prefixLength != null && !arg.startsWith(prefix))
				continue;
			final String[] split = StringUtils.split(arg, "=");
			final int l = split.length - 1;
			if (l < 1)
				continue;
			final String value = split[l];
			for (int i = 0; i < l; i++) {
				final String key = prefixLength == null || i > 0 ? split[i] : split[i].substring(prefixLength);
				props.put(key, value);
			}
		}
		return props;
	}

	protected static Map<String, String> argsToMap(final String... args) throws IOException {
		final Map<String, String> props = argsToMapPrefix("--", args);

		// Load the QWAZR_PROPERTIES
		String propertyFile = props.get(QWAZR_PROPERTIES);
		if (propertyFile == null)
			propertyFile = System.getProperty(QWAZR_PROPERTIES, System.getenv(QWAZR_PROPERTIES));
		if (propertyFile != null) {
			final File propFile = new File(propertyFile);
			if (LOGGER.isInfoEnabled())
				LOGGER.info("Load QWAZR_PROPERTIES file: " + propFile.getAbsolutePath());
			final Properties properties = new Properties();
			try (final FileReader reader = new FileReader(propFile)) {
				properties.load(reader);
			}
			// Priority to program argument, we only put the value if the key is not present
			properties.forEach((key, value) -> props.putIfAbsent(key.toString(), value.toString()));
		}

		return props;
	}

	public static Builder of() {
		return new Builder();
	}

	public static class Builder {

		protected final Map<String, String> map;
		private final Set<String> masters;
		private final Set<String> groups;
		private final Set<String> etcFilters;
		private final Set<String> etcDirectories;

		protected Builder() {
			this.map = new HashMap<>();
			this.masters = new LinkedHashSet<>();
			this.groups = new LinkedHashSet<>();
			this.etcFilters = new LinkedHashSet<>();
			this.etcDirectories = new LinkedHashSet<>();
		}

		public Builder data(final File file) {
			if (file != null)
				map.put(QWAZR_DATA, file.getPath());
			return this;
		}

		public Builder temp(final File file) {
			if (file != null)
				map.put(QWAZR_TEMP, file.getPath());
			return this;
		}

		public Builder publicAddress(final String address) {
			if (address != null)
				map.put(PUBLIC_ADDR, address);
			return this;
		}

		public Builder listenAddress(final String address) {
			if (address != null)
				map.put(LISTEN_ADDR, address);
			return this;
		}

		public Builder master(final String master) {
			if (master != null)
				masters.add(master);
			return this;
		}

		public Builder group(final String group) {
			if (group != null)
				groups.add(group);
			return this;
		}

		public Builder etcFilter(final String etcFilter) {
			if (etcFilter != null)
				etcFilters.add(etcFilter);
			return this;
		}

		public Builder etcDirectory(final File etcDirectory) {
			if (etcDirectory != null)
				etcDirectories.add(etcDirectory.getAbsolutePath());
			return this;
		}

		public Builder webAppPort(Integer webappPort) {
			if (webappPort != null)
				map.put(WEBAPP_PORT, webappPort.toString());
			return this;
		}

		public Builder webServicePort(Integer webServicePort) {
			if (webServicePort != null)
				map.put(WEBSERVICE_PORT, webServicePort.toString());
			return this;
		}

		public Builder webAppRealm(String webAppRealm) {
			if (webAppRealm != null)
				map.put(WEBAPP_REALM, webAppRealm);
			return this;
		}

		public Builder webServiceRealm(String webServiceRealm) {
			if (webServiceRealm != null)
				map.put(WEBSERVICE_REALM, webServiceRealm);
			return this;
		}

		public Builder multicastAddress(String multicastAddress) {
			if (multicastAddress != null)
				map.put(MULTICAST_ADDR, multicastAddress);
			return this;
		}

		public Builder multicastPort(Integer multicastPort) {
			if (multicastPort != null)
				map.put(MULTICAST_PORT, multicastPort.toString());
			return this;
		}

		public Map<String, String> finalMap() {
			if (!masters.isEmpty())
				map.put(QWAZR_MASTERS, StringUtils.join(masters, ','));
			if (!groups.isEmpty())
				map.put(QWAZR_GROUPS, StringUtils.join(groups, ','));
			if (!etcFilters.isEmpty())
				map.put(QWAZR_ETC, StringUtils.join(etcFilters, ','));
			if (!etcDirectories.isEmpty())
				map.put(QWAZR_ETC_DIR, StringUtils.join(etcDirectories, File.pathSeparatorChar));
			return map;
		}

		public ServerConfiguration build() throws IOException {
			return new ServerConfiguration(finalMap());
		}

	}
}


