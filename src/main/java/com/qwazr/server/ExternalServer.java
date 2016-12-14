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
package com.qwazr.server;

import com.qwazr.utils.process.ProcessUtils;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExternalServer implements Closeable {

	final Process process;

	private ExternalServer(final Class<? extends GenericServer> serverClass, final Map<String, String> env)
			throws Exception {
		process = ProcessUtils.java(serverClass, env);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> close()));
	}

	@Override
	public void close() {
		if (process != null && process.isAlive())
			process.destroy();
	}

	public static class Pool implements Closeable {

		private final List<ExternalServer> servers;

		public Pool() {
			servers = new ArrayList<>();
		}

		public void add(final Class<? extends GenericServer> serverClass, final Map<String, String> env)
				throws Exception {
			servers.add(new ExternalServer(serverClass, env));
		}

		@Override
		public void close() {
			servers.forEach(ExternalServer::close);
		}

	}

}
