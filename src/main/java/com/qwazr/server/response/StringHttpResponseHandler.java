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
 */

package com.qwazr.server.response;

import com.qwazr.utils.CharsetUtils;
import com.qwazr.utils.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

public class StringHttpResponseHandler extends AbstractHttpResponseHandler<String> {

	public StringHttpResponseHandler(final ResponseValidator validator) {
		super(validator);
	}

	@Override
	final public String handleResponse(final HttpResponse response) throws IOException {
		try {
			super.handleResponse(response);
			return entity == null ? null : EntityUtils.toString(entity, CharsetUtils.CharsetUTF8);
		} finally {
			IOUtils.close((CloseableHttpResponse) response);
		}
	}
}
