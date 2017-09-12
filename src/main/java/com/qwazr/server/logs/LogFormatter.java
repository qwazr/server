package com.qwazr.server.logs;

import java.text.MessageFormat;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter {

	@Override
	public String format(LogRecord record) {
		return MessageFormat.format(record.getMessage(), record.getParameters());
	}
}
