package com.qwazr.server.logs;

import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;

public interface Handlers {

	class Console extends ConsoleHandler {
	}

	class File extends FileHandler {

		public File() throws IOException, SecurityException {
		}
	}
}
