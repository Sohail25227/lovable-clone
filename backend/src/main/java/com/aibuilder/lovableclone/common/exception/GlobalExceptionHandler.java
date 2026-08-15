package com.aibuilder.lovableclone.common.exception;

import java.time.Instant;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.aibuilder.lovableclone.common.dto.ApiErrorDto;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ApiErrorDto> handleAlreadyExists(ResourceAlreadyExistsException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    // @Valid fail hone pe Spring yeh exception phenkta hai
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorDto> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return build(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorDto> handleInvalidCredentials(InvalidCredentialsException ex) {
        String message = ex.getMessage();

        return build(HttpStatus.UNAUTHORIZED, message);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorDto> handleResourceNotFoundException(ResourceNotFoundException ex) {
        String message = ex.getMessage();

        return build(HttpStatus.NOT_FOUND, message);
    }

    // Do requests ne ek hi row saath mein badli. Spring ka exception catch karte hain,
    // JPA ka nahi, taaki web layer persistence-specific type pe depend na kare
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorDto> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Concurrent modification: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "Project was modified by another request, please retry");
    }

    // Request theek thi, upstream model ne bekaar output diya — isliye 502, 500 nahi.
    // Wajah client ko bhi jaati hai, kyunki prompt badalna hi aage ka raasta hai
    @ExceptionHandler(GenerationFailedException.class)
    public ResponseEntity<ApiErrorDto> handleGenerationFailed(GenerationFailedException ex) {
        log.warn("Generation rejected: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    // Koi bhi unexpected exception — last safety net
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDto> handleUnexpected(Exception ex) {
        // Generic message to the client, full detail in the logs
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
    }
    // Common builder — DRY
    private ResponseEntity<ApiErrorDto> build(HttpStatus status, String message) {
        ApiErrorDto error = new ApiErrorDto(
                status.value(),
                status.getReasonPhrase(),
                message,
                Instant.now()
        );
        return ResponseEntity.status(status).body(error);
    }
}
