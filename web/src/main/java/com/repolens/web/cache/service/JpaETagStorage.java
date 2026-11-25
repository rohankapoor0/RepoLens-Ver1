package com.repolens.web.cache.service;

import com.repolens.core.cache.CacheMetrics;
import com.repolens.github.etag.CachedResponse;
import com.repolens.github.etag.ETagStorage;
import com.repolens.github.model.RateLimitInfo;
import com.repolens.web.cache.entity.CachedETagEntity;
import com.repolens.web.cache.repository.CachedETagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Spring Data JPA implementation of {@link ETagStorage} saving HTTP ETags to H2 database.
 */
@Service
public class JpaETagStorage implements ETagStorage {

    private final CachedETagRepository repository;
    private final CacheMetrics metrics;

    public JpaETagStorage(CachedETagRepository repository, CacheMetrics metrics) {
        this.repository = repository;
        this.metrics = metrics;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CachedResponse> get(String url) {
        if (url == null) return Optional.empty();
        return repository.findById(url).map(entity -> {
            RateLimitInfo rl = RateLimitInfo.UNKNOWN;
            if (entity.getRateLimitLimit() != null && entity.getRateLimitRemaining() != null) {
                rl = new RateLimitInfo(
                        entity.getRateLimitLimit(),
                        entity.getRateLimitRemaining(),
                        entity.getRateLimitReset() != null ? entity.getRateLimitReset() : Instant.now().plusSeconds(3600),
                        0,
                        "core"
                );
            }
            return new CachedResponse(entity.getEtag(), entity.getBody(), rl);
        });
    }

    @Override
    @Transactional
    public void put(String url, String etag, String body, RateLimitInfo rateLimitInfo) {
        if (url == null || etag == null || body == null) return;
        CachedETagEntity entity = new CachedETagEntity(
                url, etag, body,
                rateLimitInfo != null ? rateLimitInfo.limit() : null,
                rateLimitInfo != null ? rateLimitInfo.remaining() : null,
                rateLimitInfo != null ? rateLimitInfo.resetTime() : null,
                Instant.now()
        );
        repository.save(entity);
    }

    @Override
    @Transactional
    public void clear() {
        repository.deleteAll();
    }
}
