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

import com.qwazr.utils.StringUtils;
import com.qwazr.utils.http.HttpResponseEntityException;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.entity.ContentType;

public class ResponseValidator {

	protected final ResponseValidator parent;

	public ResponseValidator() {
		parent = null;
	}

	public static ResponseValidator create() {
		return new ResponseValidator();
	}

	protected ResponseValidator(final ResponseValidator parent) {
		this.parent = parent;
	}

	public ResponseValidator status(final int... expectedCodes) {
		if (expectedCodes == null)
			return this;
		return new Status(this, expectedCodes);
	}

	public ResponseValidator content(final ContentType contentType) {
		if (contentType == null)
			return null;
		return new Content(this, contentType);
	}

	final public void checkResponse(final Header[] headers, final StatusLine statusLine, final HttpEntity entity)
			throws ClientProtocolException {
		check(headers, statusLine, entity);
	}

	protected void check(final Header[] headers, final StatusLine statusLine, final HttpEntity entity)
			throws ClientProtocolException {
		if (parent != null)
			parent.check(headers, statusLine, entity);
	}

	private class Status extends ResponseValidator {

		private final int[] expectedCodes;

		private Status(final ResponseValidator parent, int... expectedCodes) {
			super(parent);
			this.expectedCodes = expectedCodes;
		}

		@Override
		final protected void check(final Header[] headers, final StatusLine statusLine, final HttpEntity entity)
				throws ClientProtocolException {
			super.check(headers, statusLine, entity);
			if (statusLine == null)
				throw new ClientProtocolException("Response does not contains any status");
			if (expectedCodes == null)
				return;
			final int statusCode = statusLine.getStatusCode();
			for (int code : expectedCodes)
				if (code == statusCode)
					return;
			throw new HttpResponseEntityException(statusLine, entity,
					StringUtils.fastConcat("Unexpected HTTP status code: ", statusCode));
		}
	}

	private class Content extends ResponseValidator {

		private final ContentType expectedContentType;

		private Content(final ResponseValidator parent, final ContentType expectedContentType) {
			super(parent);
			this.expectedContentType = expectedContentType;
		}

		@Override
		final protected void check(final Header[] headers, final StatusLine statusLine, final HttpEntity entity)
				throws ClientProtocolException {
			super.check(headers, statusLine, entity);
			if (entity == null || expectedContentType == null)
				return;
			final ContentType contentType = ContentType.get(entity);
			if (contentType == null)
				throw new HttpResponseEntityException(statusLine, entity, "Unknown content type");
			if (!expectedContentType.getMimeType().equals(contentType.getMimeType()))
				throw new HttpResponseEntityException(statusLine, entity,
						StringUtils.fastConcat("Wrong content type: ", contentType.getMimeType()));
		}
	}
}
