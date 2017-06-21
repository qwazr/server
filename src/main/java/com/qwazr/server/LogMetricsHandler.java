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

import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import org.apache.commons.lang3.StringUtils;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LogMetricsHandler implements HttpHandler, ConnectorStatisticsMXBean {

	private final String address;
	private final int port;
	private final String name;
	private final HttpHandler next;
	private final Logger logger;
	public final AtomicInteger active;
	public final AtomicInteger maxActive;
	private final String logMessage;
	private final LogParameters[] logParameters;

	LogMetricsHandler(final HttpHandler next, final Logger logger, final String address, final int port,
			final String name, final String logMessage, LogParameters... logParameters) {
		this.next = next;
		this.logger = logger;
		this.active = new AtomicInteger();
		this.maxActive = new AtomicInteger();
		this.address = address;
		this.port = port;
		this.name = name;
		this.logMessage = logMessage == null ? StringUtils.EMPTY : logMessage;
		this.logParameters = Objects.requireNonNull(logParameters, "Missing logging parameters");
	}

	@Override
	final public void handleRequest(final HttpServerExchange exchange) throws Exception {
		if (logger != null)
			exchange.addExchangeCompleteListener(new CompletionListener());
		final int act = active.incrementAndGet();
		if (act > maxActive.get())
			maxActive.set(act);
		try {
			next.handleRequest(exchange);
		} finally {
			active.decrementAndGet();
		}
	}

	@Override
	final public int getActiveCount() {
		return active.get();
	}

	@Override
	final public int getMaxActiveCount() {
		return maxActive.get();
	}

	@Override
	final public String getAddress() {
		return this.address;
	}

	@Override
	final public int getPort() {
		return this.port;
	}

	@Override
	final public String getName() {
		return this.name;
	}

	@Override
	final public void reset() {
		maxActive.set(0);
	}

	private class CompletionListener implements ExchangeCompletionListener {

		private class LogContext {

			final HttpServerExchange exchange;
			final HeaderMap requestHeaders;

			final InetSocketAddress destinationAddress;
			final InetSocketAddress sourceAddress;

			final long nanoStartTime;
			final long nanoEndTime;
			final LocalDateTime logDateTime;

			private LogContext(HttpServerExchange exchange) {
				this.exchange = exchange;
				this.requestHeaders = exchange.getRequestHeaders();
				this.destinationAddress = exchange.getDestinationAddress();
				this.sourceAddress = exchange.getSourceAddress();
				this.nanoStartTime = exchange.getRequestStartTime();
				this.nanoEndTime = System.nanoTime();
				this.logDateTime = LocalDateTime.now();
			}

		}

		@Override
		final public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {
			try {
				final LogContext context = new LogContext(exchange);
				final Object[] parameters = new Object[logParameters.length];
				int i = 0;
				for (LogParameters logParameter : logParameters)
					parameters[i++] = logParameter.supplier.supply(context);
				logger.log(Level.INFO, logMessage, parameters);
			} finally {
				nextListener.proceed();
			}
		}
	}

	@FunctionalInterface
	private interface LogSupplier {
		String supply(CompletionListener.LogContext context);
	}

	private static String getUsername(final SecurityContext securityContext) {
		if (!securityContext.isAuthenticated())
			return null;
		final Account account = securityContext.getAuthenticatedAccount();
		if (account == null)
			return null;
		final Principal principal = account.getPrincipal();
		if (principal == null)
			return null;
		return principal.getName();
	}

	private static void span2(final StringBuilder sb, final int value) {
		if (value < 10)
			sb.append('0');
		sb.append(value);
	}

	private static void span3(final StringBuilder sb, final int value) {
		if (value < 10)
			sb.append("00");
		else if (value < 100)
			sb.append('0');
		sb.append(value);
	}

	private static String getDate(final LocalDateTime localDateTime) {
		final StringBuilder sb = new StringBuilder();
		sb.append(localDateTime.getYear());
		sb.append('-');
		span2(sb, localDateTime.getMonthValue());
		sb.append('-');
		span2(sb, localDateTime.getDayOfMonth());
		return sb.toString();
	}

	private static String getTime(final LocalDateTime localDateTime) {
		final StringBuilder sb = new StringBuilder();
		span2(sb, localDateTime.getHour());
		sb.append(':');
		span2(sb, localDateTime.getMinute());
		sb.append(':');
		span2(sb, localDateTime.getSecond());
		sb.append('.');
		span3(sb, localDateTime.getNano() / 1000000);
		return sb.toString();
	}

	public enum LogParameters {

		C_IP(0, "c-ip", ctx -> ctx.sourceAddress.getAddress().getHostAddress()),

		CS_HOST(1, "cs-host", ctx -> ctx.sourceAddress.getHostName()),

		CS_METHOD(2, "cs-method", ctx -> ctx.exchange.getRequestMethod().toString()),

		CS_URI_QUERY(3, "cs-uri-query", ctx -> ctx.exchange.getQueryString()),

		CS_URI_STEM(4, "cs-uri-stem", ctx -> ctx.exchange.getRequestPath()),

		CS_USER_AGENT(5, "cs-user-agent", ctx -> ctx.requestHeaders.getFirst("User-Agent")),

		CS_USERNAME(6, "cs-username", ctx -> getUsername(ctx.exchange.getSecurityContext())),

		CS_X_FORWARDED_FOR(7, "cs-x-forwarded-for", ctx -> ctx.requestHeaders.getFirst("X-Forwarded-For")),

		DATE(8, "date", ctx -> getDate(ctx.logDateTime)),

		CS_REFERER(9, "cs-referer", ctx -> ctx.requestHeaders.getFirst("Referer")),

		SC_STATUS(10, "sc-status", ctx -> Integer.toString(ctx.exchange.getStatusCode())),

		S_IP(11, "s-ip", ctx -> ctx.destinationAddress.getAddress().getHostAddress()),

		S_PORT(12, "s-port", ctx -> Integer.toString(ctx.destinationAddress.getPort())),

		TIME(13, "time", ctx -> getTime(ctx.logDateTime)),

		TIME_TAKEN(14, "time-taken", ctx -> Float.toString(
				ctx.nanoStartTime == -1 ? 0 : (ctx.nanoEndTime - ctx.nanoStartTime) / 1000000000)),

		CS_BYTES(15, "cs-bytes", ctx -> Long.toString(ctx.exchange.getRequestContentLength())),

		SC_BYTES(16, "sc-bytes", ctx -> Long.toString(ctx.exchange.getResponseBytesSent()));

		final int pos;

		final String name;

		final LogSupplier supplier;

		LogParameters(int pos, String name, LogSupplier supplier) {
			this.pos = pos;
			this.name = name;
			this.supplier = supplier;
		}

	}

}
