package org.shubh.assignment.web;

import java.time.Instant;
import org.shubh.assignment.service.GuardrailRejectedException;
import org.shubh.assignment.service.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(GuardrailRejectedException.class)
    public ResponseEntity<ApiError> guardrailRejected(GuardrailRejectedException ex) {
        return error(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        return error(HttpStatus.BAD_REQUEST, "Request validation failed");
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message);
        return ResponseEntity.status(status).body(body);
    }
}
