package com.repolens.web.cache.service;

import com.repolens.core.cache.CachedRepoScoreRecord;
import com.repolens.core.cache.RepoScoreCache;
import com.repolens.web.cache.entity.CachedRepoScoreEntity;
import com.repolens.web.cache.repository.CachedRepoScoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA implementation of {@link RepoScoreCache} backed by H2 database in file mode.
 */
@Service
public class JpaRepoScoreCache implements RepoScoreCache {

    private final CachedRepoScoreRepository repository;

    public JpaRepoScoreCache(CachedRepoScoreRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CachedRepoScoreRecord> get(String owner, String repo, String commitOrPushedAt) {
        String key = CachedRepoScoreRecord.buildKey(owner, repo, commitOrPushedAt);
        return repository.findById(key).map(this::toRecord);
    }

    @Override
    @Transactional
    public void put(CachedRepoScoreRecord record) {
        if (record == null) return;
        CachedRepoScoreEntity entity = new CachedRepoScoreEntity(
                record.repoKey(),
                record.owner(),
                record.repo(),
                record.commitOrPushedAt(),
                record.snapshotSummaryJson(),
                record.scoreJson(),
                record.fetchedAt() != null ? record.fetchedAt() : Instant.now()
        );
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getAllRepoKeys() {
        return repository.findAllKeys();
    }

    @Override
    @Transactional
    public void evict(String owner, String repo, String commitOrPushedAt) {
        String key = CachedRepoScoreRecord.buildKey(owner, repo, commitOrPushedAt);
        repository.deleteById(key);
    }

    @Override
    @Transactional
    public void clear() {
        repository.deleteAll();
    }

    private CachedRepoScoreRecord toRecord(CachedRepoScoreEntity entity) {
        return new CachedRepoScoreRecord(
                entity.getRepoKey(),
                entity.getOwner(),
                entity.getRepo(),
                entity.getCommitOrPushedAt(),
                entity.getSnapshotSummaryJson(),
                entity.getScoreJson(),
                entity.getFetchedAt()
        );
    }
}
