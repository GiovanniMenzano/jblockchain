package com.giovannimenzano.jblockchain.interceptor;

import com.giovannimenzano.jblockchain.dto.response.GenericResponse;
import com.giovannimenzano.jblockchain.exceptions.BlockchainException;
import com.giovannimenzano.jblockchain.exceptions.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BlockchainException.class)
	public ResponseEntity<GenericResponse<Void>> handleBlockchainException(BlockchainException ex) {
		log.warn("Blockchain error: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(GenericResponse.error(ex.getMessage()));
	}

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<GenericResponse<Void>> handleNotFoundException(NotFoundException ex) {
		log.warn("Resource not found: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(GenericResponse.error(ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<GenericResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
		String errors = ex.getBindingResult().getAllErrors().stream()
				.map(error -> ((FieldError) error).getField() + ": " + error.getDefaultMessage())
				.collect(Collectors.joining(", "));
		return ResponseEntity.badRequest().body(GenericResponse.error("Validation failed: " + errors));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<GenericResponse<Void>> handleGenericException(Exception ex) {
		log.error("Unexpected error", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(GenericResponse.error("An unexpected error occurred"));
	}
}
