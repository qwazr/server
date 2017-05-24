/**
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
 **/

package com.qwazr.server;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.qwazr.utils.RuntimeUtils;

import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WelcomeStatus {

	public final TitleVendorVersion implementation;
	public final TitleVendorVersion specification;
	public final Set<String> webapp_endpoints;
	public final Set<String> webservice_endpoints;
	public final MemoryStatus memory;
	public final RuntimeStatus runtime;
	public final SortedMap<String, Object> properties;
	public final SortedMap<String, String> env;

	@JsonCreator
	WelcomeStatus(@JsonProperty("implementation") TitleVendorVersion implementation,
			@JsonProperty("specification") TitleVendorVersion specification,
			@JsonProperty("webapp_endpoints") Set<String> webapp_endpoints,
			@JsonProperty("webservice_endpoints") Set<String> webservice_endpoints,
			@JsonProperty("memory") MemoryStatus memory, @JsonProperty("runtime") RuntimeStatus runtime,
			@JsonProperty("properties") SortedMap<String, Object> properties,
			@JsonProperty("env") SortedMap<String, String> env) {
		this.implementation = implementation;
		this.specification = specification;
		this.webapp_endpoints = webapp_endpoints;
		this.webservice_endpoints = webservice_endpoints;
		this.memory = memory;
		this.runtime = runtime;
		this.properties = properties;
		this.env = env;
	}

	WelcomeStatus(final GenericServer server, final Boolean showProperties, final Boolean showEnvVars) {
		this.webapp_endpoints = server == null ? null : server.getWebAppEndPoints();
		this.webservice_endpoints = server == null ? null : server.getWebServiceEndPoints();
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
	public static class TitleVendorVersion {

		public final String title;
		public final String vendor;
		public final String version;

		@JsonCreator
		TitleVendorVersion(@JsonProperty("title") final String title, @JsonProperty("vendor") final String vendor,
				@JsonProperty("version") final String version) {
			this.title = title;
			this.vendor = vendor;
			this.version = version;
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class MemoryStatus {

		public final Long free;
		public final Long total;
		public final Long max;

		@JsonCreator
		MemoryStatus(@JsonProperty("free") Long free, @JsonProperty("total") Long total,
				@JsonProperty("max") Long max) {
			this.free = free;
			this.total = total;
			this.max = max;
		}

		MemoryStatus() {
			Runtime runtime = Runtime.getRuntime();
			free = runtime.freeMemory();
			total = runtime.totalMemory();
			max = runtime.maxMemory();
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class RuntimeStatus {

		public final Integer activeThreads;
		public final Long openFiles;

		@JsonCreator
		RuntimeStatus(@JsonProperty("activeThreads") Integer activeThreads, @JsonProperty("openFiles") Long openFiles) {
			this.activeThreads = activeThreads;
			this.openFiles = openFiles;
		}

		RuntimeStatus() {
			activeThreads = RuntimeUtils.getActiveThreadCount();
			openFiles = RuntimeUtils.getOpenFileCount();
		}
	}

}
