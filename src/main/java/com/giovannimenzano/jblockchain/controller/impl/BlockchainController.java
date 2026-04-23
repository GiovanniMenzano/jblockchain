package com.giovannimenzano.jblockchain.controller.impl;

import com.giovannimenzano.jblockchain.controller.IBlockchainController;
import com.giovannimenzano.jblockchain.dto.request.Message;
import com.giovannimenzano.jblockchain.dto.response.BlockchainStatus;
import com.giovannimenzano.jblockchain.dto.response.GenericResponse;
import com.giovannimenzano.jblockchain.dto.response.MineResponse;
import com.giovannimenzano.jblockchain.entities.Block;
import com.giovannimenzano.jblockchain.services.IBlockchainService;
import com.giovannimenzano.jblockchain.services.INetworkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
public class BlockchainController implements IBlockchainController {

	private final IBlockchainService blockchainService;
	private final INetworkService networkService;

	@Override
	public ResponseEntity<GenericResponse<List<Block>>> getChain() {
		return ResponseEntity.ok(GenericResponse.success("Chain retrieved", blockchainService.getChain()));
	}

	@Override
	public ResponseEntity<GenericResponse<BlockchainStatus>> getStatus() {
		return ResponseEntity.ok(GenericResponse.success("Status retrieved", blockchainService.getStatus()));
	}

	@Override
	public ResponseEntity<GenericResponse<Block>> getBlock(@PathVariable int index) {
		return ResponseEntity.ok(GenericResponse.success("Block retrieved", blockchainService.getBlockByIndex(index)));
	}

	@Override
	public ResponseEntity<GenericResponse<Boolean>> validateChain() {
		log.info("Request to validate chain integrity");
		boolean valid = blockchainService.isChainValid();
		String msg = valid ? "Chain is valid - no tampering detected" : "Chain is INVALID - tampering or corruption detected!";
		return ResponseEntity.ok(GenericResponse.success(msg, valid));
	}

	@Override
	public ResponseEntity<GenericResponse<List<Message>>> getPendingMessages() {
		return ResponseEntity.ok(GenericResponse.success("Pending messages retrieved", blockchainService.getPendingMessages()));
	}

	@Override
	public ResponseEntity<GenericResponse<Integer>> addMessage(@Valid @RequestBody Message message) {
		log.info("Incoming request to add message of type: {}", message.getType());
		blockchainService.addMessage(message);
		int pendingCount = blockchainService.getPendingMessages().size();
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(GenericResponse.success("Message added to pending pool", pendingCount));
	}

	@Override
	public ResponseEntity<GenericResponse<MineResponse>> mine() {
		log.info("Incoming request to mine a new block");
		MineResponse mineResponse = blockchainService.mineBlock();
		networkService.broadcastBlock(mineResponse.getBlock());
		return ResponseEntity.ok(GenericResponse.success("Block mined successfully", mineResponse));
	}
}
