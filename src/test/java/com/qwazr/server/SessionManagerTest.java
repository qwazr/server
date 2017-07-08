/*
 * Copyright 2017 Emmanuel Keller / QWAZR
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
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.management.JMException;
import javax.servlet.ServletException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SessionManagerTest {

	private SimpleServer server1;
	private SimpleServer server2;

	static Path sessionDir;

	@Before
	public void setup() throws IOException {
		sessionDir = Files.createTempDirectory("sessionTest");
		httpClientContext = new HttpClientContext();
	}

	@After
	public void cleanup() {
		if (server1 != null)
			server1.stop();
		if (server2 != null)
			server2.stop();
	}

	private SimpleServer startNewServer()
			throws IOException, ReflectiveOperationException, ServletException, JMException {
		final SimpleServer server = new SimpleServer(new InFileSessionPersistenceManager(sessionDir));
		Assert.assertNotNull(server.getServer());
		Assert.assertTrue(Files.exists(sessionDir));
		server.start();
		return server;
	}

	private HttpClientContext httpClientContext;

	private void callServlet() throws IOException {
		final HttpRequest.Base<HttpGet> request = HttpRequest.Get("http://localhost:9090/test_bis");
		final CloseableHttpResponse response = request.execute(httpClientContext);
		Assert.assertEquals(200, response.getStatusLine().getStatusCode());
	}

	@Test
	public void test() throws ReflectiveOperationException, JMException, ServletException, IOException {

		server1 = startNewServer();
		callServlet();
		String firstSessionId = SimpleServlet.sessionId;
		Assert.assertNotNull(firstSessionId);
		server1.stop();
		server1 = null;

		server2 = startNewServer();
		callServlet();
		Assert.assertEquals(firstSessionId, SimpleServlet.lastSessionAttribute);
		server2.stop();
		server2 = null;
	}

}
