package com.giovannimenzano.jblockchain.services;

import com.giovannimenzano.jblockchain.dto.response.BlockchainStatus;
import com.giovannimenzano.jblockchain.entities.Block;

import java.util.Set;

public interface INetworkService {

	/**
	 * Registers a peer node URL in the local peer set.
	 */
	void registerNode(String url);

	/**
	 * Returns an unmodifiable view of all known peer URLs.
	 */
	Set<String> getNodes();

	/**
	 * Broadcasts a newly mined block to a subset of registered peers (gossip).
	 * Failures per individual peer are swallowed so one unreachable node
	 * does not prevent the rest of the network from receiving the block.
	 */
	void broadcastBlock(Block block);

	/**
	 * Two-step Nakamoto-style consensus:
	 * 1. Fetches only the chain status (length + last hash) from each peer - lightweight
	 * 2. Downloads the full chain only from the peer with the longest chain
	 * Replaces the local chain if the peer's chain is longer and valid.
	 */
	boolean resolveConflicts();

	/**
	 * Receives a block broadcast from a peer, validates it and re-broadcasts
	 * to a random subset of local peers (gossip propagation).
	 * Uses an anti-loop Set to ignore already-seen blocks.
	 */
	void receiveBroadcast(Block block, String senderUrl);

	/**
	 * Fetches chain status from a peer node without downloading the full chain.
	 */
	BlockchainStatus getPeerStatus(String peerUrl);
}
