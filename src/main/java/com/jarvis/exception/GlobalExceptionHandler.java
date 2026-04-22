package com.jarvis.exception;

import com.jarvis.dto.JarvisResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Collections;

/**
 * Global exception handler for REST API endpoints.
 * Converts exceptions into structured JarvisResponse error objects.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<JarvisResponse> handleSecurityException(SecurityException ex) {
        log.error("Security violation: {}", ex.getMessage());
        JarvisResponse response = JarvisResponse.builder()
                .success(false)
                .responseText("I'm sorry, that command is blocked for security reasons.")
                .timestamp(LocalDateTime.now())
                .results(Collections.emptyList())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(JarvisException.class)
    public ResponseEntity<JarvisResponse> handleJarvisException(JarvisException ex) {
        log.error("Jarvis error: {}", ex.getMessage(), ex);
        JarvisResponse response = JarvisResponse.builder()
                .success(false)
                .responseText("An error occurred: " + ex.getMessage())
                .timestamp(LocalDateTime.now())
                .results(Collections.emptyList())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<JarvisResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        JarvisResponse response = JarvisResponse.builder()
                .success(false)
                .responseText("An unexpected error occurred. Please try again.")
                .timestamp(LocalDateTime.now())
                .results(Collections.emptyList())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
