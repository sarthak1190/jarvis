package com.jarvis.exception;

/**
 * Base exception for all Jarvis-specific errors.
 */
public class JarvisException extends RuntimeException {

    public JarvisException(String message) {
        super(message);
    }

    public JarvisException(String message, Throwable cause) {
        super(message, cause);
    }
}
