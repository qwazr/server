/*
 * Copyright 2015-2018 Emmanuel Keller / QWAZR
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

import com.qwazr.utils.RandomUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.management.JMException;
import javax.servlet.ServletException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class AsyncServiceTest {

	private static AsyncJaxRsServer server;

	@BeforeClass
	public static void setup() throws ServletException, IOException, JMException {
		server = new AsyncJaxRsServer();
		server.start();
	}

	@Test
	public void testAsync() throws ExecutionException, InterruptedException {
		Assert.assertTrue(true);
		Client client = ClientBuilder.newClient();
		final String randomTest = RandomUtils.alphanumeric(10);
		final String returnedString = client.target("http://localhost:9091/async")
				.queryParam("test", randomTest)
				.request()
				.rx()
				.get(String.class)
				.toCompletableFuture()
				.get();
		Assert.assertEquals(randomTest, returnedString);
	}

	@AfterClass
	public static void cleanup() {
		server.stop();
	}
}
