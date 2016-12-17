/**
 * s * Copyright 2016 Emmanuel Keller / QWAZR
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.Collection;

public class UdpServerThread extends Thread {

	private static final Logger LOGGER = LoggerFactory.getLogger(UdpServerThread.class);

	private final PacketListener[] packetListeners;

	private final InetSocketAddress socketAddress;
	private final InetAddress multicastGroupAddress;
	private final Integer multicastPort;

	private UdpServerThread(final InetSocketAddress socketAddress, final InetAddress multicastGroupAddress,
			final Integer multicastPort, final Collection<PacketListener> packetListeners) {
		super();
		setName("UDP Server");
		setDaemon(true);
		this.socketAddress = socketAddress;
		this.multicastGroupAddress = multicastGroupAddress;
		this.multicastPort = multicastPort;
		this.packetListeners = packetListeners.toArray(new PacketListener[packetListeners.size()]);
	}

	public UdpServerThread(final InetSocketAddress socketAddress, final Collection<PacketListener> packetListeners) {
		this(socketAddress, null, null, packetListeners);
	}

	public UdpServerThread(final String multicastGroupAddress, final int multicastPort,
			final Collection<PacketListener> packetListeners) throws UnknownHostException {
		this(null, InetAddress.getByName(multicastGroupAddress), multicastPort, packetListeners);
	}

	@Override
	public void run() {
		try (final DatagramSocket socket = multicastGroupAddress != null ?
				new MulticastSocket(multicastPort) :
				new DatagramSocket(socketAddress)) {
			if (multicastGroupAddress != null)
				((MulticastSocket) socket).joinGroup(multicastGroupAddress);
			if (LOGGER.isInfoEnabled())
				LOGGER.info("UDP Server started: " + socket.getLocalSocketAddress());
			for (; ; ) {
				final byte[] dataBuffer = new byte[65536];
				final DatagramPacket datagramPacket = new DatagramPacket(dataBuffer, dataBuffer.length);
				socket.receive(datagramPacket);
				for (PacketListener packetListener : packetListeners) {
					try {
						packetListener.acceptPacket(datagramPacket);
					} catch (Exception e) {
						LOGGER.warn(e.getMessage(), e);
					}
				}
			}
		} catch (IOException e) {
			throw new RuntimeException("Cannot start the server " + socketAddress, e);
		} finally {
			if (LOGGER.isInfoEnabled())
				LOGGER.info("UDP Server exit: " + socketAddress);
		}
	}

	/**
	 * Start or restart the thread if it is stopped
	 */
	public synchronized void checkStarted() throws UnknownHostException {
		if (isAlive())
			return;
		this.start();
	}

	public void shutdown() {
		if (isInterrupted())
			return;
		this.interrupt();
	}

	public interface PacketListener {

		void acceptPacket(final DatagramPacket packet) throws Exception;
	}
}
