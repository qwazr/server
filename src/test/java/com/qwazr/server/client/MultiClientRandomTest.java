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

import org.junit.Assert;
import org.junit.Test;

public class MultiClientRandomTest extends MultiClientTest {

	MultiClient.Result<Integer> firstRandom(ClientExample[] clients) {
		final MultiClient<ClientExample> multiClient = new MultiClient<>(clients);
		return multiClient.firstRandomSuccess(ClientExample::action);
	}

	void firstRandomTest(ClientExample[] clients) {

		Assert.assertNotNull(clients);
		Assert.assertTrue(clients.length > 0);

		final MultiClient.Result<Integer> result = firstRandom(clients);
		Assert.assertNotNull(result);

		ClientExample clientFound = null;
		int successCount = 0;
		for (ClientExample client : clients) {
			if (client.id == result.result)
				clientFound = client;
			if (client.actionCounter.get() > 1)
				Assert.fail("Wrong counter: " + client.actionCounter.get());
			if (client.actionCounter.get() == 1 && client instanceof ClientExample.SuccessClient)
				successCount++;

		}
		Assert.assertNotNull(clientFound);
		Assert.assertEquals(1, clientFound.actionCounter.get(), 0);
		Assert.assertEquals(ClientExample.SuccessClient.class, clientFound.getClass());
		Assert.assertEquals(1, successCount, 0);
	}

	@Test
	public void firstRandomTestsWithResult() {
		firstRandomTest(panel(Type.success));
		firstRandomTest(panel(Type.success, Type.success));
		firstRandomTest(panel(Type.success, Type.error));
		firstRandomTest(panel(Type.error, Type.success));
		firstRandomTest(panel(Type.error, Type.success, Type.error));
		firstRandomTest(panel(Type.success, Type.error, Type.error));
		firstRandomTest(panel(Type.error, Type.success, Type.error, Type.success));
		firstRandomTest(panel(Type.error, Type.error, Type.success, Type.success));
		firstRandomTest(panel(Type.success, Type.success, Type.error, Type.error));
	}

	void firstRandomTestEmpty(ClientExample[] clients) {

		final MultiClient.Result<Integer> result = firstRandom(clients);
		Assert.assertNull(result);
		if (clients != null)
			for (ClientExample client : clients)
				Assert.assertEquals(1, client.actionCounter.get(), 0);
	}

	@Test
	public void firstRandomTestWithoutResult() {
		firstRandomTestEmpty(null);
		firstRandomTestEmpty(panel());
		firstRandomTestEmpty(panel(Type.error));
		firstRandomTestEmpty(panel(Type.error, Type.error));
		firstRandomTestEmpty(panel(Type.error, Type.error, Type.error));
	}

}
