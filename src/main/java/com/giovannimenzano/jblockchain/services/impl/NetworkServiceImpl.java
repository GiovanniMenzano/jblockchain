package com.giovannimenzano.jblockchain.services.impl;

import com.giovannimenzano.jblockchain.dto.response.BlockchainStatus;
import com.giovannimenzano.jblockchain.entities.Block;
import com.giovannimenzano.jblockchain.exceptions.BlockchainException;
import com.giovannimenzano.jblockchain.services.IBlockchainService;
import com.giovannimenzano.jblockchain.services.INetworkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Manages the P2P network of blockchain nodes.
 *
 * Each node maintains a set of peer URLs. When a block is mined locally, it is broadcast
 * to a random subset of peers (gossip). Peers that receive a broadcast re-broadcast
 * to their own subset, creating an efficient propagation wave across the network.
 * An anti-loop Set prevents the same block from being re-broadcast indefinitely.
 */
@Slf4j
@Service
public class NetworkServiceImpl implements INetworkService {

	/**
	 * Maximum number of peers to contact per broadcast. 0 = broadcast to all (legacy mode).
	 */
	@Value("${blockchain.network.max-broadcast-peers:0}")
	private int maxBroadcastPeers;

	@Value("${api.blockchain.base-path:/api/chain}")
	private String blockchainBasePath;

	@Value("${api.blockchain.status:/status}")
	private String statusEndpoint;

	@Value("${api.network.base-path:/api/network}")
	private String networkBasePath;

	@Value("${api.network.broadcast:/broadcast}")
	private String broadcastEndpoint;

	private final Set<String> nodes = new HashSet<>();

	/**
	 * Anti-loop set: stores hashes of blocks already received via broadcast.
	 * Prevents the same block from being re-broadcast indefinitely in a mesh network.
	 */
	private final Set<String> seenBlockHashes = Collections.synchronizedSet(new HashSet<>());

	private final IBlockchainService blockchainService;
	private final RestTemplate restTemplate;
	private final Random random = new Random();

	public NetworkServiceImpl(IBlockchainService blockchainService, RestTemplate restTemplate) {
		this.blockchainService = blockchainService;
		this.restTemplate = restTemplate;
	}

	@Override
	public void registerNode(String url) {
		if (url == null || url.isBlank()) {
			throw new BlockchainException("Node URL cannot be empty");
		}
		nodes.add(url.trim());
		log.info("Node registered: {}. Total peers: {}", url.trim(), nodes.size());
	}

	@Override
	public Set<String> getNodes() {
		return Collections.unmodifiableSet(nodes);
	}

	/**
	 * Broadcasts a newly mined block to a random subset of registered peers (gossip).
	 * If max-broadcast-peers is 0 (default), broadcasts to all peers.
	 * Failures per individual peer are swallowed so one unreachable node
	 * does not prevent the rest of the network from receiving the block.
	 */
	@Override
	public void broadcastBlock(Block block) {
		if (nodes.isEmpty()) {
			log.info("No peer nodes registered. Skipping broadcast.");
			return;
		}

		seenBlockHashes.add(block.getHash());
		Set<String> targets = selectBroadcastTargets(nodes, null);
		log.info("Broadcasting block {} to {}/{} peers", block.getIndex(), targets.size(), nodes.size());

		targets.forEach(nodeUrl -> sendBroadcast(block, nodeUrl, null));
	}

	/**
	 * Receives a block broadcast from a peer. Ignores already-seen blocks (anti-loop).
	 * If the block is new, triggers a consensus round and re-broadcasts to local peers.
	 */
	@Override
	public void receiveBroadcast(Block block, String senderUrl) {
		if (seenBlockHashes.contains(block.getHash())) {
			log.debug("Block {} already seen - ignoring duplicate broadcast", block.getHash());
			return;
		}

		seenBlockHashes.add(block.getHash());
		log.info("Received new block {} from {}. Triggering consensus and re-broadcasting.", block.getIndex(), senderUrl);

		resolveConflicts();

		Set<String> targets = selectBroadcastTargets(nodes, senderUrl);
		if (!targets.isEmpty()) {
			log.info("Re-broadcasting block {} to {}/{} peers", block.getIndex(), targets.size(), nodes.size());
			targets.forEach(nodeUrl -> sendBroadcast(block, nodeUrl, senderUrl));
		}
	}

