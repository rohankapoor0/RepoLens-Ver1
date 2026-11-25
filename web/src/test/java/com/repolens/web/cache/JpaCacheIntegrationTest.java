package com.repolens.web.cache;

import com.repolens.core.cache.CachedAnalysis;
import com.repolens.core.cache.CachedRepoScoreRecord;
import com.repolens.core.cache.CacheMetrics;
import com.repolens.github.etag.CachedResponse;
import com.repolens.github.model.RateLimitInfo;
import com.repolens.web.cache.repository.CachedETagRepository;
import com.repolens.web.cache.repository.CachedRepoScoreRepository;
import com.repolens.web.cache.repository.CachedUserAnalysisRepository;
import com.repolens.web.cache.service.JpaAnalysisCache;
import com.repolens.web.cache.service.JpaETagStorage;
import com.repolens.web.cache.service.JpaRepoScoreCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class JpaCacheIntegrationTest {

    @Autowired
    private CachedRepoScoreRepository repoScoreRepository;

    @Autowired
    private CachedETagRepository etagRepository;

    @Autowired
    private CachedUserAnalysisRepository userAnalysisRepository;

    @Test
    @DisplayName("H2 persistence: RepoScoreCache stores and retrieves score records by composite key")
    void shouldPersistAndRetrieveRepoScores() {
        JpaRepoScoreCache cache = new JpaRepoScoreCache(repoScoreRepository);

        String key = CachedRepoScoreRecord.buildKey("octocat", "hello-world", "commit-123");
        CachedRepoScoreRecord record = new CachedRepoScoreRecord(
                key, "octocat", "hello-world", "commit-123",
                "{\"files\": 12}", "{\"totalScore\": 88}", Instant.now()
        );

        cache.put(record);

        Optional<CachedRepoScoreRecord> loaded = cache.get("octocat", "hello-world", "commit-123");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().scoreJson()).isEqualTo("{\"totalScore\": 88}");
        assertThat(cache.getAllRepoKeys()).contains(key);
    }

    @Test
    @DisplayName("H2 persistence: ETagStorage stores and retrieves HTTP ETags and bodies")
    void shouldPersistAndRetrieveETags() {
        JpaETagStorage etagStorage = new JpaETagStorage(etagRepository, new CacheMetrics());

        String url = "https://api.github.com/repos/octocat/hello-world";
        etagStorage.put(url, "\"etag-12345\"", "{\"name\": \"hello-world\"}", new RateLimitInfo(60, 50, Instant.now().plusSeconds(3600), 10, "core"));

        Optional<CachedResponse> cached = etagStorage.get(url);
        assertThat(cached).isPresent();
        assertThat(cached.get().etag()).isEqualTo("\"etag-12345\"");
        assertThat(cached.get().body()).contains("hello-world");
        assertThat(cached.get().rateLimitInfo().remaining()).isEqualTo(50);
    }

    @Test
    @DisplayName("H2 persistence: AnalysisCache stores user analyses and respects expiration TTL")
    void shouldPersistUserAnalysesAndHandleTtl() {
        JpaAnalysisCache analysisCache = new JpaAnalysisCache(userAnalysisRepository, 6);

        // Put active analysis
        analysisCache.put("testuser", "etag-user", "{\"repos\": [\"repo1\"]}", Instant.now().plusSeconds(3600));

        Optional<CachedAnalysis> active = analysisCache.get("testuser");
        assertThat(active).isPresent();
        assertThat(active.get().dataJson()).contains("repo1");
        assertThat(analysisCache.contains("testuser")).isTrue();

        // Put expired analysis
        analysisCache.put("expireduser", "etag-exp", "{\"repos\": []}", Instant.now().minusSeconds(100));

        Optional<CachedAnalysis> expired = analysisCache.get("expireduser");
        assertThat(expired).isEmpty(); // Expired entries are filtered out
    }
}
