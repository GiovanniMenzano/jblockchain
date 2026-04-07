package com.giovannimenzano.jblockchain.entities;

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
	private String data;
	private int nonce;
	private String previousHash;
	private String hash;

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
