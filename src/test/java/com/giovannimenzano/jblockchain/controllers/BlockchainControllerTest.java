package com.giovannimenzano.jblockchain.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.giovannimenzano.jblockchain.controller.impl.BlockchainController;
import com.giovannimenzano.jblockchain.dto.MessageType;
import com.giovannimenzano.jblockchain.dto.request.Message;
import com.giovannimenzano.jblockchain.dto.response.BlockchainStatus;
import com.giovannimenzano.jblockchain.dto.response.MineResponse;
import com.giovannimenzano.jblockchain.entities.Block;
import com.giovannimenzano.jblockchain.exceptions.BlockchainException;
import com.giovannimenzano.jblockchain.interceptor.GlobalExceptionHandler;
import com.giovannimenzano.jblockchain.services.IBlockchainService;
import com.giovannimenzano.jblockchain.services.INetworkService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BlockchainController.class)
@Import(GlobalExceptionHandler.class)
class BlockchainControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockBean
	private IBlockchainService blockchainService;

	@MockBean
	private INetworkService networkService;

	@MockBean
	private RestTemplate restTemplate;

	@Test
	@DisplayName("GET /api/chain should return 200 with the full blockchain")
	void getChain_shouldReturn200() throws Exception {
		Block genesis = new Block(0, LocalDateTime.now(), "Genesis Block", "0");
		when(blockchainService.getChain()).thenReturn(List.of(genesis));

		mockMvc.perform(get("/api/chain"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray())
				.andExpect(jsonPath("$.data[0].index").value(0))
				.andExpect(jsonPath("$.data[0].data").value("Genesis Block"));
	}

	@Test
	@DisplayName("GET /api/chain/status should return chain metadata")
	void getStatus_shouldReturn200() throws Exception {
		when(blockchainService.getStatus())
				.thenReturn(new BlockchainStatus(1, true, "abc123", 0));

		mockMvc.perform(get("/api/chain/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.chainLength").value(1))
				.andExpect(jsonPath("$.data.valid").value(true))
				.andExpect(jsonPath("$.data.pendingMessages").value(0));
	}

	@Test
	@DisplayName("GET /api/chain/validate should return valid=true")
	void validateChain_shouldReturnValid() throws Exception {
		when(blockchainService.isChainValid()).thenReturn(true);

		mockMvc.perform(get("/api/chain/validate"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").value(true));
	}

	@Test
	@DisplayName("POST /api/chain/messages should return 201 for valid message")
	void addMessage_withValidBody_shouldReturn201() throws Exception {
		Message message = new Message(MessageType.TEXT, "Hello Blockchain!", null);
		when(blockchainService.getPendingMessages()).thenReturn(List.of(message));

		mockMvc.perform(post("/api/chain/messages")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(message)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.message").value("Message added to pending pool"));

		verify(blockchainService, times(1)).addMessage(any(Message.class));
	}

	@Test
	@DisplayName("POST /api/chain/messages should return 400 when content is blank")
	void addMessage_withBlankContent_shouldReturn400() throws Exception {
		Message message = new Message(MessageType.TEXT, "", null);

		mockMvc.perform(post("/api/chain/messages")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(message)))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("POST /api/chain/messages should return 400 when type is missing")
	void addMessage_withMissingType_shouldReturn400() throws Exception {
		String json = "{\"content\": \"no type\"}";

		mockMvc.perform(post("/api/chain/messages")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("POST /api/chain/mine should return 200 with the mined block")
	void mine_shouldReturn200() throws Exception {
		Block mined = new Block(1, LocalDateTime.now(), "[{\"type\":\"TEXT\",\"content\":\"Hello\"}]", "prev-hash");
		MineResponse mineResponse = new MineResponse("Block mined successfully", mined, 2);
		when(blockchainService.mineBlock()).thenReturn(mineResponse);

		mockMvc.perform(post("/api/chain/mine"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Block mined successfully"))
				.andExpect(jsonPath("$.data.block.index").value(1));

		verify(networkService, times(1)).broadcastBlock(any(Block.class));
	}

	@Test
	@DisplayName("POST /api/chain/mine should return 400 when no pending messages")
	void mine_withNoPending_shouldReturn400() throws Exception {
		when(blockchainService.mineBlock())
				.thenThrow(new BlockchainException("No pending messages to mine"));

		mockMvc.perform(post("/api/chain/mine"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("No pending messages to mine"));
	}
}
