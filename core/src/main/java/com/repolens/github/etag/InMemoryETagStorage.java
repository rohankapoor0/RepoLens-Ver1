package com.repolens.github.etag;

import com.repolens.github.model.RateLimitInfo;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe implementation of {@link ETagStorage}.
 */
public class InMemoryETagStorage implements ETagStorage {

    private final ConcurrentHashMap<String, CachedResponse> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<CachedResponse> get(String url) {
        if (url == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.get(url));
    }

    @Override
    public void put(String url, String etag, String body, RateLimitInfo rateLimitInfo) {
        if (url != null && etag != null && body != null) {
            cache.put(url, new CachedResponse(etag, body, rateLimitInfo));
        }
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
