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
package com.qwazr.server.test;

import com.qwazr.server.WelcomeShutdownService;
import com.qwazr.utils.http.HttpRequest;
import org.apache.http.HttpHost;
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

import javax.management.MBeanException;
import javax.management.OperationsException;
import javax.servlet.ServletException;
import java.io.IOException;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SimpleServerTest {

	private static SimpleServer server;

	@Test
	public void test100createServer()
			throws IOException, ReflectiveOperationException, OperationsException, ServletException, MBeanException {
		server = new SimpleServer();
		Assert.assertTrue(server.getServer().getWebServiceNames().contains(WelcomeShutdownService.SERVICE_NAME));
	}

	@Test
	public void test200startServer()
			throws ReflectiveOperationException, OperationsException, MBeanException, ServletException, IOException {
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
		Assert.assertEquals(403,
				HttpRequest.Get("http://localhost:9090/secured").execute().getStatusLine().getStatusCode());
	}

	@Test
	public void test400SecuredLoginServlet() throws IOException {

		HttpHost target = new HttpHost("localhost", 9090, "http");
		// Create AuthCache instance
		AuthCache authCache = new BasicAuthCache();
		// Generate BASIC scheme object and add it to the local
		// auth cache
		BasicScheme basicAuth = new BasicScheme();
		authCache.put(target, basicAuth);

		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(new AuthScope(target.getHostName(), target.getPort()),
				new UsernamePasswordCredentials(server.username, server.password));
		HttpClientContext localContext = HttpClientContext.create();
		localContext.setCredentialsProvider(credsProvider);
		localContext.setAuthCache(authCache);

		Assert.assertEquals(200,
				HttpRequest.Get("http://localhost:9090/secured").execute(localContext).getStatusLine().getStatusCode());
	}

	@Test
	public void test900stopServer() {
		server.stop();
	}
}
