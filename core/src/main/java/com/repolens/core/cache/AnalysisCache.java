package com.repolens.core.cache;

import java.time.Instant;
import java.util.Optional;

/**
 * Cache abstraction contract in core engine.
 * <p>
 * Core remains purely decoupled from Spring. The concrete persistence implementation
 * resides in the {@code web} module using Spring Data JPA and H2.
 */
public interface AnalysisCache {

    /**
     * Retrieves cached analysis payload by username.
     *
     * @param username github username (case-insensitive)
     * @return optional containing cached data if present and not expired
     */
    Optional<CachedAnalysis> get(String username);

    /**
     * Stores or updates analysis payload in cache.
     *
     * @param username github username
     * @param etag     HTTP ETag associated with user or repo response
     * @param dataJson serialized JSON payload
     * @param expiresAt expiration instant
     */
    void put(String username, String etag, String dataJson, Instant expiresAt);

    /**
     * Evicts entry from cache.
     *
     * @param username github username
     */
    void evict(String username);

    /**
     * Checks if username exists in cache.
     *
     * @param username github username
     * @return true if present and valid
     */
    boolean contains(String username);
}
