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

import com.qwazr.utils.http.HttpRequest;
import org.apache.http.client.methods.CloseableHttpResponse;
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
	public void test300SimpleServletWithFilter() throws IOException {
		try (final CloseableHttpResponse response = HttpRequest.Get("http://localhost:9090/test").execute()) {
			Assert.assertEquals(server.contextAttribute, EntityUtils.toString(response.getEntity()));
			Assert.assertEquals(SimpleFilter.TEST_VALUE, response.getFirstHeader(SimpleFilter.HEADER_NAME).getValue());
		}
	}

	@Test
	public void test301SimpleServletUrlMappingBis() throws IOException {
		try (final CloseableHttpResponse response = HttpRequest.Get("http://localhost:9090/test_bis").execute()) {
			Assert.assertEquals(server.contextAttribute, EntityUtils.toString(response.getEntity()));
		}
	}

	@Test
	public void test900stopServer() {
		server.stop();
	}
}