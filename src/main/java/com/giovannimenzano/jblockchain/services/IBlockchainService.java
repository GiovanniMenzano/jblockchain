package com.giovannimenzano.jblockchain.services;

import com.giovannimenzano.jblockchain.dto.request.Message;
import com.giovannimenzano.jblockchain.dto.response.BlockchainStatus;
import com.giovannimenzano.jblockchain.dto.response.MineResponse;
import com.giovannimenzano.jblockchain.entities.Block;

import java.util.List;

public interface IBlockchainService {

	/**
	 * Adds a new message to the pending pool.
	 * Messages wait here until mineBlock() is called.
	 */
	void addMessage(Message message);

	/**
	 * Collects all pending messages, serializes them as JSON, and mines them into a new block.
	 * The pending pool is cleared only after the block is successfully added to the chain.
	 */
	MineResponse mineBlock();

	/**
	 * Full chain validation. For each block (starting from block 1) it verifies:
	 * 1. Data integrity - stored hash matches a fresh recomputation of the block's contents
	 * 2. Chain linkage - previousHash field matches the actual hash of the preceding block
	 * 3. Proof of Work - hash starts with the required number of leading zeros
	 * The genesis block (index 0) is checked separately for hash integrity only.
	 */
	boolean isChainValid();

	/**
	 * Nakamoto consensus: accepts an incoming chain only if it is both longer than the current one
	 * and fully valid. The "longest valid chain wins" rule ensures all nodes eventually agree.
	 */
	boolean replaceChain(List<Block> newChain);

	/**
	 * Returns summary information about the current state of the blockchain.
	 */
	BlockchainStatus getStatus();

	/**
	 * Returns an unmodifiable view of the full chain.
	 */
	List<Block> getChain();

	/**
	 * Returns the last block in the chain.
	 */
	Block getLastBlock();

	/**
	 * Returns an unmodifiable view of the pending message pool.
	 */
	List<Message> getPendingMessages();

	/**
	 * Returns the block at the given index, or throws NotFoundException.
	 */
	Block getBlockByIndex(int index);

	/**
	 * Returns the configured mining difficulty.
	 */
	int getMiningDifficulty();
}
