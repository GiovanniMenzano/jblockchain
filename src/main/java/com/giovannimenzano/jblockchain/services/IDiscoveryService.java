package com.giovannimenzano.jblockchain.services;

/**
 * Service responsible for automatic peer discovery on the local network.
 * Implementations broadcast the node's presence and listen for other nodes.
 */
public interface IDiscoveryService {

	/**
	 * Broadcasts this node's URL to the local network so other nodes can discover it.
	 */
	void broadcastPresence();

	/**
	 * Gracefully stops the discovery listener and releases network resources.
	 */
	void shutdown();
}