	/**
	 * Fetches only the chain status (length + last hash) from a peer - lightweight call.
	 */
	@Override
	public BlockchainStatus getPeerStatus(String peerUrl) {
		String statusUrl = peerUrl + blockchainBasePath + statusEndpoint;
		return restTemplate.getForObject(statusUrl, BlockchainStatus.class);
	}

	/**
	 * Two-step Nakamoto-style consensus:
	 * 1. Fetches only the chain status (length) from each peer - lightweight
	 * 2. Downloads the full chain only from the peer with the longest chain
	 * Replaces the local chain if the peer's chain is longer and valid.
	 */
	@Override
	public boolean resolveConflicts() {
		if (nodes.isEmpty()) {
			return false;
		}

		// Step 1: find the peer with the longest chain (by status only - lightweight)
		String bestPeerUrl = null;
		int bestLength = blockchainService.getChain().size();

		for (String nodeUrl : nodes) {
			try {
				BlockchainStatus peerStatus = getPeerStatus(nodeUrl);
				if (peerStatus != null && peerStatus.getChainLength() > bestLength) {
					bestLength = peerStatus.getChainLength();
					bestPeerUrl = nodeUrl;
				}
			} catch (RestClientException e) {
				log.warn("Failed to fetch status from {}: {}", nodeUrl, e.getMessage());
			}
		}

		if (bestPeerUrl == null) {
			log.info("Consensus: current chain is already the longest (length={}).", blockchainService.getChain().size());
			return false;
		}

		// Step 2: download full chain only from the best peer
		log.info("Consensus: peer {} has longer chain (length={}). Downloading full chain.", bestPeerUrl, bestLength);
		try {
			String chainUrl = bestPeerUrl + blockchainBasePath;
			ResponseEntity<List<Block>> response = restTemplate.exchange(
					chainUrl,
					HttpMethod.GET,
					null,
					new ParameterizedTypeReference<List<Block>>() {}
			);
			List<Block> peerChain = response.getBody();
			if (peerChain != null && blockchainService.replaceChain(peerChain)) {
				log.info("Chain replaced with longer valid chain from {}", bestPeerUrl);
				return true;
			}
		} catch (RestClientException e) {
			log.warn("Failed to fetch full chain from {}: {}", bestPeerUrl, e.getMessage());
		}

		return false;
	}

	/**
	 * Selects a random subset of peers to broadcast to, excluding the sender.
	 * If max-broadcast-peers is 0, all peers (except sender) are selected.
	 */
	private Set<String> selectBroadcastTargets(Set<String> allNodes, String excludeUrl) {
		List<String> candidates = allNodes.stream()
				.filter(url -> !url.equals(excludeUrl))
				.collect(Collectors.toList());

		if (maxBroadcastPeers <= 0 || candidates.size() <= maxBroadcastPeers) {
			return new HashSet<>(candidates);
		}

		Collections.shuffle(candidates, random);
		return new HashSet<>(candidates.subList(0, maxBroadcastPeers));
	}

	private void sendBroadcast(Block block, String targetUrl, String senderUrl) {
		try {
			String url = targetUrl + networkBasePath + broadcastEndpoint;
			if (senderUrl != null) {
				url += "?sender=" + senderUrl;
			}
			restTemplate.postForObject(url, block, Void.class);
			log.info("Block {} broadcast to {}", block.getIndex(), targetUrl);
		} catch (RestClientException e) {
			log.warn("Failed to broadcast block {} to {}: {}", block.getIndex(), targetUrl, e.getMessage());
		}
	}
}
