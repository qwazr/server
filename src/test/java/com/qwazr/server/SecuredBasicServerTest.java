/**
 * Copyright 2016-2017 Emmanuel Keller / QWAZR
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

import com.qwazr.utils.http.HttpRequest;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import javax.management.JMException;
import javax.servlet.ServletException;
import java.io.IOException;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SecuredBasicServerTest {

	private static SecuredBasicServer server;

	@Test
	public void test100createServer() throws IOException, ReflectiveOperationException {
		server = new SecuredBasicServer();
		Assert.assertTrue(server.getServer().getSingletonsMap().containsKey(WelcomeShutdownService.SERVICE_NAME));
	}

	@Test
	public void test200startServer() throws ReflectiveOperationException, JMException, ServletException, IOException {
		server.start();
		Assert.assertNotNull(server.contextAttribute);
	}

	@Test
	public void test300SimpleServlet() throws IOException {
		Assert.assertEquals(server.contextAttribute,
				EntityUtils.toString(HttpRequest.Get("http://localhost:9090/test").execute().getEntity()));
	}

	@Test
	public void test400SecuredNonAuthServlet() throws IOException {
		Assert.assertEquals(401,
				HttpRequest.Get("http://localhost:9090/secured").execute().getStatusLine().getStatusCode());
	}

	@Test
	public void test410SecuredBasicLoginSuccessfulServlet() throws IOException {
		SecuredServlet.check(HttpRequest.Get("http://localhost:9090/secured")
				.execute(getBasicAuthContext(server.basicUsername, server.basicPassword)), server.basicUsername)
				.close();
	}

	@Test
	public void test415SecuredBasicLoginFailureServlet() throws IOException {
		Assert.assertEquals(401, HttpRequest.Get("http://localhost:9090/secured")
				.execute(getBasicAuthContext(server.basicUsername, "--"))
				.getStatusLine()
				.getStatusCode());
	}

	private void checkAppAuth(String path) throws IOException {
		Assert.assertEquals(401, HttpRequest.Get(path + "auth/test").execute().getStatusLine().getStatusCode());
		Assert.assertEquals(401, HttpRequest.Get(path + "auth/test")
				.execute(getBasicAuthContext(server.basicUsername, "--"))
				.getStatusLine()
				.getStatusCode());
		Assert.assertEquals(403, HttpRequest.Get(path + "auth/wrong-role")
				.execute(getBasicAuthContext(server.basicUsername, server.basicPassword))
				.getStatusLine()
				.getStatusCode());
		Assert.assertEquals(200, HttpRequest.Get(path + "auth/test")
				.execute(getBasicAuthContext(server.basicUsername, server.basicPassword))
				.getStatusLine()
				.getStatusCode());
	}

	@Test
	public void test500AppJaxRsAuth() throws IOException {
		checkAppAuth("http://localhost:9090/jaxrs-app-auth/");
	}

	@Test
	public void test505AppJaxRsAuthSingletons() throws IOException {
		checkAppAuth("http://localhost:9090/jaxrs-app-auth-singletons/");
	}

	static HttpClientContext getAuthContext(AuthScheme authScheme, String username, String password) {
		HttpHost target = new HttpHost("localhost", 9090, "http");
		AuthCache authCache = new BasicAuthCache();
		authCache.put(target, authScheme);
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(new AuthScope(target.getHostName(), target.getPort()),
				new UsernamePasswordCredentials(username, password));
		HttpClientContext localContext = HttpClientContext.create();
		localContext.setCredentialsProvider(credsProvider);
		localContext.setAuthCache(authCache);
		return localContext;
	}

	HttpClientContext getBasicAuthContext(String username, String password) {
		return getAuthContext(new BasicScheme(), username, password);
	}

	@Test
	public void test900stopServer() {
		server.stop();
	}
}
