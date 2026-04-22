package com.jarvis.exception;

/**
 * Exception thrown when a command is blocked by security validation.
 */
public class SecurityException extends JarvisException {

    public SecurityException(String message) {
        super(message);
    }

    public SecurityException(String message, Throwable cause) {
        super(message, cause);
    }
}
