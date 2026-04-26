package com.giovannimenzano.jblockchain.services.impl;

import com.giovannimenzano.jblockchain.services.IDiscoveryService;
import com.giovannimenzano.jblockchain.services.INetworkService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * P2P auto-discovery via UDP broadcast on the local network.
 *
 * On startup, the node broadcasts its public URL to a well-known UDP port.
 * A background listener picks up broadcasts from other nodes and registers
 * them as peers via {@link INetworkService#registerNode(String)}.
 *
 * This eliminates the need to manually configure seed nodes for LAN deployments.
 */
@Slf4j
@Service
public class UdpDiscoveryService implements IDiscoveryService {

	private static final String BROADCAST_PREFIX = "JBLOCKCHAIN:";

	@Value("${blockchain.network.discovery.enabled:true}")
	private boolean discoveryEnabled;

	@Value("${blockchain.network.discovery.port:8888}")
	private int discoveryPort;

	private final INetworkService networkService;
	private final ExecutorService listenerExecutor = Executors.newSingleThreadExecutor();
	private volatile boolean running = false;
	private DatagramSocket listenerSocket;

	public UdpDiscoveryService(INetworkService networkService) {
		this.networkService = networkService;
	}

	/**
	 * Starts the UDP listener and broadcasts this node's presence after the application is ready.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void onStartup() {
		if (!discoveryEnabled) {
			log.info("[Discovery] UDP discovery disabled.");
			return;
		}

		startListener();
		broadcastPresence();
	}

	/**
	 * Sends a UDP broadcast packet containing this node's URL to all hosts on the local network.
	 * Format: "JBLOCKCHAIN:<full-node-url>"
	 */
	@Override
	public void broadcastPresence() {
		String selfUrl = networkService.getLocalNodeUrl();
		String message = BROADCAST_PREFIX + selfUrl;
		byte[] data = message.getBytes(StandardCharsets.UTF_8);

		try (DatagramSocket socket = new DatagramSocket()) {
			socket.setBroadcast(true);
			DatagramPacket packet = new DatagramPacket(
				data, data.length,
				InetAddress.getByName("255.255.255.255"),
				discoveryPort
			);
			socket.send(packet);
			log.info("[Discovery] Broadcasted presence: {}", selfUrl);
		} catch (IOException e) {
			log.warn("[Discovery] Failed to broadcast presence: {}", e.getMessage());
		}
	}

	/**
	 * Starts a background thread that listens for UDP discovery packets from other nodes.
	 * When a valid JBLOCKCHAIN packet arrives, the sender URL is registered as a peer.
	 * Packets from self are silently ignored.
	 * A reciprocal broadcast is only sent when the peer is actually new (not already known),
	 * preventing broadcast storms between nodes that already know each other.
	 */
	private void startListener() {
		running = true;
		listenerExecutor.submit(() -> {
			try {
				listenerSocket = new DatagramSocket(null);
				listenerSocket.setReuseAddress(true);
				listenerSocket.bind(new InetSocketAddress(discoveryPort));
				log.info("[Discovery] Listening for peers on UDP port {}", discoveryPort);

				byte[] buffer = new byte[512];

				while (running) {
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
					try {
						listenerSocket.receive(packet);
						String received = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

						if (received.startsWith(BROADCAST_PREFIX)) {
							String peerUrl = received.substring(BROADCAST_PREFIX.length());
							String selfUrl = networkService.getLocalNodeUrl();
							if (!peerUrl.equals(selfUrl)) {
								boolean isNew = !networkService.getNodes().contains(peerUrl);
								networkService.registerNode(peerUrl);
								// Respond only if this peer is genuinely new to avoid broadcast storms
								if (isNew) {
									broadcastPresence();
								}
							}
						}
					} catch (SocketException e) {
						if (running) {
							log.warn("[Discovery] Socket error: {}", e.getMessage());
						}
						// If not running, socket was closed by shutdown - expected
					}
				}
			} catch (IOException e) {
				log.error("[Discovery] Failed to start UDP listener on port {}: {}", discoveryPort, e.getMessage());
			}
		});
	}

	@Override
	@PreDestroy
	public void shutdown() {
		running = false;
		if (listenerSocket != null && !listenerSocket.isClosed()) {
			listenerSocket.close();
		}
		listenerExecutor.shutdownNow();
		log.info("[Discovery] UDP discovery stopped.");
	}
}
