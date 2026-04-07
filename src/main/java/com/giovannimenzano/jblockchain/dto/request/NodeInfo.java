package com.giovannimenzano.jblockchain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeInfo {

	@NotBlank(message = "Node URL is required (e.g. http://localhost:8092/jblockchain)")
	private String url;
}
