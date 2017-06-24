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
package com.qwazr.server.client;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MultiClientParallelTest extends MultiClientTest {

	static ExecutorService executor;

	@Before
	public void setup() {
		executor = Executors.newCachedThreadPool();
	}

	@After
	public void cleanup() {
		executor.shutdown();
	}

	void parallel(ClientExample[] clients) {
		final MultiClient<ClientExample> multiClient = new MultiClient<>(clients);
		final Map<String, MultiClient.Result<Integer>> results = multiClient.forEachParallel(executor, 10,
				TimeUnit.SECONDS, ClientExample::action);
		Assert.assertNotNull(results);
		if (clients == null || clients.length == 0) {
			Assert.assertTrue(results.isEmpty());
			return;
		}
		final Map<String, ClientExample> clientMap = new HashMap<>();
		for (ClientExample client : clients) {
			Assert.assertEquals(1, client.actionCounter.get(), 0);
			clientMap.put(client.toString(), client);
		}
		Assert.assertEquals(results.size(), clients.length);
		results.forEach((id, result) -> {
			Assert.assertNotNull(result.client);
			Assert.assertEquals(id, result.client);
			final ClientExample client = clientMap.get(id);
			Assert.assertNotNull(client);
			if (client instanceof ClientExample.ErrorClient) {
				Assert.assertNotNull(result.error);
				Assert.assertNull(result.result);
			} else if (client instanceof ClientExample.SuccessClient) {
				Assert.assertNotNull(result.result);
				Assert.assertNull(result.error);
			} else if (client instanceof ClientExample.TimeoutClient) {
				Assert.assertNull(result.result);
				Assert.assertNotNull(result.error);
			} else
				Assert.fail("Unknown type: " + client.getClass());
			clientMap.remove(id);
		});
		Assert.assertTrue(clientMap.isEmpty());
	}

	@Test
	public void parallelTests() {
		parallel(panel(Type.success));
		parallel(panel(Type.success, Type.success));
		parallel(panel(Type.success, Type.error));
		parallel(panel(Type.error, Type.success));
		parallel(panel(Type.error, Type.success, Type.error));
		parallel(panel(Type.success, Type.error, Type.error));
		parallel(panel(Type.error, Type.success, Type.error, Type.success));
		parallel(panel(Type.error, Type.error, Type.success, Type.success));
		parallel(panel(Type.success, Type.success, Type.error, Type.error));

		parallel(null);
		parallel(panel());

		parallel(panel(Type.error));
		parallel(panel(Type.error, Type.error));
		parallel(panel(Type.error, Type.error, Type.error));

		parallel(panel(Type.success));
		parallel(panel(Type.success, Type.success));
		parallel(panel(Type.success, Type.success, Type.success));
	}

	@Test
	public void parallelTimeoutTest() {
		parallel(panel(Type.timeout));
		parallel(panel(Type.success, Type.timeout, Type.error));
	}

}
