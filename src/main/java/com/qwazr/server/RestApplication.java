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
package com.qwazr.server;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.qwazr.utils.json.JacksonConfig;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import javax.servlet.ServletContext;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generic RestApplication
 */
public abstract class RestApplication extends Application {

	public static final Class<?>[] JSON_CLASSES =
			{ JacksonConfig.class, JacksonJsonProvider.class, JsonMappingExceptionMapper.class };

	@Context
	private ServletContext context;

	public static class WithoutAuth extends RestApplication {

		@Override
		public Set<Class<?>> getClasses() {
			final Set<Class<?>> classes = new LinkedHashSet<>();
			Collections.addAll(classes, JSON_CLASSES);
			return classes;
		}

	}

	public static class WithAuth extends WithoutAuth {

		public Set<Class<?>> getClasses() {
			final Set<Class<?>> classes = super.getClasses();
			classes.add(RolesAllowedDynamicFeature.class);
			return classes;
		}
	}

}
