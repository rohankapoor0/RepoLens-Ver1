package com.repolens.web.cache.repository;

import com.repolens.web.cache.entity.CachedUserAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CachedUserAnalysisRepository extends JpaRepository<CachedUserAnalysisEntity, String> {

    Optional<CachedUserAnalysisEntity> findByUsername(String username);
}
