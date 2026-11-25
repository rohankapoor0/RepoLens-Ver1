package com.repolens.web.controller;

import com.repolens.core.cache.CacheMetrics;
import com.repolens.github.GithubClient;
import com.repolens.github.model.RateLimitInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes current GitHub API quota and total requests saved by the cache.
 */
@RestController
@RequestMapping("/api")
public class StatusController {

    private final GithubClient githubClient;
    private final CacheMetrics cacheMetrics;

    public StatusController(GithubClient githubClient, CacheMetrics cacheMetrics) {
        this.githubClient = githubClient;
        this.cacheMetrics = cacheMetrics;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        RateLimitInfo rateLimit = githubClient.getLastRateLimitInfo();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "RepoLens");
        response.put("status", "UP");

        Map<String, Object> rateLimitMap = new LinkedHashMap<>();
        rateLimitMap.put("limit", rateLimit.limit());
        rateLimitMap.put("remaining", rateLimit.remaining());
        rateLimitMap.put("resetTime", rateLimit.resetTime() != null ? rateLimit.resetTime().toString() : null);
        rateLimitMap.put("secondsUntilReset", rateLimit.secondsUntilReset());
        rateLimitMap.put("resource", rateLimit.resource());
        response.put("rateLimit", rateLimitMap);

        Map<String, Object> cacheMap = new LinkedHashMap<>();
        cacheMap.put("savedApiRequests", cacheMetrics.getSavedApiRequests());
        cacheMap.put("cacheHits", cacheMetrics.getCacheHits());
        cacheMap.put("cacheMisses", cacheMetrics.getCacheMisses());
        cacheMap.put("bloomChecks", cacheMetrics.getBloomChecks());
        cacheMap.put("bloomConfirmedHits", cacheMetrics.getBloomConfirmedHits());
        cacheMap.put("bloomNegativeMisses", cacheMetrics.getBloomNegativeDefiniteMisses());
        cacheMap.put("bloomFalsePositives", cacheMetrics.getBloomFalsePositives());
        response.put("cacheMetrics", cacheMap);

        return ResponseEntity.ok(response);
    }
}
