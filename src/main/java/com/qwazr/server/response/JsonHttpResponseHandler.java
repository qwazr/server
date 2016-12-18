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
package com.qwazr.server.response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.qwazr.utils.IOUtils;
import com.qwazr.utils.json.JsonMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;

import java.io.IOException;

public class JsonHttpResponseHandler {

	public static class JsonTreeResponse extends AbstractHttpResponseHandler<JsonNode> {

		public JsonTreeResponse(final ResponseValidator validator) {
			super(validator);
		}

		@Override
		final public JsonNode handleResponse(final HttpResponse response) throws IOException {
			try {
				super.handleResponse(response);
				return entity == null ? null : JsonMapper.MAPPER.readTree(entity.getContent());
			} finally {
				IOUtils.close((CloseableHttpResponse) response);
			}
		}
	}

	public static class JsonValueResponse<T> extends AbstractHttpResponseHandler<T> {

		private final Class<T> jsonClass;

		public JsonValueResponse(final Class<T> jsonClass, final ResponseValidator validator) {
			super(validator);
			this.jsonClass = jsonClass;
		}

		@Override
		final public T handleResponse(final HttpResponse response) throws IOException {
			try {
				super.handleResponse(response);
				return entity == null ? null : JsonMapper.MAPPER.readValue(entity.getContent(), jsonClass);
			} finally {
				IOUtils.close((CloseableHttpResponse) response);
			}
		}
	}

	public static class JsonValueTypeRefResponse<T> extends AbstractHttpResponseHandler<T> {

		private final TypeReference<T> typeReference;

		public JsonValueTypeRefResponse(final TypeReference<T> typeReference, final ResponseValidator validator) {
			super(validator);
			this.typeReference = typeReference;
		}

		@Override
		final public T handleResponse(final HttpResponse response) throws IOException {
			try {
				super.handleResponse(response);
				return entity == null ? null : JsonMapper.MAPPER.readValue(entity.getContent(), typeReference);
			} finally {
				IOUtils.close((CloseableHttpResponse) response);
			}
		}
	}
}
