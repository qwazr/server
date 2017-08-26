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
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.client.HttpResponseException;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerException extends RuntimeException {

	private static final long serialVersionUID = -6102827990391082335L;

	private final int statusCode;

	ServerException(final int statusCode, String message, final Throwable cause) {
		super(message, cause);
		this.statusCode = statusCode;
	}

	public ServerException(final Response.Status status, final String message, final Throwable cause) {
		super(message == null ? status.getReasonPhrase() : message, cause);
		this.statusCode = status.getStatusCode();
	}

	public ServerException(final Response.Status status, final String message) {
		this(status, message, null);
	}

	public ServerException(final String message) {
		super(message);
		this.statusCode = 500;
	}

	public ServerException(final Response.Status status) {
		super(status.getReasonPhrase());
		this.statusCode = status.getStatusCode();
	}

	public int getStatusCode() {
		return statusCode;
	}

	final public ServerException warnIfCause(final Logger logger) {
		final Throwable cause = getCause();
		if (cause == null)
			return this;
		if (logger != null)
			logger.log(Level.WARNING, cause, this::getMessage);
		return this;
	}

	WebApplicationException getTextException() {
		final String message = getMessage();
		final Response response = Response.status(statusCode).type(MediaType.TEXT_PLAIN).entity(message).build();
		return new WebApplicationException(message, this, response);
	}

	WebApplicationException getJsonException(boolean withStackTrace) {
		final String message = getMessage();
		final Response response = JsonExceptionReponse.of()
				.status(statusCode)
				.exception(this, withStackTrace)
				.message(message)
				.build()
				.toResponse();
		return new WebApplicationException(message, this, response);
	}

	public static ServerException of(final Throwable throwable) {
		return of(throwable.getMessage(), throwable);
	}

	public static ServerException of(String message, final Throwable throwable) {
		if (throwable instanceof ServerException)
			return (ServerException) throwable;

		int status = 500;

		if (throwable instanceof WebApplicationException) {
			final Throwable cause = throwable.getCause();
			if (cause != null && (cause instanceof ServerException))
				return (ServerException) cause;
			final WebApplicationException e = (WebApplicationException) throwable;
			status = e.getResponse().getStatus();
		} else if (throwable instanceof HttpResponseException) {
			final HttpResponseException e = (HttpResponseException) throwable;
			status = e.getStatusCode();
		}
		if (StringUtils.isBlank(message)) {
			message = throwable.getMessage();
			if (StringUtils.isBlank(message))
				message = ExceptionUtils.getRootCauseMessage(throwable);
			if (StringUtils.isBlank(message))
				message = "Internal server error";
		}

		return new ServerException(status, message, throwable);
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
		return of(e).warnIfCause(logger).getTextException();
	}

	public static WebApplicationException getJsonException(final Logger logger, final Exception e) {
		final WebApplicationException wae = checkCompatible(e, MediaType.APPLICATION_JSON_TYPE);
		if (wae != null)
			return wae;
		return of(e).warnIfCause(logger).getJsonException(logger == null);
	}

}
