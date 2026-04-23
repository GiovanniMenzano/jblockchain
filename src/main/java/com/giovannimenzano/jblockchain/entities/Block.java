package com.giovannimenzano.jblockchain.entities;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Slf4j
public class Block {

	private int index;
	private LocalDateTime timestamp;

	/**
	 * Block payload stored as a JSON string internally (for deterministic hash computation).
	 * Jackson serialization/deserialization is handled by custom getDataJson()/setDataJson()
	 * to avoid double-escaping of JSON content in the REST API output.
	 */
	@JsonIgnore
	private String data;

	private int nonce;
	private String previousHash;
	private String hash;

	/**
	 * Outputs the data field as raw JSON when it contains a JSON structure (mined blocks),
	 * or as a regular JSON string for plain text (genesis block).
	 */
	@JsonGetter("data")
	@JsonRawValue
	public String getDataJson() {
		if (data != null && (data.startsWith("[") || data.startsWith("{"))) {
			return data;
		}
		// Plain string like "Genesis Block" - must still be a valid JSON value
		return "\"" + data.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	/**
	 * Deserializes the data field from JSON: handles both plain strings (genesis)
	 * and structured JSON arrays/objects (mined blocks from peer sync).
	 */
	@JsonSetter("data")
	public void setDataJson(JsonNode node) {
		if (node.isTextual()) {
			this.data = node.textValue();
		} else {
			this.data = node.toString();
		}
	}

	public Block(int index, LocalDateTime timestamp, String data, String previousHash) {
		this.index = index;
		this.timestamp = timestamp;
		this.data = data;
		this.nonce = 0;
		this.previousHash = previousHash;
		this.hash = calculateHash();
	}

	/**
	 * Proof of Work: increments the nonce and recomputes the hash until the hash starts
	 * with the required number of leading zeros (the "difficulty" prefix).
	 * This loop is the computational "work" that makes the blockchain tamper-resistant:
	 * changing any past block would require redoing the work for that block and every one after it.
	 */
	public String mine(int difficulty) {
		String target = "0".repeat(difficulty);
		while (!hash.startsWith(target)) {
			nonce++;
			hash = calculateHash();
		}
		log.info("Block mined at index {}: {} (nonce={})", index, hash, nonce);
		return hash;
	}

	/**
	 * Computes SHA-256(index + timestamp + data + previousHash + nonce).
	 * Made public so BlockchainService can recompute it during chain validation
	 * and compare with the stored hash to detect tampering.
	 */
	public String calculateHash() {
		String dataToHash = index + timestamp.toString() + data + previousHash + nonce;
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] bytes = md.digest(dataToHash.getBytes());
			StringBuilder buffer = new StringBuilder();
			for (byte b : bytes) {
				buffer.append(String.format("%02x", b));
			}
			return buffer.toString();
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 is guaranteed by the JVM spec; this branch is unreachable in practice
			log.error("SHA-256 algorithm not found", e);
			throw new RuntimeException("SHA-256 not available", e);
		}
	}

	public boolean isValidHash(int difficulty) {
		return hash.startsWith("0".repeat(difficulty));
	}
}
