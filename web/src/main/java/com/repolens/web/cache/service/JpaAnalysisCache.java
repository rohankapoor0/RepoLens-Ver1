package com.repolens.web.cache.service;

import com.repolens.core.cache.AnalysisCache;
import com.repolens.core.cache.CachedAnalysis;
import com.repolens.web.cache.entity.CachedUserAnalysisEntity;
import com.repolens.web.cache.repository.CachedUserAnalysisRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Spring Data JPA implementation of {@link AnalysisCache} with configurable TTL.
 */
@Service
public class JpaAnalysisCache implements AnalysisCache {

    private final CachedUserAnalysisRepository repository;
    private final Duration ttl;

    public JpaAnalysisCache(
            CachedUserAnalysisRepository repository,
            @Value("${repolens.cache.analysis-ttl-hours:6}") long ttlHours
    ) {
        this.repository = repository;
        this.ttl = Duration.ofHours(Math.max(1, ttlHours));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CachedAnalysis> get(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        return repository.findByUsername(username.trim().toLowerCase())
                .filter(entity -> !entity.isExpired())
                .map(entity -> new CachedAnalysis(
                        entity.getUsername(),
                        entity.getEtag(),
                        entity.getDataJson(),
                        entity.getCachedAt(),
                        entity.getExpiresAt()
                ));
    }

    @Override
    @Transactional
    public void put(String username, String etag, String dataJson, Instant expiresAt) {
        if (username == null || dataJson == null) return;
        Instant expiry = expiresAt != null ? expiresAt : Instant.now().plus(ttl);
        CachedUserAnalysisEntity entity = new CachedUserAnalysisEntity(
                username.trim().toLowerCase(),
                etag,
                dataJson,
                Instant.now(),
                expiry
        );
        repository.save(entity);
    }

    @Override
    @Transactional
    public void evict(String username) {
        if (username != null) {
            repository.deleteById(username.trim().toLowerCase());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean contains(String username) {
        return get(username).isPresent();
    }
}
