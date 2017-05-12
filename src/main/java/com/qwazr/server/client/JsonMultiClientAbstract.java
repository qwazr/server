/**
 * Copyright 2014-2016 Emmanuel Keller / QWAZR
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
import com.qwazr.server.RemoteService;

import java.util.HashMap;
import java.util.Iterator;

/**
 * This class represents a connection to a set of servers
 *
 * @param <T> The type of the class which handle the connection to one server
 */
public abstract class JsonMultiClientAbstract<T> implements Iterable<T> {

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
		return new RandomArrayIterator<T>(clientsArray);
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

}
