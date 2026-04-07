package com.giovannimenzano.jblockchain.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giovannimenzano.jblockchain.dto.request.Message;
import com.giovannimenzano.jblockchain.dto.response.BlockchainStatus;
import com.giovannimenzano.jblockchain.dto.response.MineResponse;
import com.giovannimenzano.jblockchain.entities.Block;
import com.giovannimenzano.jblockchain.exceptions.BlockchainException;
import com.giovannimenzano.jblockchain.exceptions.NotFoundException;
import com.giovannimenzano.jblockchain.services.IBlockchainService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class BlockchainServiceImpl implements IBlockchainService {

	@Value("${blockchain.mining.difficulty:4}")
	private int miningDifficulty;

	private final List<Block> chain = new ArrayList<>();
	private final List<Message> pendingMessages = new ArrayList<>();
	private final ObjectMapper objectMapper;

	public BlockchainServiceImpl(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@PostConstruct
	public void init() {
		log.info("Initializing blockchain with genesis block (difficulty={})", miningDifficulty);
		Block genesis = new Block(0, LocalDateTime.now(), "Genesis Block", "0");
		genesis.mine(miningDifficulty);
		chain.add(genesis);
		log.info("Genesis block created: {}", genesis.getHash());
	}

	@Override
	public void addMessage(Message message) {
		pendingMessages.add(message);
		log.info("Message added to pending pool. Total pending: {}", pendingMessages.size());
	}

	/**
	 * Collects all pending messages, serializes them as JSON, and mines them into a new block.
	 * The pending pool is cleared only after the block is successfully added to the chain.
	 */
	@Override
	public MineResponse mineBlock() {
		if (pendingMessages.isEmpty()) {
			throw new BlockchainException("No pending messages to mine. Submit at least one message first.");
		}

		String data;
		try {
			data = objectMapper.writeValueAsString(pendingMessages);
		} catch (JsonProcessingException e) {
			throw new BlockchainException("Failed to serialize pending messages: " + e.getMessage(), e);
		}

		Block newBlock = new Block(chain.size(), LocalDateTime.now(), data, getLastBlock().getHash());
		int pendingCount = pendingMessages.size();
		log.info("Mining block {} with {} messages (difficulty={})...", newBlock.getIndex(), pendingCount, miningDifficulty);

		newBlock.mine(miningDifficulty);

		chain.add(newBlock);
		pendingMessages.clear();

		log.info("Block {} mined successfully. Hash: {}", newBlock.getIndex(), newBlock.getHash());
		return new MineResponse("Block mined successfully", newBlock, chain.size());
	}

	/**
	 * Full chain validation. For each block (starting from block 1) it verifies:
	 * 1. Data integrity - stored hash matches a fresh recomputation of the block's contents
	 * 2. Chain linkage - previousHash field matches the actual hash of the preceding block
	 * 3. Proof of Work - hash starts with the required number of leading zeros
	 * The genesis block (index 0) is checked separately for hash integrity only.
	 */
	@Override
	public boolean isChainValid() {
		String target = "0".repeat(miningDifficulty);

		Block genesis = chain.get(0);
		if (!genesis.getHash().equals(genesis.calculateHash())) {
			log.warn("Genesis block has invalid hash - chain is corrupted");
			return false;
		}

		for (int i = 1; i < chain.size(); i++) {
			Block current = chain.get(i);
			Block previous = chain.get(i - 1);

			if (!current.getHash().equals(current.calculateHash())) {
				log.warn("Block {} has invalid hash - data may have been tampered with", i);
				return false;
			}

			if (!current.getPreviousHash().equals(previous.getHash())) {
				log.warn("Block {} has broken chain linkage", i);
				return false;
			}

			if (!current.getHash().startsWith(target)) {
				log.warn("Block {} does not satisfy the difficulty requirement", i);
				return false;
			}
		}
		return true;
	}

	/**
	 * Nakamoto consensus: accepts an incoming chain only if it is both longer than the current one
	 * and fully valid. The "longest valid chain wins" rule ensures all nodes eventually agree.
	 */
	@Override
	public boolean replaceChain(List<Block> newChain) {
		if (newChain == null || newChain.size() <= chain.size()) {
			log.info("Incoming chain is not longer than current chain (current={}, incoming={}). Keeping current.",
					chain.size(), newChain == null ? 0 : newChain.size());
			return false;
		}

		String target = "0".repeat(miningDifficulty);

		Block incomingGenesis = newChain.get(0);
		if (!incomingGenesis.getHash().equals(incomingGenesis.calculateHash())) {
			log.warn("Incoming chain has invalid genesis block - rejected");
			return false;
		}

		for (int i = 1; i < newChain.size(); i++) {
			Block current = newChain.get(i);
			Block previous = newChain.get(i - 1);

			if (!current.getHash().equals(current.calculateHash())) return false;
			if (!current.getPreviousHash().equals(previous.getHash())) return false;
			if (!current.getHash().startsWith(target)) return false;
		}

		log.info("Replacing current chain (length={}) with incoming chain (length={})", chain.size(), newChain.size());
		chain.clear();
		chain.addAll(newChain);
		return true;
	}

	@Override
	public BlockchainStatus getStatus() {
		return new BlockchainStatus(chain.size(), isChainValid(), getLastBlock().getHash(), pendingMessages.size());
	}

	@Override
	public List<Block> getChain() {
		return Collections.unmodifiableList(chain);
	}

	@Override
	public Block getLastBlock() {
		return chain.get(chain.size() - 1);
	}

	@Override
	public List<Message> getPendingMessages() {
		return Collections.unmodifiableList(pendingMessages);
	}

	@Override
	public Block getBlockByIndex(int index) {
		if (index < 0 || index >= chain.size()) {
			throw new NotFoundException("Block with index " + index + " not found. Chain length: " + chain.size());
		}
		return chain.get(index);
	}

	@Override
	public int getMiningDifficulty() {
		return miningDifficulty;
	}
}
