package com.repolens.github.model;

import java.time.Instant;

/**
 * Encapsulates GitHub REST API rate-limit metadata extracted from HTTP response headers.
 */
public record RateLimitInfo(
        int limit,
        int remaining,
        Instant resetTime,
        int used,
        String resource
) {
    public static final RateLimitInfo UNKNOWN = new RateLimitInfo(60, 60, Instant.now().plusSeconds(3600), 0, "core");

    public boolean isExhausted() {
        return remaining <= 0;
    }

    public long secondsUntilReset() {
        if (resetTime == null) {
            return 0;
        }
        long diff = resetTime.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, diff);
    }
}
