package com.giovannimenzano.jblockchain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO returned when querying the blockchain status.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockchainStatus {

	private int chainLength;
	private boolean valid;
	private String lastBlockHash;
	private int pendingMessages;
}
