package com.giovannimenzano.jblockchain.controller.impl;

import com.giovannimenzano.jblockchain.controller.INetworkController;
import com.giovannimenzano.jblockchain.dto.request.NodeInfo;
import com.giovannimenzano.jblockchain.dto.response.GenericResponse;
import com.giovannimenzano.jblockchain.entities.Block;
import com.giovannimenzano.jblockchain.services.INetworkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@Slf4j
@RequiredArgsConstructor
public class NetworkController implements INetworkController {

	private final INetworkService networkService;

	@Override
	public ResponseEntity<GenericResponse<Set<String>>> getNodes() {
		return ResponseEntity.ok(GenericResponse.success("Peers retrieved", networkService.getNodes()));
	}

	@Override
	public ResponseEntity<GenericResponse<Integer>> registerNode(@Valid @RequestBody NodeInfo nodeInfo) {
		log.info("Request to register peer node: {}", nodeInfo.getUrl());
		networkService.registerNode(nodeInfo.getUrl());
		int total = networkService.getNodes().size();
		return ResponseEntity.ok(GenericResponse.success("Node registered successfully", total));
	}

	@Override
	public ResponseEntity<GenericResponse<Boolean>> resolveConflicts() {
		log.info("Request to resolve chain conflicts (consensus)");
		boolean replaced = networkService.resolveConflicts();
		String msg = replaced
				? "Chain was replaced with a longer valid chain from a peer node"
				: "Current chain is authoritative - no replacement needed";
		return ResponseEntity.ok(GenericResponse.success(msg, replaced));
	}

	/**
	 * Receives a block broadcast from a peer. Rather than incrementally appending the single block,
	 * we trigger a full consensus round: this is simpler and handles forks correctly
	 * at the cost of one extra network call per broadcast.
	 * The senderUrl is passed through to prevent re-broadcasting back to the original sender.
	 */
	@Override
	public ResponseEntity<GenericResponse<String>> receiveBlock(
			@RequestBody Block block,
			@RequestParam(value = "sender", required = false) String senderUrl) {
		log.info("Received block broadcast for index: {} from: {}", block.getIndex(), senderUrl != null ? senderUrl : "unknown");
		networkService.receiveBroadcast(block, senderUrl);
		return ResponseEntity.ok(GenericResponse.success("Block received and processed", null));
	}
}
