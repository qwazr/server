/*
 * Copyright 2015-2017 Emmanuel Keller / QWAZR
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

import com.qwazr.utils.RandomArrayIterator;

import javax.ws.rs.WebApplicationException;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * This class represents a connection to a set of servers
 *
 * @param <T> The type of the class which handle the connection to one server
 */
public class MultiClient<T> implements Iterable<T> {

	private final ExecutorService executorService;
	private final T[] clients;

	/**
	 * Create a new multi client with given clients
	 *
	 * @param clients an array of client
	 */
	protected MultiClient(final T[] clients, final ExecutorService executorService) {
		this.clients = clients;
		this.executorService = executorService;
	}

	@Override
	final public Iterator<T> iterator() {
		return new RandomArrayIterator<>(clients);
	}

	protected <R> R firstRandomSuccess(final Function<T, R> action,
			final Function<WebApplicationException, Boolean> exceptionHandler,
			final Function<WebApplicationException, R> notFoundAction) {
		WebApplicationException exception = null;
		for (final T client : this) {
			try {
				final R result = action.apply(client);
				if (result != null)
					return result;
			} catch (WebApplicationException e) {
				if (exceptionHandler == null || !exceptionHandler.apply(e))
					exception = e;
			}
		}
		return notFoundAction == null ? null : notFoundAction.apply(exception);
	}

	protected <R> void forEachParallel(final Function<T, R> action, final Consumer<R> result,
			final Function<WebApplicationException, Boolean> exceptionHandler,
			final Function<WebApplicationException, WebApplicationException> endException) {

		WebApplicationException exception = null;

		// Start the parallel threads
		final Future<R>[] futures = new Future[clients == null ? 0 : clients.length];
		int i = 0;
		for (final T client : this)
			futures[i++] = executorService.submit(() -> action.apply(client));

		// Get the results
		for (Future<R> future : futures) {
			if (future == null)
				continue;
			try {
				final R r = future.get();
				result.accept(r);
			} catch (ExecutionException | InterruptedException e) {
				throw new WebApplicationException(e);
			} catch (WebApplicationException e) {
				if (exceptionHandler == null || !exceptionHandler.apply(e))
					exception = e;
			}
		}
		// At the end, if we had an exception, let's notify the caller
		if (endException != null)
			exception = endException.apply(exception);
		if (exception != null)
			throw exception;
	}

}
