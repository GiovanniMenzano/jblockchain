package com.giovannimenzano.jblockchain.controller;

import com.giovannimenzano.jblockchain.dto.request.NodeInfo;
import com.giovannimenzano.jblockchain.dto.response.GenericResponse;
import com.giovannimenzano.jblockchain.entities.Block;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@Tag(name = "Network", description = "Manage P2P network peers and blockchain consensus")
@RequestMapping("${api.network.base-path}")
public interface INetworkController {

	@Operation(summary = "Get registered peer nodes")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Peer list returned")
	})
	@GetMapping("${api.network.nodes}")
	ResponseEntity<GenericResponse<Set<String>>> getNodes();

	@Operation(summary = "Register a peer node", description = "Adds a peer node URL to the local peer set")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Node registered successfully"),
		@ApiResponse(responseCode = "400", description = "Invalid node URL")
	})
	@PostMapping("${api.network.nodes}")
	ResponseEntity<GenericResponse<Integer>> registerNode(@Valid @RequestBody NodeInfo nodeInfo);

	@Operation(summary = "Resolve chain conflicts (consensus)", description = "Runs two-step Nakamoto consensus: fetches status from all peers, downloads and adopts the longest valid chain")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Consensus result returned")
	})
	@PostMapping("${api.network.resolve}")
	ResponseEntity<GenericResponse<Boolean>> resolveConflicts();

	@Operation(summary = "Receive a block broadcast from a peer", description = "Internal endpoint used for P2P gossip propagation. Validates the block, runs consensus and re-broadcasts to local peers.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Broadcast received and processed")
	})
	@PostMapping("${api.network.broadcast}")
	ResponseEntity<GenericResponse<String>> receiveBlock(
		@RequestBody Block block,
		@RequestParam(value = "sender", required = false) String senderUrl
	);
}
