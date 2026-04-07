package com.giovannimenzano.jblockchain.controller;

import com.giovannimenzano.jblockchain.dto.request.Message;
import com.giovannimenzano.jblockchain.dto.response.BlockchainStatus;
import com.giovannimenzano.jblockchain.dto.response.GenericResponse;
import com.giovannimenzano.jblockchain.dto.response.MineResponse;
import com.giovannimenzano.jblockchain.entities.Block;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Blockchain", description = "Manage the blockchain: query, validate, submit messages and mine blocks")
@RequestMapping("${api.blockchain.base-path}")
public interface IBlockchainController {

	@Operation(summary = "Get the full chain")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Chain returned successfully")
	})
	@GetMapping
	ResponseEntity<GenericResponse<List<Block>>> getChain();

	@Operation(summary = "Get blockchain status", description = "Returns chain length, validity, last block hash and pending message count")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Status returned successfully")
	})
	@GetMapping("${api.blockchain.status}")
	ResponseEntity<GenericResponse<BlockchainStatus>> getStatus();

	@Operation(summary = "Get a block by index")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Block found"),
		@ApiResponse(responseCode = "404", description = "Block not found")
	})
	@GetMapping("${api.blockchain.block}")
	ResponseEntity<GenericResponse<Block>> getBlock(@PathVariable int index);

	@Operation(summary = "Validate the chain integrity")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Validation result returned")
	})
	@GetMapping("${api.blockchain.validate}")
	ResponseEntity<GenericResponse<Boolean>> validateChain();

	@Operation(summary = "Get all pending messages in the pool")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Pending messages returned")
	})
	@GetMapping("${api.blockchain.pending}")
	ResponseEntity<GenericResponse<List<Message>>> getPendingMessages();

	@Operation(summary = "Submit a message to the pending pool")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "Message added to pool"),
		@ApiResponse(responseCode = "400", description = "Invalid message payload")
	})
	@PostMapping("${api.blockchain.messages}")
	ResponseEntity<GenericResponse<Integer>> addMessage(@Valid @RequestBody Message message);

	@Operation(summary = "Mine a new block", description = "Collects all pending messages and mines them into a new block, then broadcasts to peers")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Block mined successfully"),
		@ApiResponse(responseCode = "400", description = "No pending messages to mine")
	})
	@PostMapping("${api.blockchain.mine}")
	ResponseEntity<GenericResponse<MineResponse>> mine();
}
