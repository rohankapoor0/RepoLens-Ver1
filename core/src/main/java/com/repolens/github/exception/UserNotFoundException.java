package com.repolens.github.exception;

/**
 * Thrown when the requested GitHub user cannot be found (HTTP 404).
 */
public class UserNotFoundException extends RuntimeException {

    private final String username;

    public UserNotFoundException(String username) {
        super("GitHub user '" + username + "' does not exist or has no accessible public profile.");
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
