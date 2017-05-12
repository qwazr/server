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
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.DigestScheme;
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
public class SecuredDigestServerTest {

	private static SecuredDigestServer server;

	@Test
	public void test100createServer()
			throws IOException, ReflectiveOperationException, OperationsException, ServletException, MBeanException {
		server = new SecuredDigestServer();
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
		Assert.assertEquals(401,
				HttpRequest.Get("http://localhost:9090/secured").execute().getStatusLine().getStatusCode());
	}

	@Test
	public void test410SecuredLoginSuccessfulServlet() throws IOException {
		SecuredServlet.check(HttpRequest.Get("http://localhost:9090/secured")
				.execute(getDigestAuthContext(server.digestUsername, server.digestPassword)), server.digestUsername)
				.close();
	}

	@Test
	public void test415SecuredLoginFailureServlet() throws IOException {
		Assert.assertEquals(401, HttpRequest.Get("http://localhost:9090/secured")
				.execute(getDigestAuthContext(server.digestUsername, "--"))
				.getStatusLine()
				.getStatusCode());
	}

	HttpClientContext getDigestAuthContext(String username, String password) {
		DigestScheme digestAuth = new DigestScheme();
		digestAuth.overrideParamter("realm", server.realm);
		return SecuredBasicServerTest.getAuthContext(digestAuth, username, password);
	}

	@Test
	public void test900stopServer() {
		server.stop();
	}
}
