package com.repolens.web.cache.repository;

import com.repolens.web.cache.entity.CachedETagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CachedETagRepository extends JpaRepository<CachedETagEntity, String> {
}
