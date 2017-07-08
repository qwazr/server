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

import java.util.concurrent.atomic.AtomicInteger;

abstract class ClientExample {

	final AtomicInteger actionCounter = new AtomicInteger();
	final int id;

	ClientExample(int id) {
		this.id = id;
	}

	@Override
	public String toString() {
		return "ID " + id;
	}

	abstract Integer action();

	static class ErrorClient extends ClientExample {

		ErrorClient(int id) {
			super(id);
		}

		@Override
		public Integer action() {
			actionCounter.incrementAndGet();
			throw new RuntimeException("I failed");
		}
	}

	static class SuccessClient extends ClientExample {

		SuccessClient(int id) {
			super(id);
		}

		@Override
		public Integer action() {
			actionCounter.incrementAndGet();
			return id;
		}
	}

	static class TimeoutClient extends ClientExample {

		TimeoutClient(int id) {
			super(id);
		}

		@Override
		public Integer action() {
			actionCounter.incrementAndGet();
			try {
				Thread.sleep(120000);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			return id;
		}
	}
}