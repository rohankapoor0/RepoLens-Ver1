package com.repolens.core.cache;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for cached repository evaluations.
 * Implemented in web module via Spring Data JPA and H2 database.
 */
public interface RepoScoreCache {

    /**
     * Looks up cached score by owner, repo, and commit/pushed_at timestamp.
     */
    Optional<CachedRepoScoreRecord> get(String owner, String repo, String commitOrPushedAt);

    /**
     * Persists or updates a cached repository evaluation.
     */
    void put(CachedRepoScoreRecord record);

    /**
     * Retrieves all existing cache keys to seed the Bloom Filter at application startup.
     */
    List<String> getAllRepoKeys();

    /**
     * Evicts a single repository evaluation from cache.
     */
    void evict(String owner, String repo, String commitOrPushedAt);

    /**
     * Clears all cached repository scores.
     */
    void clear();
}
