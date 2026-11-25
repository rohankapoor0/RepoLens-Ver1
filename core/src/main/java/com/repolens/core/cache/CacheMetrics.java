package com.repolens.core.cache;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe metrics tracker for cache performance, saved GitHub API calls, and Bloom Filter accuracy.
 */
public class CacheMetrics {

    private final AtomicLong savedApiRequests = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong bloomChecks = new AtomicLong(0);
    private final AtomicLong bloomNegativeDefiniteMisses = new AtomicLong(0);
    private final AtomicLong bloomConfirmedHits = new AtomicLong(0);
    private final AtomicLong bloomFalsePositives = new AtomicLong(0);

    public void incrementSavedRequests() {
        savedApiRequests.incrementAndGet();
    }

    public void recordSavedRequests(long count) {
        savedApiRequests.addAndGet(count);
    }

    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }

    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }

    public void recordBloomCheck() {
        bloomChecks.incrementAndGet();
    }

    public void recordBloomNegativeMiss() {
        bloomNegativeDefiniteMisses.incrementAndGet();
        cacheMisses.incrementAndGet();
    }

    public void recordBloomConfirmedHit() {
        bloomConfirmedHits.incrementAndGet();
        cacheHits.incrementAndGet();
    }

    public void recordBloomFalsePositive() {
        bloomFalsePositives.incrementAndGet();
        cacheMisses.incrementAndGet();
    }

    public long getSavedApiRequests() {
        return savedApiRequests.get();
    }

    public long getCacheHits() {
        return cacheHits.get();
    }

    public long getCacheMisses() {
        return cacheMisses.get();
    }

    public long getBloomChecks() {
        return bloomChecks.get();
    }

    public long getBloomNegativeDefiniteMisses() {
        return bloomNegativeDefiniteMisses.get();
    }

    public long getBloomConfirmedHits() {
        return bloomConfirmedHits.get();
    }

    public long getBloomFalsePositives() {
        return bloomFalsePositives.get();
    }

    public void reset() {
        savedApiRequests.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
        bloomChecks.set(0);
        bloomNegativeDefiniteMisses.set(0);
        bloomConfirmedHits.set(0);
        bloomFalsePositives.set(0);
    }
}
