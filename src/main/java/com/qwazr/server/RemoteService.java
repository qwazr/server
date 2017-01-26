/**
 * Copyright 2016 Emmanuel Keller / QWAZR
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.qwazr.utils.LinkUtils;
import com.qwazr.utils.StringUtils;
import com.qwazr.utils.UBuilder;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;

import javax.ws.rs.core.MultivaluedMap;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RemoteService {

	final public static String TIMEOUT_PARAMETER = "timeout";

	/**
	 * The protocol. Should be "http" or "https"
	 */
	final public String scheme;

	/**
	 * The hostname of the remote server. Localhost by default.
	 */
	final public String host;

	/**
	 * The root path of the service
	 */
	final public String path;

	/**
	 * the TCP port (9091 by default)
	 */
	final public Integer port;

	/**
	 * the default timeout (milliseconds)
	 */
	final public Integer timeout;

	/**
	 * the (optional) login (Basic HTTP authentication)
	 */
	final public String username;

	/**
	 * the (optional) password (Basic HTTP authentication)
	 */
	final public String password;

	@JsonIgnore
	final public String serverAddress; // {scheme}://{host}:{port}

	@JsonIgnore
	final public String serviceAddress; // {scheme}://{host}:{port}:{path}

	public RemoteService() {
		scheme = null;
		host = null;
		port = null;
		path = null;
		timeout = null;
		username = null;
		password = null;
		serverAddress = null;
		serviceAddress = null;
	}

	protected RemoteService(final Builder builder) {
		this.scheme = builder.scheme == null ? "http" : builder.scheme;
		this.host = builder.host == null ? "localhost" : builder.host;
		this.path = builder.getPathSegment(0);
		this.port = builder.port == null ? 9091 : builder.port == -1 ? 9091 : builder.port;
		this.timeout = builder.timeout;
		this.username = builder.username;
		this.password = builder.password;
		this.serverAddress = (this.scheme + "://" + this.host + ':' + this.port).intern();
		this.serviceAddress = (this.serverAddress + '/' + (this.path == null ? StringUtils.EMPTY : this.path)).intern();
	}

	@Override
	public boolean equals(final Object o) {
		if (o == null || !(o instanceof RemoteService))
			return false;
		final RemoteService rs = (RemoteService) o;
		if (!Objects.equals(scheme, rs.scheme))
			return false;
		if (!Objects.equals(host, rs.host))
			return false;
		if (!Objects.equals(port, rs.port))
			return false;
		if (!Objects.equals(path, rs.path))
			return false;
		if (!Objects.equals(timeout, rs.timeout))
			return false;
		if (!Objects.equals(username, rs.username))
			return false;
		if (!Objects.equals(password, rs.password))
			return false;
		if (!Objects.equals(serverAddress, rs.serverAddress))
			return false;
		if (!Objects.equals(serviceAddress, rs.serviceAddress))
			return false;
		return true;
	}

	@JsonIgnore
	final public Credentials getCredentials() {
		return username == null ? null : new UsernamePasswordCredentials(username, password);
	}

	public static Builder of() {
		return new Builder();
	}

	public static Builder of(final URI uri) {
		return new Builder(uri);
	}

	public static Builder of(final String url) throws URISyntaxException {
		return new Builder(url);
	}

	public static class Builder {

		private final URI initialURI;

		private String scheme;
		private String host;
		private String[] pathSegments;
		private Integer port;
		private Integer timeout;
		private String username;
		private String password;
		private MultivaluedMap<String, String> queryParams;

		private Builder() {
			initialURI = null;
			scheme = null;
			host = null;
			pathSegments = null;
			port = null;
			timeout = null;
			username = null;
			password = null;
			queryParams = null;
		}

		private Builder(final URI uri) {
			initialURI = uri;
			setScheme(uri.getScheme());
			setHost(uri.getHost());
			setPath(uri.getPath());
			setPort(uri.getPort());
			setUserInfo(uri.getUserInfo());
			setQuery(uri.getQuery());
		}

		private Builder(final String url) throws URISyntaxException {
			this(new URI(url));
		}

		public URI getInitialURI() {
			return initialURI;
		}

		/**
		 * @param scheme The protocol. Should be "http" or "https"
		 * @return the current builder
		 */
		public Builder setScheme(final String scheme) {
			this.scheme = scheme == null ? "http" : scheme;
			return this;
		}

		/**
		 * @param host The hostname of the remote server. "localhost" by default.
		 * @return the current builder
		 */
		public Builder setHost(final String host) {
			this.host = host == null ? "localhost" : host;
			return this;
		}

		/**
		 * @param path The root path of the service
		 * @return the current builder
		 */
		public Builder setPath(final String path) {
			pathSegments = path == null ? null : StringUtils.split(path, '/');
			return this;
		}

		public String getPathSegment(final int pos) {
			if (pathSegments == null)
				return null;
			return pathSegments.length > pos ? pathSegments[pos] : null;
		}

		/**
		 * @param port The TCP port (9091 by default)
		 * @return the current builder
		 */
		public Builder setPort(final Integer port) {
			this.port = port == null ? 9091 : port == -1 ? 9091 : port;
			return this;
		}

		/**
		 * @param userInfo The username and password in the form {username}:{password}
		 * @return the current builder
		 */
		public Builder setUserInfo(final String userInfo) {
			final String[] s = userInfo != null ? StringUtils.split(userInfo, ':') : null;
			if (s != null) {
				this.username = s.length > 0 ? s[0] : null;
				this.password = s.length > 1 ? s[1] : null;
			} else {
				this.username = null;
				this.password = null;
			}
			return this;
		}

		/**
		 * @param username The (optional) login (Basic HTTP authentication)
		 * @return the current builder
		 */
		public Builder setUsername(final String username) {
			this.username = username;
			return this;
		}

		/**
		 * @param password The (optional) password (Basic HTTP authentication)
		 * @return the current builder
		 */
		public Builder setPassword(final String password) {
			this.password = password;
			return this;
		}

		/**
		 * @param timeout The default timeout (milliseconds)
		 * @return the current builder
		 */
		public Builder setTimeout(final Integer timeout) {
			this.timeout = timeout;
			return this;
		}

		/**
		 * Set the parameters by extracting the query parameters
		 *
		 * @param query the query string
		 * @return
		 */
		public Builder setQuery(final String query) {
			queryParams = LinkUtils.getQueryParameters(query);
			if (queryParams == null)
				return this;
			final String s = queryParams.getFirst(TIMEOUT_PARAMETER);
			if (s != null)
				setTimeout(Integer.parseInt(s));
			return this;
		}

		/**
		 * Return the first query value if any
		 *
		 * @param param the name of the parameter
		 * @return the value or null
		 */
		public String getQueryParam(final String param) {
			return queryParams == null ? null : queryParams.getFirst(param);
		}

		/**
		 * Return the query values or null if the value is not mapped
		 *
		 * @param param the name of the parameter
		 * @return a value list or null
		 */
		public List<String> getQueryParams(final String param) {
			return queryParams == null ? null : queryParams.get(param);
		}

		/**
		 * @return a new RemoteService
		 */
		public RemoteService build() {
			return new RemoteService(this);
		}

	}

	/**
	 * Build a list of Builder filled with an array of URL.
	 * The form of the URL should be:
	 * {protocol}://{username:password@}{host}:{port}/{service_path}?timeout={timeout}
	 *
	 * @param remoteServiceURLs
	 * @return an list of RemoteService
	 * @throws URISyntaxException
	 */
	public static List<Builder> builders(final String... remoteServiceURLs) throws URISyntaxException {

		if (remoteServiceURLs == null || remoteServiceURLs.length == 0)
			return null;

		final List<Builder> builderList = new ArrayList<>();
		for (String url : remoteServiceURLs)
			if (url != null && !url.isEmpty())
				builderList.add(new Builder(url));

		return builderList.isEmpty() ? null : builderList;
	}

	public static List<Builder> builders(final Collection<String> remoteServiceURLs) throws URISyntaxException {

		if (remoteServiceURLs == null || remoteServiceURLs.isEmpty())
			return null;

		final List<Builder> builderList = new ArrayList<>();
		for (String url : remoteServiceURLs)
			if (url != null && !url.isEmpty())
				builderList.add(new Builder(url));

		return builderList.isEmpty() ? null : builderList;
	}

	private static RemoteService[] fromBuilders(final Collection<Builder> builders) {
		if (builders == null || builders.isEmpty())
			return null;
		// Build the array
		int i = 0;
		RemoteService[] remotes = new RemoteService[builders.size()];
		for (Builder builder : builders)
			remotes[i++] = new RemoteService(builder);
		return remotes;
	}

	/**
	 * Build an array of RemoteService filled with an array of URL.
	 * The form of the URL should be:
	 * {protocol}://{username:password@}{host}:{port}/{service_path}?timeout={timeout}
	 *
	 * @param remoteServiceURLs
	 * @return
	 * @throws URISyntaxException
	 */
	public static RemoteService[] build(final String... remoteServiceURLs) throws URISyntaxException {
		return fromBuilders(builders(remoteServiceURLs));
	}

	/**
	 * Build an array of RemoteService filled with an array of URL.
	 * The form of the URL should be:
	 * {protocol}://{username:password@}{host}:{port}/{service_path}?timeout={timeout}
	 *
	 * @param remoteServiceURLs
	 * @return
	 * @throws URISyntaxException
	 */
	public static RemoteService[] build(final Collection<String> remoteServiceURLs) throws URISyntaxException {
		return fromBuilders(builders(remoteServiceURLs));
	}

	/**
	 * Helper for URL building. The URL is built by concatening the url
	 * parameters given in the constructor and an array of pathes.
	 *
	 * @param remote the remoteservice
	 * @param paths  An array of path
	 */
	public static UBuilder getNewUBuilder(final RemoteService remote, final String... paths) {
		final UBuilder builder = new UBuilder();
		StringBuilder sb = new StringBuilder();
		if (remote.path != null)
			sb.append(remote.path);
		if (paths != null)
			for (String path : paths)
				if (path != null)
					sb.append(path);
		builder.setScheme(remote.scheme == null ? "http" : remote.scheme)
				.setHost(remote.host == null ? "localhost" : remote.host)
				.setPort(remote.port == null ? 9091 : remote.port);
		if (sb.length() > 0)
			builder.setPath(sb.toString());
		return builder;
	}
}