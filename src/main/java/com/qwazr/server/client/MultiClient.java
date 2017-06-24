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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.qwazr.utils.ExceptionUtils;
import com.qwazr.utils.LoggerUtils;
import com.qwazr.utils.RandomArrayIterator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class represents a connection to a set of servers
 *
 * @param <T> The type of the class which handle the connection to one server
 */
public class MultiClient<T> implements Iterable<T> {

	private final static Logger LOGGER = LoggerUtils.getLogger(MultiClient.class);

	private final T[] clients;

	/**
	 * Create a new multi client with given clients
	 *
	 * @param clients an array of client
	 */
	protected MultiClient(final T[] clients) {
		this.clients = clients;
	}

	@Override
	final public Iterator<T> iterator() {
		return new RandomArrayIterator<>(clients);
	}

	protected <R> Result<R> firstRandomSuccess(final Function<T, R> action) {
		for (T client : this) {
			final ActionThread<R> actionThread = new ActionThread<>(client, action);
			final Result<R> result = actionThread.call();
			if (result.result != null && result.error == null)
				return result;
		}
		return null;
	}

	protected <R> Map<String, Result<R>> forEachParallel(final ExecutorService executorService, final long timeout,
			final TimeUnit unit, final Function<T, R> action) {
		final Map<Future<Result<R>>, ActionThread<R>> futures = new HashMap<>();
		// Start the parallel threads
		for (final T client : this) {
			final ActionThread<R> actionThread = new ActionThread<>(client, action);
			futures.put(executorService.submit(actionThread), actionThread);
		}
		// Get the results
		final Map<String, Result<R>> results = new HashMap<>();
		futures.forEach((future, actionThread) -> {
			Result<R> result;
			try {
				result = future.get(timeout, unit);
			} catch (InterruptedException | ExecutionException | TimeoutException e) {
				LOGGER.log(Level.SEVERE, e, () -> "Execution error on " + actionThread.clientId);
				result = actionThread.error(e);
			}
			results.put(actionThread.client.toString(), result);
		});
		return results;
	}

	private class ActionThread<R> implements Callable<Result<R>> {

		final long startTime;
		final Function<T, R> action;
		final T client;
		final String clientId;

		ActionThread(final T client, final Function<T, R> action) {
			this.startTime = System.nanoTime();
			this.client = client;
			this.clientId = client.toString();
			this.action = action;
		}

		private long getTime() {
			return (System.nanoTime() - startTime) / 1000000;
		}

		@Override
		public Result<R> call() {
			try {
				return new Result<>(clientId, getTime(), null, action.apply(client));
			} catch (Exception e) {
				return error(e);
			}
		}

		public Result<R> error(Throwable t) {
			return new Result<>(clientId, getTime(), t);
		}
	}

	public static class Result<E> {

		final public String client;
		final public long time;
		final public String error;
		final public E result;

		@JsonCreator
		Result(@JsonProperty("client") final String client, @JsonProperty("time") final long time,
				@JsonProperty("error") final String error, @JsonProperty("result") E result) {
			this.client = client;
			this.time = time;
			this.error = error;
			this.result = result;
		}

		Result(String client, long time, Throwable t) {
			this(client, time, ExceptionUtils.getMessage(t), null);
		}

	}

}
