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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

public class UdpServerThread extends Thread {

	private static final Logger LOGGER = LoggerFactory.getLogger(UdpServerThread.class);

	private final PacketListener[] packetListeners;

	private final AtomicBoolean isShutdown;

	private final InetSocketAddress socketAddress;
	private final InetAddress multicastGroupAddress;
	private final Integer multicastPort;
	private volatile DatagramSocket datagramSocket;

	private UdpServerThread(final InetSocketAddress socketAddress, final InetAddress multicastGroupAddress,
			final Integer multicastPort, final Collection<PacketListener> packetListeners) {
		super();
		setName("UDP Server");
		setDaemon(true);
		this.isShutdown = new AtomicBoolean(false);
		this.socketAddress = socketAddress;
		this.multicastGroupAddress = multicastGroupAddress;
		this.multicastPort = multicastPort;
		this.packetListeners = packetListeners.toArray(new PacketListener[packetListeners.size()]);
		this.datagramSocket = null;
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
			this.datagramSocket = socket;
			if (multicastGroupAddress != null)
				((MulticastSocket) socket).joinGroup(multicastGroupAddress);
			if (LOGGER.isInfoEnabled())
				LOGGER.info("UDP Server started: " + socket.getLocalSocketAddress());
			while (!isShutdown.get()) {
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
			if (!isShutdown.get())
				throw new RuntimeException("Error on UDP server " + socketAddress, e);
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

	public void shutdown() throws InterruptedException, IOException {
		isShutdown.set(true);
		if (datagramSocket != null) {
			if (multicastGroupAddress != null)
				((MulticastSocket) datagramSocket).leaveGroup(multicastGroupAddress);
			if (datagramSocket.isConnected())
				datagramSocket.disconnect();
			if (!datagramSocket.isClosed())
				datagramSocket.close();
			datagramSocket = null;
		}
	}

	public interface PacketListener {

		void acceptPacket(final DatagramPacket packet) throws Exception;
	}
}
