package com.repolens.web.exception;

/**
 * Thrown when a supplied username violates GitHub's naming conventions.
 */
public class InvalidUsernameException extends RuntimeException {

    private final String username;

    public InvalidUsernameException(String username, String message) {
        super(message);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
