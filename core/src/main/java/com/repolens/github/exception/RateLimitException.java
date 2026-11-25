package com.repolens.github.exception;

import java.time.Instant;

/**
 * Thrown when GitHub rate limits are exhausted (HTTP 403 / 429).
 */
public class RateLimitException extends RuntimeException {

    private final Instant resetTime;
    private final int remainingLimit;

    public RateLimitException(Instant resetTime, int remainingLimit, String message) {
        super(message);
        this.resetTime = resetTime;
        this.remainingLimit = remainingLimit;
    }

    public Instant getResetTime() {
        return resetTime;
    }

    public int getRemainingLimit() {
        return remainingLimit;
    }
}
