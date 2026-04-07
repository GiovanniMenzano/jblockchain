package com.giovannimenzano.jblockchain.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.giovannimenzano.jblockchain.dto.MessageType;
import com.giovannimenzano.jblockchain.dto.request.Message;
import com.giovannimenzano.jblockchain.dto.response.MineResponse;
import com.giovannimenzano.jblockchain.entities.Block;
import com.giovannimenzano.jblockchain.exceptions.BlockchainException;
import com.giovannimenzano.jblockchain.exceptions.NotFoundException;
import com.giovannimenzano.jblockchain.services.impl.BlockchainServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class BlockchainServiceTest {

	private BlockchainServiceImpl blockchainService;

	@BeforeEach
	void setUp() {
		blockchainService = new BlockchainServiceImpl(new ObjectMapper());
		// Use difficulty=2 so tests run fast (mining takes ~ms instead of seconds)
		ReflectionTestUtils.setField(blockchainService, "miningDifficulty", 2);
		blockchainService.init();
	}

	@Test
	@DisplayName("Blockchain should start with a genesis block")
	void shouldInitializeWithGenesisBlock() {
		List<Block> chain = blockchainService.getChain();
		assertThat(chain).hasSize(1);
		assertThat(chain.get(0).getIndex()).isEqualTo(0);
		assertThat(chain.get(0).getData()).isEqualTo("Genesis Block");
		assertThat(chain.get(0).getPreviousHash()).isEqualTo("0");
		assertThat(chain.get(0).getHash()).startsWith("00"); // difficulty=2
	}

	@Test
	@DisplayName("Adding a message should increase the pending pool")
	void shouldAddMessageToPendingPool() {
		Message msg = new Message(MessageType.TEXT, "Hello Blockchain!", null);
		blockchainService.addMessage(msg);
		assertThat(blockchainService.getPendingMessages()).hasSize(1);
	}

	@Test
	@DisplayName("Mining should create a valid block and clear the pending pool")
	void shouldMineBlockWithPendingMessages() {
		blockchainService.addMessage(new Message(MessageType.TEXT, "Hello!", null));

		MineResponse response = blockchainService.mineBlock();
		Block mined = response.getBlock();

		assertThat(mined.getIndex()).isEqualTo(1);
		assertThat(mined.getHash()).startsWith("00"); // difficulty=2
		assertThat(mined.getPreviousHash()).isEqualTo(blockchainService.getChain().get(0).getHash());
		assertThat(blockchainService.getPendingMessages()).isEmpty();
		assertThat(blockchainService.getChain()).hasSize(2);
	}

	@Test
	@DisplayName("Mining multiple blocks should build a correct chain")
	void shouldMineMultipleBlocks() {
		blockchainService.addMessage(new Message(MessageType.TEXT, "Block 1", null));
		blockchainService.mineBlock();
		blockchainService.addMessage(new Message(MessageType.JSON, "{\"key\":\"value\"}", null));
		blockchainService.mineBlock();

		assertThat(blockchainService.getChain()).hasSize(3);
	}

	@Test
	@DisplayName("Mining with no pending messages should throw BlockchainException")
	void shouldThrowWhenMiningWithNoPendingMessages() {
		assertThatThrownBy(() -> blockchainService.mineBlock())
				.isInstanceOf(BlockchainException.class)
				.hasMessageContaining("No pending messages");
	}

	@Test
	@DisplayName("Chain validation should pass for an untampered chain")
	void shouldPassValidationForUntamperedChain() {
		blockchainService.addMessage(new Message(MessageType.TEXT, "Valid message", null));
		blockchainService.mineBlock();

		assertThat(blockchainService.isChainValid()).isTrue();
	}

	@Test
	@DisplayName("Chain validation should fail when block data is tampered")
	void shouldFailValidationForTamperedData() {
		blockchainService.addMessage(new Message(MessageType.TEXT, "Original data", null));
		blockchainService.mineBlock();

		// Directly tamper with block data (simulates an attack)
		Block block = blockchainService.getChain().get(1);
		ReflectionTestUtils.setField(block, "data", "TAMPERED DATA");

		assertThat(blockchainService.isChainValid()).isFalse();
	}

	@Test
	@DisplayName("Chain validation should fail when hash linkage is broken")
	void shouldFailValidationForBrokenHashLinkage() {
		blockchainService.addMessage(new Message(MessageType.TEXT, "Block 1", null));
		blockchainService.mineBlock();

		// Tamper with the previousHash of block 1 to break linkage
		Block block = blockchainService.getChain().get(1);
		ReflectionTestUtils.setField(block, "previousHash", "0000000000000000000000000000000000000000000000000000000000000000");

		assertThat(blockchainService.isChainValid()).isFalse();
	}

	@Test
	@DisplayName("replaceChain should reject a chain of equal or smaller length")
	void shouldRejectShorterOrEqualChainInReplace() {
		List<Block> currentChain = List.copyOf(blockchainService.getChain());
		boolean replaced = blockchainService.replaceChain(currentChain);
		assertThat(replaced).isFalse();
	}

	@Test
	@DisplayName("getBlockByIndex should throw NotFoundException for out-of-bounds index")
	void shouldThrowForInvalidBlockIndex() {
		assertThatThrownBy(() -> blockchainService.getBlockByIndex(99))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining("not found");
	}

	@Test
	@DisplayName("getStatus should return correct chain metadata")
	void shouldReturnCorrectStatus() {
		var status = blockchainService.getStatus();
		assertThat(status.getChainLength()).isEqualTo(1);
		assertThat(status.isValid()).isTrue();
		assertThat(status.getPendingMessages()).isEqualTo(0);
		assertThat(status.getLastBlockHash()).isNotBlank();
	}
}
