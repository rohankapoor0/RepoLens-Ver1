package com.repolens.web.config;

import com.repolens.core.cache.BloomFilterRepoScoreCache;
import com.repolens.core.cache.CacheMetrics;
import com.repolens.github.GithubClient;
import com.repolens.web.cache.service.JpaETagStorage;
import com.repolens.web.cache.service.JpaRepoScoreCache;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Spring configuration wiring GithubClient with H2 ETag persistence and Bloom-guarded Cache.
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheMetrics cacheMetrics() {
        return new CacheMetrics();
    }

    @Bean
    public BloomFilterRepoScoreCache bloomFilterRepoScoreCache(JpaRepoScoreCache jpaRepoScoreCache, CacheMetrics metrics) {
        return new BloomFilterRepoScoreCache(jpaRepoScoreCache, new com.repolens.core.algorithms.BloomFilter<>(10000, 0.01), metrics);
    }

    @Bean
    public ApplicationRunner cacheWarmupRunner(BloomFilterRepoScoreCache bloomCache) {
        return args -> {
            // Seed the Bloom Filter from existing H2 keys on startup
            bloomCache.seedFromDatabase();
        };
    }

    @Bean
    public GithubClient githubClient(JpaETagStorage jpaETagStorage) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        return new GithubClient(
                httpClient,
                GithubClient.DEFAULT_BASE_URL,
                resolveGithubToken(),
                jpaETagStorage
        );
    }

    private String resolveGithubToken() {
        String token = System.getenv("GITHUB_TOKEN");
        if (token != null && !token.isBlank()) {
            return token.trim();
        }

        String sysProp = System.getProperty("GITHUB_TOKEN");
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp.trim();
        }

        // Optional fallback: read from local gitignored .env file
        try {
            java.nio.file.Path envPath = java.nio.file.Path.of(".env");
            if (java.nio.file.Files.exists(envPath)) {
                for (String line : java.nio.file.Files.readAllLines(envPath)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("GITHUB_TOKEN=")) {
                        String val = trimmed.substring("GITHUB_TOKEN=".length()).trim();
                        if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                            val = val.substring(1, val.length() - 1);
                        } else if (val.startsWith("'") && val.endsWith("'") && val.length() >= 2) {
                            val = val.substring(1, val.length() - 1);
                        }
                        if (!val.isBlank()) {
                            return val;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }
}
