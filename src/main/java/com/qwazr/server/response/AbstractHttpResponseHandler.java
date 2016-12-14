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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;

import java.io.IOException;

public abstract class AbstractHttpResponseHandler<T> implements ResponseHandler<T> {

	private final ResponseValidator validator;
	protected HttpEntity entity;
	protected StatusLine statusLine;

	public AbstractHttpResponseHandler(final ResponseValidator validator) {
		this.validator = validator;
	}

	@Override
	public T handleResponse(final HttpResponse response) throws IOException {
		if (response == null)
			throw new ClientProtocolException("No response");
		entity = response.getEntity();
		statusLine = response.getStatusLine();
		if (validator != null)
			validator.check(statusLine, entity);
		return null;
	}

}
