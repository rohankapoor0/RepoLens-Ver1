package com.repolens.core.cache;

import com.repolens.core.algorithms.BloomFilter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * High-performance Cache decorator utilizing a hand-crafted {@link BloomFilter} as an ultra-fast
 * pre-filter in front of the database.
 * <p>
 * <h3>STRICT BLOOM FILTER COMPLIANCE (Project Hard Requirement):</h3>
 * <ul>
 *   <li><b>Negative Result:</b> Guarantees the repository/commit key is <i>definitely new / absent</i>.
 *       The database lookup is bypassed completely, saving IO operations.</li>
 *   <li><b>Positive Result:</b> Indicates the key <i>might</i> be in the database.
 *       <b>MUST ALWAYS be confirmed by an actual database lookup</b> before skipping analysis work!</li>
 * </ul>
 */
public class BloomFilterRepoScoreCache implements RepoScoreCache {

    private final RepoScoreCache underlyingCache;
    private final BloomFilter<String> bloomFilter;
    private final CacheMetrics metrics;

    public BloomFilterRepoScoreCache(RepoScoreCache underlyingCache) {
        this(underlyingCache, new BloomFilter<>(10000, 0.01), new CacheMetrics());
    }

    public BloomFilterRepoScoreCache(RepoScoreCache underlyingCache, BloomFilter<String> bloomFilter, CacheMetrics metrics) {
        this.underlyingCache = Objects.requireNonNull(underlyingCache, "underlyingCache cannot be null");
        this.bloomFilter = Objects.requireNonNull(bloomFilter, "bloomFilter cannot be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
    }

    /**
     * Seeds the in-memory Bloom Filter from existing keys stored in the database.
     * Invoked at application startup.
     */
    public void seedFromDatabase() {
        List<String> keys = underlyingCache.getAllRepoKeys();
        if (keys != null) {
            for (String key : keys) {
                bloomFilter.add(key);
            }
        }
    }

    @Override
    public Optional<CachedRepoScoreRecord> get(String owner, String repo, String commitOrPushedAt) {
        String key = CachedRepoScoreRecord.buildKey(owner, repo, commitOrPushedAt);
        metrics.recordBloomCheck();

        // Step 1: Query Bloom Filter pre-filter
        boolean mightBePresent = bloomFilter.mightContain(key);

        // Step 2a: NEGATIVE GUARANTEE -> Definitely new, skip database query!
        if (!mightBePresent) {
            metrics.recordBloomNegativeMiss();
            return Optional.empty();
        }

        // Step 2b: POSITIVE MATCH -> MUST confirm via database lookup!
        Optional<CachedRepoScoreRecord> dbResult = underlyingCache.get(owner, repo, commitOrPushedAt);

        if (dbResult.isPresent()) {
            metrics.recordBloomConfirmedHit();
            // Each cached repo score saves at least 4 GitHub API requests (repo, tree, readme, commits)
            metrics.recordSavedRequests(4);
            return dbResult;
        } else {
            // False positive handled safely: work is NOT skipped!
            metrics.recordBloomFalsePositive();
            return Optional.empty();
        }
    }

    @Override
    public void put(CachedRepoScoreRecord record) {
        if (record == null) {
            return;
        }
        underlyingCache.put(record);
        bloomFilter.add(record.repoKey());
    }

    @Override
    public List<String> getAllRepoKeys() {
        return underlyingCache.getAllRepoKeys();
    }

    @Override
    public void evict(String owner, String repo, String commitOrPushedAt) {
        underlyingCache.evict(owner, repo, commitOrPushedAt);
    }

    @Override
    public void clear() {
        underlyingCache.clear();
    }

    public BloomFilter<String> getBloomFilter() {
        return bloomFilter;
    }

    public CacheMetrics getMetrics() {
        return metrics;
    }
}
