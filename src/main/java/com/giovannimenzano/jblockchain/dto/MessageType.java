package com.giovannimenzano.jblockchain.dto;

/**
 * Describes the type of data stored in a blockchain message.
 * TEXT - plain text strings
 * JSON - arbitrary JSON payloads
 * BINARY - Base64-encoded binary content (images, PDFs, etc.)
 */
public enum MessageType {
	TEXT,
	JSON,
	BINARY
}
