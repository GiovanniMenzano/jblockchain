package com.giovannimenzano.jblockchain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenericResponse<T> {

	private boolean success;
	private String message;
	private T data;

	public static <T> GenericResponse<T> success(String message, T data) {
		return GenericResponse.<T>builder().success(true).message(message).data(data).build();
	}

	public static <T> GenericResponse<T> success(String message) {
		return GenericResponse.<T>builder().success(true).message(message).build();
	}

	public static <T> GenericResponse<T> error(String message) {
		return GenericResponse.<T>builder().success(false).message(message).build();
	}
}
