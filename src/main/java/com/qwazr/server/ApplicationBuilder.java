/**
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
package com.qwazr.server;

import org.glassfish.jersey.server.ResourceConfig;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class ApplicationBuilder {

	final Set<String> applicationPaths = new LinkedHashSet<>();

	final Set<Class<?>> classes = new LinkedHashSet<>();

	final Set<Object> singletons = new LinkedHashSet<>();

	final Map<String, Object> properties = new LinkedHashMap<>();

	volatile ResourceConfig cache;

	public ApplicationBuilder(String... applicationPaths) {
		if (applicationPaths != null)
			Collections.addAll(this.applicationPaths, applicationPaths);
	}

	public ApplicationBuilder(Collection<String> applicationPaths) {
		if (applicationPaths != null)
			this.applicationPaths.addAll(applicationPaths);
	}

	public Collection<String> getApplicationPaths() {
		return Collections.unmodifiableCollection(applicationPaths);
	}

	public ApplicationBuilder classes(Class<?>... classes) {
		if (classes != null)
			Collections.addAll(this.classes, classes);
		cache = null;
		return this;
	}

	public ApplicationBuilder classes(Collection<Class<?>> classes) {
		if (classes != null)
			this.classes.addAll(classes);
		cache = null;
		return this;
	}

	public ApplicationBuilder singletons(Object... singletons) {
		if (singletons != null)
			Collections.addAll(this.singletons, singletons);
		cache = null;
		return this;
	}

	public ApplicationBuilder singletons(Collection<?> singletons) {
		if (singletons != null)
			this.singletons.addAll(singletons);
		cache = null;
		return this;
	}

	public ApplicationBuilder properties(Map<String, ?> properties) {
		if (properties != null)
			this.properties.putAll(properties);
		cache = null;
		return this;
	}

	void apply(ResourceConfig resourceConfig) {
		resourceConfig.registerClasses(classes);
		resourceConfig.registerInstances(singletons);
		resourceConfig.setProperties(properties);
	}

	ResourceConfig build() {
		if (cache != null)
			return cache;
		final ResourceConfig resourceConfig = new ResourceConfig();
		apply(resourceConfig);
		return cache = resourceConfig;
	}
}
