package com.repolens.web.exception;

/**
 * Thrown when an IP address exceeds the hourly quota of distinct analyzed usernames.
 */
public class VisitorThrottleException extends RuntimeException {

    private final int limit;
    private final long retryAfterMinutes;

    public VisitorThrottleException(int limit, long retryAfterMinutes, String message) {
        super(message);
        this.limit = limit;
        this.retryAfterMinutes = retryAfterMinutes;
    }

    public int getLimit() {
        return limit;
    }

    public long getRetryAfterMinutes() {
        return retryAfterMinutes;
    }
}
