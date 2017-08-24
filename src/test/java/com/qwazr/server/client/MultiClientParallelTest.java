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

import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
		final MultiClient<ClientExample> multiClient = new MultiClient<>(clients, executor);
		final List<Integer> results = new ArrayList<>();
		final List<WebApplicationException> exceptions = new ArrayList<>();
		multiClient.forEachParallel(ClientExample::action, results::add, exceptions::add,
				e -> e != null ? e : results.isEmpty() ? new NotFoundException("test") : null);
		Assert.assertEquals(clients == null ? 0 : clients.length, results.size());
		if (clients == null || clients.length == 0) {
			Assert.assertTrue(results.isEmpty());
			return;
		}

		for (ClientExample client : clients) {
			Assert.assertEquals(1, client.actionCounter.get(), 0);
			if (client instanceof ClientExample.ErrorClient) {
				Assert.assertFalse(results.contains(client.id));
				Assert.assertNotNull(((ClientExample.ErrorClient) client).exception);
				Assert.assertTrue(exceptions.contains(((ClientExample.ErrorClient) client).exception));
			}
			if (client instanceof ClientExample.SuccessClient)
				Assert.assertTrue(results.contains(client.id));
		}

	}

	@Test
	public void parallelTests() {
		parallel(panel(Type.success));
		parallel(panel(Type.success, Type.success));

		parallel(panel(Type.success));
		parallel(panel(Type.success, Type.success));
		parallel(panel(Type.success, Type.success, Type.success));
	}

	@Test(expected = NotFoundException.class)
	public void parallelTestsNullClients() {
		parallel(null);
	}

	@Test(expected = NotFoundException.class)
	public void parallelTestsEmptyClients() {
		parallel(panel());
	}

	@Test(expected = WebApplicationException.class)
	public void parallelTestsError() {
		parallel(panel(Type.error));
	}

	@Test(expected = WebApplicationException.class)
	public void parallelTestsErrorError() {
		parallel(panel(Type.error, Type.error));
	}

	@Test(expected = WebApplicationException.class)
	public void parallelTestsErrorErrorError() {
		parallel(panel(Type.error, Type.error, Type.error));
	}

	@Test(expected = WebApplicationException.class)
	public void parallelTestsSuccessError() {
		parallel(panel(Type.success, Type.error));
	}

	@Test(expected = WebApplicationException.class)
	public void parallelTestsErrorSuccess() {
		parallel(panel(Type.error, Type.success));
	}

	@Test(expected = WebApplicationException.class)
	public void parallelTestsErrorSucessError() {
		parallel(panel(Type.error, Type.success, Type.error));
	}

	@Test(expected = WebApplicationException.class)
	public void parallelTestsSuccessErrorError() {
		parallel(panel(Type.success, Type.error, Type.error));
	}

	@Test(expected = WebApplicationException.class)
	public void parallelTestsErrorSuccessErrorSuccess() {
		parallel(panel(Type.error, Type.success, Type.error, Type.success));
	}

	@Test(expected = WebApplicationException.class)
	public void parallelTestsErrorErrorSuccessSuccess() {
		parallel(panel(Type.error, Type.error, Type.success, Type.success));
	}

	@Test(expected = WebApplicationException.class)
	public void parallelTestsSuccessSuccessErrorError() {
		parallel(panel(Type.success, Type.success, Type.error, Type.error));
	}

}
