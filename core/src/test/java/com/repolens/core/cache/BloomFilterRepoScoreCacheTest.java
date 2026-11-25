package com.repolens.core.cache;

import com.repolens.core.algorithms.BloomFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BloomFilterRepoScoreCacheTest {

    private InMemoryRepoScoreCache underlyingCache;
    private CacheMetrics metrics;
    private BloomFilterRepoScoreCache bloomCache;

    @BeforeEach
    void setUp() {
        underlyingCache = new InMemoryRepoScoreCache();
        metrics = new CacheMetrics();
        bloomCache = new BloomFilterRepoScoreCache(underlyingCache, new BloomFilter<>(100, 0.01), metrics);
    }

    @Test
    @DisplayName("Cache Miss: Negative Bloom Filter guarantee skips database lookup completely")
    void shouldSkipDatabaseOnBloomFilterNegative() {
        Optional<CachedRepoScoreRecord> result = bloomCache.get("octocat", "hello-world", "commit-aaa");

        assertThat(result).isEmpty();
        // Negative in Bloom filter guarantees absence: database was never queried!
        assertThat(underlyingCache.getReadCount()).isEqualTo(0);
        assertThat(metrics.getBloomNegativeDefiniteMisses()).isEqualTo(1);
        assertThat(metrics.getCacheMisses()).isEqualTo(1);
        assertThat(metrics.getCacheHits()).isEqualTo(0);
    }

    @Test
    @DisplayName("Cache Hit: Bloom filter positive is confirmed by database lookup and saves API requests")
    void shouldConfirmInDatabaseOnBloomFilterPositive() {
        String key = CachedRepoScoreRecord.buildKey("octocat", "hello-world", "commit-aaa");
        CachedRepoScoreRecord record = new CachedRepoScoreRecord(
                key, "octocat", "hello-world", "commit-aaa",
                "{\"files\": 10}", "{\"totalScore\": 90}", Instant.now()
        );

        // Put into cache
        bloomCache.put(record);

        // Query again: should hit
        Optional<CachedRepoScoreRecord> hit = bloomCache.get("octocat", "hello-world", "commit-aaa");

        assertThat(hit).isPresent();
        assertThat(hit.get().scoreJson()).isEqualTo("{\"totalScore\": 90}");
        // Database was queried exactly once to confirm positive
        assertThat(underlyingCache.getReadCount()).isEqualTo(1);
        assertThat(metrics.getBloomConfirmedHits()).isEqualTo(1);
        assertThat(metrics.getCacheHits()).isEqualTo(1);
        // Saved at least 4 GitHub REST API calls
        assertThat(metrics.getSavedApiRequests()).isEqualTo(4);
    }

    @Test
    @DisplayName("Changed-commit refresh: new commit SHA causes cache miss and triggers fresh evaluation")
    void shouldRefreshCacheWhenCommitChanges() {
        // Cache commit v1
        String keyV1 = CachedRepoScoreRecord.buildKey("octocat", "hello-world", "commit-111");
        CachedRepoScoreRecord recordV1 = new CachedRepoScoreRecord(
                keyV1, "octocat", "hello-world", "commit-111",
                "{}", "{\"totalScore\": 70}", Instant.now()
        );
        bloomCache.put(recordV1);

        // Query commit v1: hits
        assertThat(bloomCache.get("octocat", "hello-world", "commit-111")).isPresent();

        // Query commit v2 (new push): must be a cache miss!
        Optional<CachedRepoScoreRecord> missV2 = bloomCache.get("octocat", "hello-world", "commit-222");
        assertThat(missV2).isEmpty();

        // Store refreshed score for commit v2
        String keyV2 = CachedRepoScoreRecord.buildKey("octocat", "hello-world", "commit-222");
        CachedRepoScoreRecord recordV2 = new CachedRepoScoreRecord(
                keyV2, "octocat", "hello-world", "commit-222",
                "{}", "{\"totalScore\": 85}", Instant.now()
        );
        bloomCache.put(recordV2);

        // Now both exist and query correctly by commit
        assertThat(bloomCache.get("octocat", "hello-world", "commit-111").get().scoreJson()).contains("70");
        assertThat(bloomCache.get("octocat", "hello-world", "commit-222").get().scoreJson()).contains("85");
    }

    @Test
    @DisplayName("Bloom-then-Database Confirmation Rule: Positive MUST check DB and handle false positives safely")
    void shouldAlwaysConfirmWithDatabaseAndHandleFalsePositives() {
        String testKey = CachedRepoScoreRecord.buildKey("user", "repo", "commit-fp");

        // Manually inject key into Bloom Filter only (simulating a false positive or un-committed entry)
        bloomCache.getBloomFilter().add(testKey);

        // Bloom filter says true
        assertThat(bloomCache.getBloomFilter().mightContain(testKey)).isTrue();

        // Query cache: Bloom filter is positive, but database does NOT contain it
        Optional<CachedRepoScoreRecord> result = bloomCache.get("user", "repo", "commit-fp");

        // Must NOT skip work: returns empty and records false positive
        assertThat(result).isEmpty();
        assertThat(underlyingCache.getReadCount()).isEqualTo(1); // Confirmed in DB
        assertThat(metrics.getBloomFalsePositives()).isEqualTo(1);
        assertThat(metrics.getCacheHits()).isEqualTo(0);
        assertThat(metrics.getCacheMisses()).isEqualTo(1);
    }

    @Test
    @DisplayName("Startup seeding: populates Bloom Filter from pre-existing database records")
    void shouldSeedBloomFilterFromDatabase() {
        // Pre-populate DB directly without going through bloomCache
        String key = CachedRepoScoreRecord.buildKey("seeduser", "seedrepo", "commit-seed");
        underlyingCache.put(new CachedRepoScoreRecord(
                key, "seeduser", "seedrepo", "commit-seed",
                "{}", "{\"totalScore\": 95}", Instant.now()
        ));

        // Before seeding, Bloom filter has no knowledge
        assertThat(bloomCache.getBloomFilter().mightContain(key)).isFalse();

        // Seed Bloom Filter on startup
        bloomCache.seedFromDatabase();

        // After seeding, Bloom filter recognizes key
        assertThat(bloomCache.getBloomFilter().mightContain(key)).isTrue();
        Optional<CachedRepoScoreRecord> record = bloomCache.get("seeduser", "seedrepo", "commit-seed");
        assertThat(record).isPresent();
        assertThat(record.get().scoreJson()).contains("95");
    }

    /* ---------------- Test Doubles ---------------- */

    static class InMemoryRepoScoreCache implements RepoScoreCache {
        private final Map<String, CachedRepoScoreRecord> storage = new HashMap<>();
        private final AtomicInteger readCount = new AtomicInteger(0);

        @Override
        public Optional<CachedRepoScoreRecord> get(String owner, String repo, String commitOrPushedAt) {
            readCount.incrementAndGet();
            String key = CachedRepoScoreRecord.buildKey(owner, repo, commitOrPushedAt);
            return Optional.ofNullable(storage.get(key));
        }

        @Override
        public void put(CachedRepoScoreRecord record) {
            storage.put(record.repoKey(), record);
        }

        @Override
        public List<String> getAllRepoKeys() {
            return new ArrayList<>(storage.keySet());
        }

        @Override
        public void evict(String owner, String repo, String commitOrPushedAt) {
            storage.remove(CachedRepoScoreRecord.buildKey(owner, repo, commitOrPushedAt));
        }

        @Override
        public void clear() {
            storage.clear();
        }

        public int getReadCount() {
            return readCount.get();
        }
    }
}
