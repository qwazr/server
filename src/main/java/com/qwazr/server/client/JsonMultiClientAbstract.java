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
import com.qwazr.server.RemoteService;
import com.qwazr.utils.RandomArrayIterator;

import javax.ws.rs.WebApplicationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * This class represents a connection to a set of servers
 *
 * @param <T> The type of the class which handle the connection to one server
 */
public abstract class JsonMultiClientAbstract<T extends JsonClientInterface> implements Iterable<T> {

	private final T[] clientsArray;
	private final HashMap<String, T> clientsMap;

	/**
	 * Create a new multi client
	 *
	 * @param clientArray an array of client connection
	 * @param remotes     an array of RemoteService
	 */
	protected JsonMultiClientAbstract(T[] clientArray, RemoteService... remotes) {
		clientsArray = clientArray;
		clientsMap = new HashMap<>();
		for (RemoteService remote : remotes)
			clientsMap.put(remote.serverAddress, newClient(remote));
		clientsMap.values().toArray(clientsArray);
	}

	/**
	 * Create a new single client
	 *
	 * @param remote the RemoteService of the single client
	 * @return a new JsonClient
	 */
	protected abstract T newClient(RemoteService remote);

	@Override
	public Iterator<T> iterator() {
		return new RandomArrayIterator<>(clientsArray);
	}

	/**
	 * @return the number of clients
	 */
	public int size() {
		return clientsArray.length;
	}

	/**
	 * @param serverAddress the URL of the client
	 * @return the client which handle this URL
	 */
	public T getClientByUrl(String serverAddress) {
		return clientsMap.get(serverAddress);
	}

	/**
	 * @param pos the position of the client
	 * @return a json client
	 */
	protected T getClientByPos(Integer pos) {
		return clientsArray[pos];
	}

	protected Map<String, Result> forEachRandom(final Action<T> action) {
		final Map<String, Result> results = new LinkedHashMap<>();
		for (T client : this) {
			final ActionThread actionThread = new ActionThread(client, action);
			final Result result = actionThread.call();
			results.put(client.toString(), result);
			if (actionThread.exit)
				break;
		}
		return results;
	}

	protected Map<String, Result> forEachParallel(final ExecutorService executorService, final Action<T> action) {
		final Map<Future<Result>, ActionThread> futures = new HashMap<>();
		// Start the parallel threads
		for (T client : this) {
			final ActionThread actionThread = new ActionThread(client, action);
			futures.put(executorService.submit(actionThread), actionThread);
		}
		// Get the results
		final Map<String, Result> results = new HashMap<>();
		futures.forEach((future, actionThread) -> {
			Result result;
			try {
				result = future.get();
			} catch (InterruptedException | ExecutionException e) {
				result = actionThread.error(e);
			}
			results.put(actionThread.client.toString(), result);
		});
		return results;
	}

	private class ActionThread implements Callable<Result> {

		final long startTime;
		final Action<T> action;
		final T client;
		volatile boolean exit;

		ActionThread(final T client, final Action<T> action) {
			this.startTime = System.nanoTime();
			this.client = client;
			this.action = action;
		}

		private long getTime() {
			return (System.nanoTime() - startTime) / 1000000;
		}

		@Override
		public Result call() {
			try {
				exit = action.applyAndContinue(client);
				return new Result(getTime(), (String) null);
			} catch (WebApplicationException e) {
				return error(e);
			}
		}

		public Result error(Throwable t) {
			return new Result(getTime(), t);
		}
	}

	@FunctionalInterface
	public interface Action<T extends JsonClientInterface> {

		boolean applyAndContinue(T client);
	}

	public static class Result {

		final public long time;
		final public String error;

		@JsonCreator
		Result(@JsonProperty("time") final long time, @JsonProperty("error") final String error) {
			this.time = time;
			this.error = error;
		}

		Result(long time, Throwable t) {
			this(time, t.getMessage());
		}

	}

}
