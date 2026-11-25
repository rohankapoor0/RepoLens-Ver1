package com.repolens.github.exception;

/**
 * Thrown when GitHub REST API is unavailable or returns 5xx server errors.
 */
public class GithubUnavailableException extends RuntimeException {

    private final int statusCode;

    public GithubUnavailableException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
