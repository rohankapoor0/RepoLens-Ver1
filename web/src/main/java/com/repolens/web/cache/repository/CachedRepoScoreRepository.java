package com.repolens.web.cache.repository;

import com.repolens.web.cache.entity.CachedRepoScoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CachedRepoScoreRepository extends JpaRepository<CachedRepoScoreEntity, String> {

    @Query("SELECT c.repoKey FROM CachedRepoScoreEntity c")
    List<String> findAllKeys();
}
