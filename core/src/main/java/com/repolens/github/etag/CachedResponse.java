package com.repolens.github.etag;

import com.repolens.github.model.RateLimitInfo;

/**
 * Cached HTTP response representation for ETag validation.
 */
public record CachedResponse(
        String etag,
        String body,
        RateLimitInfo rateLimitInfo
) {}
