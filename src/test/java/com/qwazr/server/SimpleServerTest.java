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
import com.qwazr.utils.json.JsonMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import javax.management.JMException;
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
		Assert.assertNotNull(server.getServer());
	}

	@Test
	public void test200startServer() throws ReflectiveOperationException, JMException, ServletException, IOException {
		server.start();
		Assert.assertNotNull(server.contextAttribute);
		Assert.assertEquals(200, HttpRequest.Get("http://localhost:9091/").execute().getStatusLine().getStatusCode());
		Assert.assertEquals(404, HttpRequest.Get("http://localhost:9091/sdflksjflskdfj")
				.execute()
				.getStatusLine()
				.getStatusCode());
	}

	@Test
	public void test250welcomeStatus() throws IOException {
		final WelcomeStatus welcomeStatus = JsonMapper.MAPPER.readValue(HttpRequest.Get("http://localhost:9091/")
				.execute()
				.getEntity()
				.getContent(), WelcomeStatus.class);
		Assert.assertNotNull(welcomeStatus);
		Assert.assertNotNull(welcomeStatus.webapp_endpoints);
		Assert.assertNotNull(welcomeStatus.webservice_endpoints);
		Assert.assertEquals(1, welcomeStatus.webapp_endpoints.size());
		Assert.assertEquals(2, welcomeStatus.webservice_endpoints.size());
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
	public void test400LoadedService() throws IOException {
		try (final CloseableHttpResponse response = HttpRequest.Get("http://localhost:9091/loaded").execute()) {
			Assert.assertEquals(LoadedService.TEXT, EntityUtils.toString(response.getEntity()));
		}
	}

	@Test
	public void test404() throws IOException {
		try (final CloseableHttpResponse response = HttpRequest.Get("http://localhost:9091/sd404flsfjskdfj")
				.execute()) {
			Assert.assertEquals(404, response.getStatusLine().getStatusCode());
		}
	}

	@Test
	public void test900stopServer() {
		server.stop();
	}
}
