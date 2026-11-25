package com.repolens.core.cache;

import java.time.Instant;

/**
 * Immutable cached analysis record.
 */
public record CachedAnalysis(
        String username,
        String etag,
        String dataJson,
        Instant cachedAt,
        Instant expiresAt
) {
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
