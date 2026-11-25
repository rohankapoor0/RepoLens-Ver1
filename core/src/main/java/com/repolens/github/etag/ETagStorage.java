package com.repolens.github.etag;

import com.repolens.github.model.RateLimitInfo;

import java.util.Optional;

/**
 * Storage contract for HTTP ETags and cached bodies.
 */
public interface ETagStorage {

    /**
     * Retrieves cached ETag and body for a request URL.
     *
     * @param url target URL
     * @return cached response if present
     */
    Optional<CachedResponse> get(String url);

    /**
     * Saves or updates cached response for a request URL.
     *
     * @param url           target URL
     * @param etag          ETag header value
     * @param body          raw response body
     * @param rateLimitInfo rate limit snapshot
     */
    void put(String url, String etag, String body, RateLimitInfo rateLimitInfo);

    /**
     * Clears all cached entries.
     */
    void clear();
}
