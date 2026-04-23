package com.giovannimenzano.jblockchain.dto.response;

import com.giovannimenzano.jblockchain.entities.Block;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO returned after a successful mining operation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MineResponse {

	private String message;
	private Block block;
	private int chainLength;
}
