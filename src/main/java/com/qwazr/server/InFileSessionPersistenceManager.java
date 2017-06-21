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

import com.qwazr.utils.LoggerUtils;
import com.qwazr.utils.SerializationUtils;
import io.undertow.servlet.api.SessionPersistenceManager;
import org.apache.commons.io.filefilter.FileFileFilter;

import java.io.EOFException;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InFileSessionPersistenceManager implements SessionPersistenceManager {

	private static final Logger LOGGER = LoggerUtils.getLogger(InFileSessionPersistenceManager.class);

	private final File sessionDir;

	public InFileSessionPersistenceManager(File sessionDir) {
		this.sessionDir = sessionDir;
	}

	@Override
	public void persistSessions(final String deploymentName, final Map<String, PersistentSession> sessionData) {
		if (sessionData == null)
			return;
		final File deploymentDir = new File(sessionDir, deploymentName);
		if (!deploymentDir.exists())
			deploymentDir.mkdir();
		if (!deploymentDir.exists() && !deploymentDir.isDirectory()) {
			LOGGER.warning(() -> "Cannot create the session directory " + deploymentDir + ": persistence aborted.");
			return;
		}
		sessionData.forEach(
				(sessionId, persistentSession) -> writeSession(deploymentDir, sessionId, persistentSession));
	}

	private void writeSession(final File deploymentDir, final String sessionId,
			final PersistentSession persistentSession) {
		final Date expDate = persistentSession.getExpiration();
		if (expDate == null)
			return; // No expiry date? no serialization
		final Map<String, Object> sessionData = persistentSession.getSessionData();
		if (sessionData == null || sessionData.isEmpty())
			return; // No attribute? no serialization
		File sessionFile = new File(deploymentDir, sessionId);
		try {
			try (final FileOutputStream fileOutputStream = new FileOutputStream(sessionFile)) {
				try (final ObjectOutputStream out = new ObjectOutputStream(fileOutputStream)) {
					out.writeLong(expDate.getTime()); // The date is stored as long
					sessionData.forEach((attribute, object) -> writeSessionAttribute(out, attribute, object));
				}
			}
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, e, () -> "Cannot save sessions in " + sessionFile + " " + e.getMessage());
		}
	}

	private void writeSessionAttribute(final ObjectOutputStream out, final String attribute, final Object object) {
		if (attribute == null || object == null)
			return;
		if (!(object instanceof Serializable))
			return;
		try {
			out.writeUTF(attribute); // Attribute name stored as string
		} catch (IOException e) {
			LOGGER.warning(() -> "Cannot write session attribute " + attribute + ": persistence aborted.");
			return; // The attribute cannot be written, we abort
		}
		try {
			out.writeObject(object);
			return; // The object was written, job done, we can exit
		} catch (IOException e) {
			LOGGER.warning(() -> "Cannot write session object " + object);
			try {
				out.writeObject(SerializationUtils.NullEmptyObject.INSTANCE);
			} catch (IOException e1) {
				LOGGER.warning(
						() -> "Cannot write NULL session object for attribute " + attribute + ": persistence aborted.");
			}
		}

	}

	@Override
	public Map<String, PersistentSession> loadSessionAttributes(final String deploymentName,
			final ClassLoader classLoader) {
		final File deploymentDir = new File(sessionDir, deploymentName);
		if (!deploymentDir.exists() || !deploymentDir.isDirectory())
			return null;
		File[] sessionFiles = deploymentDir.listFiles((FileFilter) FileFileFilter.FILE);
		if (sessionFiles == null || sessionFiles.length == 0)
			return null;
		final long time = System.currentTimeMillis();
		final Map<String, PersistentSession> finalMap = new HashMap<>();
		for (File sessionFile : sessionFiles) {
			PersistentSession persistentSession = readSession(sessionFile);
			if (persistentSession != null && persistentSession.getExpiration().getTime() > time)
				finalMap.put(sessionFile.getName(), persistentSession);
			sessionFile.delete();
		}
		return finalMap.isEmpty() ? null : finalMap;
	}

	private PersistentSession readSession(final File sessionFile) {
		try {
			try (final FileInputStream fileInputStream = new FileInputStream(sessionFile)) {
				try (final ObjectInputStream in = new ObjectInputStream(fileInputStream)) {
					final Date expDate = new Date(in.readLong());
					final HashMap<String, Object> sessionData = new HashMap<>();
					try {
						for (; ; )
							readSessionAttribute(in, sessionData);
					} catch (EOFException e) {
						;// Ok we reached the end of the file
					}
					return sessionData.isEmpty() ? null : new PersistentSession(expDate, sessionData);
				}
			}
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, e, () -> "Cannot load sessions from " + sessionFile);
			return null;
		}
	}

	private void readSessionAttribute(final ObjectInputStream in, final Map<String, Object> sessionData)
			throws IOException {
		final String attribute = in.readUTF();
		try {
			final Object object = in.readObject();
			if (!(object instanceof SerializationUtils.NullEmptyObject))
				sessionData.put(attribute, object);
		} catch (ClassNotFoundException | NotSerializableException e) {
			LOGGER.log(Level.WARNING, e, () -> "The attribute " + attribute + " cannot be deserialized");
		}
	}

	@Override
	public void clear(final String deploymentName) {
	}
}
