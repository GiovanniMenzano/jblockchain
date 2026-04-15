package com.giovannimenzano.jblockchain.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.giovannimenzano.jblockchain.dto.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Generic payload stored inside a block.
 * The {@code metadata} map is optional and intended for binary messages
 * (e.g. filename, mimeType when type is BINARY).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {

	@NotNull(message = "Message type is required (TEXT, JSON, BINARY)")
	private MessageType type;

	@NotBlank(message = "Content cannot be blank")
	private String content;

	private Map<String, String> metadata;
}
