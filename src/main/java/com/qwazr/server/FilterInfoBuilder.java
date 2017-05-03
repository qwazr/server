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

import com.qwazr.utils.AnnotationsUtils;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.FilterMappingInfo;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;

class FilterInfoBuilder {

	static <T extends Filter> void filter(String filterName, Class<T> filterClass, GenericFactory<T> instanceFactory,
			GenericServer.Builder builder) {

		// WebServlet annotation
		final WebFilter webFilter = AnnotationsUtils.getFirstAnnotation(filterClass, WebFilter.class);
		if (webFilter != null)
			if (filterName == null || filterName.isEmpty())
				filterName = webFilter.filterName();

		final FilterInfo filterInfo = instanceFactory == null ?
				new FilterInfo(filterName, filterClass) :
				new FilterInfo(filterName, filterClass, instanceFactory);

		if (webFilter != null) {
			for (WebInitParam webInitParam : webFilter.initParams())
				filterInfo.addInitParam(webInitParam.name(), webInitParam.value());

			buildMappings(filterName, webFilter.dispatcherTypes(), FilterMappingInfo.MappingType.URL,
						  webFilter.urlPatterns(), builder);
			buildMappings(filterName, webFilter.dispatcherTypes(), FilterMappingInfo.MappingType.SERVLET,
						  webFilter.servletNames(), builder);
		}

		builder.filter(filterInfo);
	}

	private static void buildMappings(final String filterName, final DispatcherType[] dispatcherTypes,
			final FilterMappingInfo.MappingType mappingType, final String[] mappings,
			final GenericServer.Builder builder) {
		for (String mapping : mappings) {
			for (DispatcherType dispatcher : dispatcherTypes) {
				final FilterMappingInfo filterMappingInfo =
						new FilterMappingInfo(filterName, mappingType, mapping, dispatcher);
				builder.filterMapping(filterMappingInfo);
			}
		}
	}
}
