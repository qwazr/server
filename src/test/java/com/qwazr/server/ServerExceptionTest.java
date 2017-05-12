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

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.util.function.Function;

public class ServerExceptionTest {

	static class Tester {

		final String message;
		final String causeMessage;
		final Exception cause;
		final int statusCode;

		Tester(Response.Status status) {
			message = status.getReasonPhrase();
			statusCode = status.getStatusCode();
			causeMessage = null;
			cause = null;
		}

		Tester(Response.Status status, boolean withMessage, boolean withCause) {
			message = withMessage ? RandomStringUtils.randomAlphanumeric(10) : null;
			statusCode = status.getStatusCode();
			if (withCause) {
				causeMessage = RandomStringUtils.randomAlphanumeric(10);
				cause = new Exception(causeMessage);
			} else {
				causeMessage = null;
				cause = null;
			}
		}

		Tester check(Function<Tester, ServerException> exception) {
			final ServerException e = exception.apply(this);
			Assert.assertNotNull(e);
			Assert.assertEquals(statusCode, e.getStatusCode());
			if (causeMessage == null || message != null)
				Assert.assertEquals(message, e.getMessage());
			Assert.assertEquals(cause, e.getCause());
			if (causeMessage != null) {
				Assert.assertNotNull(e.getCause());
				Assert.assertEquals(causeMessage, e.getCause().getMessage());
				if (message == null)
					Assert.assertEquals(causeMessage, e.getMessage());
			}
			return this;
		}

	}

	@Test
	public void withStatusWithMessage() {
		new Tester(Response.Status.CONFLICT, true, false).check(
				t -> new ServerException(Response.Status.CONFLICT, t.message))
				.check(t -> new ServerException(Response.Status.CONFLICT, () -> t.message));
	}

	@Test
	public void withStatusWithMessageWithCause() {
		new Tester(Response.Status.INTERNAL_SERVER_ERROR, true, true).check(
				t -> new ServerException(t.message, t.cause)).check(t -> new ServerException(() -> t.message, t.cause));
	}

	@Test
	public void withMessage() {
		new Tester(Response.Status.INTERNAL_SERVER_ERROR, true, false).check(t -> new ServerException(t.message))
				.check(t -> new ServerException(() -> t.message));
	}

	@Test
	public void withStatus() {
		new Tester(Response.Status.CONFLICT).check(t -> new ServerException(Response.Status.CONFLICT));
	}

	@Test
	public void withCause() {
		new Tester(Response.Status.INTERNAL_SERVER_ERROR, false, true).check(t -> new ServerException(t.cause));
	}

	@Test
	public void withErronousMessageSupplier() {
		Exception e = null;
		ServerException se = new ServerException(() -> e.getMessage());
		Assert.assertEquals("Cannot build error message:  java.lang.NullPointerException", se.getMessage());
	}
}
