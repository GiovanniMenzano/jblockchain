package com.giovannimenzano.jblockchain.exceptions;

import java.io.Serial;

/**
 * Custom runtime exception for blockchain-specific errors.
 * Caught by GlobalExceptionHandler and returned as a structured HTTP 400 response.
 */
public class BlockchainException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	public BlockchainException(String message) {
		super(message);
	}

	public BlockchainException(String message, Throwable cause) {
		super(message, cause);
	}
}
