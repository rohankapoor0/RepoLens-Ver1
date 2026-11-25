package com.repolens.web.exception;

/**
 * Thrown when a valid GitHub user has 0 public repositories to analyze.
 */
public class NoPublicReposException extends RuntimeException {

    private final String username;

    public NoPublicReposException(String username) {
        super("GitHub user '" + username + "' has no public repositories to analyze.");
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
