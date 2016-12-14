/**
 * Copyright 2015-2016 Emmanuel Keller / QWAZR
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
 **/

package com.qwazr.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.qwazr.utils.RuntimeUtils;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WelcomeStatus {

	public final TitleVendorVersion implementation;
	public final TitleVendorVersion specification;
	public final TreeSet<String> endpoints;
	public final MemoryStatus memory;
	public final RuntimeStatus runtime;
	public final SortedMap<String, Object> properties;
	public final SortedMap<String, String> env;

	public WelcomeStatus(final Boolean showProperties, final Boolean showEnvVars) {
		final GenericServer server = GenericServer.getInstance();
		if (server != null) {
			endpoints = new TreeSet<>();
			server.forEachServicePath(endpoints::add);
		} else
			endpoints = null;
		final Package pkg = getClass().getPackage();
		implementation = new TitleVendorVersion(pkg.getImplementationTitle(), pkg.getImplementationVendor(),
				pkg.getImplementationVersion());
		specification = new TitleVendorVersion(pkg.getSpecificationTitle(), pkg.getSpecificationVendor(),
				pkg.getSpecificationVersion());
		memory = new MemoryStatus();
		runtime = new RuntimeStatus();
		if (showProperties != null && showProperties) {
			properties = new TreeMap<>();
			System.getProperties().forEach((key, value) -> properties.put(key.toString(), value));
		} else
			properties = null;
		if (showEnvVars != null && showEnvVars)
			env = new TreeMap<>(System.getenv());
		else
			env = null;
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public class TitleVendorVersion {

		public final String title;
		public final String vendor;
		public final String version;

		TitleVendorVersion(final String title, final String vendor, final String version) {
			this.title = title;
			this.vendor = vendor;
			this.version = version;
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public class MemoryStatus {

		public final Long free;
		public final Long total;
		public final Long max;

		MemoryStatus() {
			Runtime runtime = Runtime.getRuntime();
			free = runtime.freeMemory();
			total = runtime.totalMemory();
			max = runtime.maxMemory();
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public class RuntimeStatus {

		public final Integer activeThreads;
		public final Long openFiles;

		RuntimeStatus() {
			activeThreads = RuntimeUtils.getActiveThreadCount();
			openFiles = RuntimeUtils.getOpenFileCount();
		}
	}

}
