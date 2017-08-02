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

import com.qwazr.server.response.JsonExceptionReponse;
import com.qwazr.utils.StringUtils;
import org.apache.http.client.HttpResponseException;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerException extends RuntimeException {

	/**
	 *
	 */
	private static final long serialVersionUID = -6102827990391082335L;

	private final int statusCode;
	private final String message;

	private final static int INTERNAL_SERVER_ERROR = Status.INTERNAL_SERVER_ERROR.getStatusCode();

	public ServerException(final Status status, String message, final Exception cause) {
		super(message, cause);
		if (status == null && cause != null) {
			if (cause instanceof WebApplicationException) {
				this.statusCode = ((WebApplicationException) cause).getResponse().getStatus();
			} else if (cause instanceof HttpResponseException) {
				this.statusCode = ((HttpResponseException) cause).getStatusCode();
			} else
				this.statusCode = INTERNAL_SERVER_ERROR;
		} else
			this.statusCode = status != null ? status.getStatusCode() : INTERNAL_SERVER_ERROR;
		if (StringUtils.isEmpty(message)) {
			if (cause != null)
				message = cause.getMessage();
			if (StringUtils.isEmpty(message) && status != null)
				message = status.getReasonPhrase();
		}
		this.message = message;
	}

	final static String SAFE_MESSAGE_ERROR = "Cannot build error message: ";

	private static String getSafeMessage(Supplier<String> message) {
		try {
			return message == null ? null : message.get();
		} catch (RuntimeException e) {
			return SAFE_MESSAGE_ERROR + " " + e;
		}
	}

	public ServerException(final Status status, final Supplier<String> message, final Exception cause) {
		this(status, message == null ? null : getSafeMessage(message), cause);
	}

	public ServerException(final Status status) {
		this(status, status.getReasonPhrase(), null);
	}

	public ServerException(final Status status, final String message) {
		this(status, message, null);
	}

	public ServerException(final Status status, final Supplier<String> message) {
		this(status, message, null);
	}

	public ServerException(final String message, final Exception cause) {
		this(null, message, cause);
	}

	public ServerException(final Supplier<String> message, final Exception cause) {
		this(null, message, cause);
	}

	public ServerException(final String message) {
		this(null, message, null);
	}

	public ServerException(final Supplier<String> message) {
		this(null, message, null);
	}

	public ServerException(final Exception cause) {
		this(null, (String) null, cause);
	}

	public int getStatusCode() {
		return statusCode;
	}

	final public ServerException warnIfCause(final Logger logger) {
		final Throwable cause = getCause();
		if (cause == null)
			return this;
		logger.log(Level.WARNING, cause, this::getMessage);
		return this;
	}

	final public ServerException errorIfCause(final Logger logger) {
		final Throwable cause = getCause();
		if (cause == null)
			return this;
		logger.log(Level.SEVERE, cause, this::getMessage);
		return this;
	}

	@Override
	final public String getMessage() {
		if (message != null)
			return message;
		return super.getMessage();
	}

	public WebApplicationException getTextException() {
		return new WebApplicationException(this, Response.status(statusCode)
				.type(MediaType.TEXT_PLAIN)
				.entity(message == null ? StringUtils.EMPTY : message)
				.build());
	}

	public WebApplicationException getJsonException(boolean allowStackTrace) {
		return new WebApplicationException(this, JsonExceptionReponse.of().status(statusCode).exception(this,
				allowStackTrace).message(message).build().toResponse());
	}

	public static ServerException getServerException(final Exception e) {
		if (e instanceof ServerException)
			return (ServerException) e;
		if (e instanceof WebApplicationException) {
			final Throwable cause = e.getCause();
			if (cause != null && (cause instanceof ServerException))
				return (ServerException) cause;
		}
		return new ServerException(e);
	}

	private static WebApplicationException checkCompatible(final Exception e, final MediaType expectedType) {
		if (!(e instanceof WebApplicationException))
			return null;
		final WebApplicationException wae = (WebApplicationException) e;
		final Response response = wae.getResponse();
		if (response == null)
			return null;
		if (!response.hasEntity())
			return null;
		final MediaType mediaType = response.getMediaType();
		if (mediaType == null)
			return null;
		if (!expectedType.isCompatible(mediaType))
			return null;
		return wae;
	}

	public static WebApplicationException getTextException(final Logger logger, final Exception e) {
		final WebApplicationException wae = checkCompatible(e, MediaType.TEXT_PLAIN_TYPE);
		if (wae != null)
			return wae;
		return getServerException(e).warnIfCause(logger).getTextException();
	}

	public static WebApplicationException getJsonException(final Logger logger, final Exception e) {
		final WebApplicationException wae = checkCompatible(e, MediaType.APPLICATION_JSON_TYPE);
		if (wae != null)
			return wae;
		return getServerException(e).warnIfCause(logger).getJsonException(logger == null);
	}

}
