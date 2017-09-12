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
package com.qwazr.server.logs;

import com.qwazr.utils.StringUtils;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class AccessLogger {

	private final Logger logger;
	private final Level level;
	private final String logMessage;
	private final LogParam[] logParams;

	public AccessLogger(Logger logger, Level level, String logMessage, LogParam... logParams) {
		this.logger = logger;
		this.level = level;
		this.logMessage = logMessage;
		this.logParams = logParams;
	}

	void log(final LogContext context) {
		if (!logger.isLoggable(level))
			return;
		final Object[] parameters = new Object[logParams.length];
		int i = 0;
		for (final LogParam logParam : logParams) {
			final Object param = logParam.supplier.apply(context);
			parameters[i++] = param == null ? StringUtils.EMPTY : param;
		}
		logger.log(level, logMessage, parameters);
	}
}