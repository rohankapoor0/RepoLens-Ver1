package com.repolens.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repolens.core.cache.BloomFilterRepoScoreCache;
import com.repolens.core.cache.CachedRepoScoreRecord;
import com.repolens.github.GithubClient;
import com.repolens.github.exception.GithubUnavailableException;
import com.repolens.github.exception.RateLimitException;
import com.repolens.github.exception.UserNotFoundException;
import com.repolens.github.model.GithubRepo;
import com.repolens.github.model.GithubTree;
import com.repolens.github.model.GithubTreeEntry;
import com.repolens.github.model.RateLimitInfo;
import com.repolens.scoring.CategoryBreakdown;
import com.repolens.scoring.RepoScore;
import com.repolens.scoring.RepoScorer;
import com.repolens.scoring.RepoSnapshot;
import com.repolens.scoring.ScoreCategory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller exposing deterministic scoring with BloomFilter-backed H2 caching and rate-limit fallbacks.
 */
@RestController
@RequestMapping("/api/debug")
public class DebugScoreController {

    private final GithubClient githubClient;
    private final RepoScorer repoScorer;
    private final BloomFilterRepoScoreCache cache;
    private final ObjectMapper objectMapper;

    public DebugScoreController(GithubClient githubClient, BloomFilterRepoScoreCache cache) {
        this.githubClient = githubClient;
        this.cache = cache;
        this.repoScorer = new RepoScorer();
        this.objectMapper = new ObjectMapper();
    }

    @GetMapping("/score/{owner}/{repo}")
    public ResponseEntity<Map<String, Object>> getDebugScore(
            @PathVariable("owner") String owner,
            @PathVariable("repo") String repoName,
            @RequestParam(name = "refresh", defaultValue = "false") boolean refresh
    ) {
        GithubRepo repo;
        try {
            repo = githubClient.getRepo(owner, repoName);
        } catch (RateLimitException rle) {
            Optional<CachedRepoScoreRecord> fallback = cache.get(owner, repoName, "LATEST");
            if (fallback.isPresent()) {
                return ResponseEntity.ok(buildCachedResponse(fallback.get(), rle.getMessage()));
            }
            throw rle;
        }

        String commitOrPushedAt = repo.pushedAt() != null ? repo.pushedAt().toString() : "HEAD";

        // Step 1: BloomFilter + Database Cache Check (skip if forced refresh or legacy cached score without summary)
        if (!refresh) {
            Optional<CachedRepoScoreRecord> cachedRecord = cache.get(owner, repoName, commitOrPushedAt);
            if (cachedRecord.isPresent()) {
                try {
                    RepoScore score = objectMapper.readValue(cachedRecord.get().scoreJson(), RepoScore.class);
                    if (score.summary() != null && !score.deductions().isEmpty() && score.deductions().get(0).ruleId() != null) {
                        return ResponseEntity.ok(buildCachedResponse(cachedRecord.get(), null));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // Step 2: Cache Miss / New Commit / Legacy Upgrade -> Fetch fresh from GitHub
        GithubTree tree;
        try {
            tree = githubClient.getTree(owner, repoName, repo.defaultBranch());
        } catch (Exception e) {
            tree = new GithubTree("", false, List.of());
        }

        List<String> filePaths = tree.entries().stream().map(GithubTreeEntry::path).toList();
        Map<String, Long> fileSizes = new HashMap<>();
        for (GithubTreeEntry entry : tree.entries()) {
            if (entry.size() != null) {
                fileSizes.put(entry.path(), entry.size());
            }
        }

        String readme = githubClient.getReadme(owner, repoName).orElse(null);
        List<Instant> commits = githubClient.getRecentCommitDates(owner, repoName);

        RepoSnapshot snapshot = new RepoSnapshot(repo, filePaths, fileSizes, readme, commits);
        RepoScore score = repoScorer.score(snapshot);

        // Save fresh score into H2 and Bloom Filter
        try {
            String scoreJson = objectMapper.writeValueAsString(score);
            String summaryJson = objectMapper.writeValueAsString(Map.of(
                    "filesScanned", filePaths.size(),
                    "hasReadme", readme != null && !readme.isBlank(),
                    "recentCommitsCount", commits.size()
            ));

            String key = CachedRepoScoreRecord.buildKey(owner, repoName, commitOrPushedAt);
            CachedRepoScoreRecord newRecord = new CachedRepoScoreRecord(
                    key, owner, repoName, commitOrPushedAt, summaryJson, scoreJson, Instant.now()
            );
            cache.put(newRecord);
        } catch (Exception ignored) {
            // Continue even if serialization fails
        }

        RateLimitInfo rateLimit = githubClient.getLastRateLimitInfo();
        return ResponseEntity.ok(buildFreshResponse(repo, score, filePaths.size(), tree.truncated(), readme != null, commits.size(), rateLimit));
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        return ResponseEntity.ok(Map.of(
                "savedApiRequests", cache.getMetrics().getSavedApiRequests(),
                "cacheHits", cache.getMetrics().getCacheHits(),
                "cacheMisses", cache.getMetrics().getCacheMisses(),
                "bloomChecks", cache.getMetrics().getBloomChecks(),
                "bloomConfirmedHits", cache.getMetrics().getBloomConfirmedHits(),
                "bloomNegativeMisses", cache.getMetrics().getBloomNegativeDefiniteMisses(),
                "bloomFalsePositives", cache.getMetrics().getBloomFalsePositives()
        ));
    }

    private Map<String, Object> buildCachedResponse(CachedRepoScoreRecord record, String fallbackReason) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("repository", record.owner() + "/" + record.repo());
        response.put("cached", true);
        response.put("cachedAt", record.fetchedAt().toString());
        long ageSeconds = Duration.between(record.fetchedAt(), Instant.now()).toSeconds();
        response.put("cacheAgeSeconds", ageSeconds);
        if (fallbackReason != null) {
            response.put("fallbackReason", fallbackReason);
        }

        try {
            RepoScore score = objectMapper.readValue(record.scoreJson(), RepoScore.class);
            response.put("totalScore", score.totalScore());
            response.put("note", score.note());
            response.put("summary", score.summary());

            Map<String, Object> categories = new LinkedHashMap<>();
            for (Map.Entry<ScoreCategory, Integer> entry : score.categoryScores().entrySet()) {
                CategoryBreakdown bd = score.categoryBreakdowns() != null ? score.categoryBreakdowns().get(entry.getKey()) : null;
                categories.put(entry.getKey().name(), Map.of(
                        "score", entry.getValue(),
                        "maxPoints", entry.getKey().getMaxPoints(),
                        "categoryName", entry.getKey().getDisplayName(),
                        "passedChecks", bd != null ? bd.passedChecks() : List.of(),
                        "failedChecks", bd != null ? bd.failedChecks() : List.of()
                ));
            }
            response.put("categories", categories);
            response.put("categoryBreakdowns", score.categoryBreakdowns());
            response.put("deductionsCount", score.deductions().size());
            response.put("deductions", score.deductions());
        } catch (Exception e) {
            response.put("rawScoreJson", record.scoreJson());
        }

        response.put("metrics", Map.of(
                "savedApiRequests", cache.getMetrics().getSavedApiRequests(),
                "cacheHits", cache.getMetrics().getCacheHits(),
                "cacheMisses", cache.getMetrics().getCacheMisses()
        ));
        return response;
    }

    private Map<String, Object> buildFreshResponse(
            GithubRepo repo, RepoScore score, int filesScanned, boolean truncated,
            boolean hasReadme, int commitsCount, RateLimitInfo rateLimit
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("repository", repo.fullName());
        response.put("cached", false);
        response.put("totalScore", score.totalScore());
        response.put("note", score.note());
        response.put("summary", score.summary());

        Map<String, Object> categories = new LinkedHashMap<>();
        for (Map.Entry<ScoreCategory, Integer> entry : score.categoryScores().entrySet()) {
            CategoryBreakdown bd = score.categoryBreakdowns() != null ? score.categoryBreakdowns().get(entry.getKey()) : null;
            categories.put(entry.getKey().name(), Map.of(
                    "score", entry.getValue(),
                    "maxPoints", entry.getKey().getMaxPoints(),
                    "categoryName", entry.getKey().getDisplayName(),
                    "passedChecks", bd != null ? bd.passedChecks() : List.of(),
                    "failedChecks", bd != null ? bd.failedChecks() : List.of()
            ));
        }
        response.put("categories", categories);
        response.put("categoryBreakdowns", score.categoryBreakdowns());
        response.put("deductionsCount", score.deductions().size());
        response.put("deductions", score.deductions());

        response.put("snapshot", Map.of(
                "filesScanned", filesScanned,
                "treeTruncated", truncated,
                "hasReadme", hasReadme,
                "recentCommitsCount", commitsCount
        ));

        response.put("rateLimit", Map.of(
                "limit", rateLimit.limit(),
                "remaining", rateLimit.remaining(),
                "secondsUntilReset", rateLimit.secondsUntilReset()
        ));

        response.put("metrics", Map.of(
                "savedApiRequests", cache.getMetrics().getSavedApiRequests(),
                "cacheHits", cache.getMetrics().getCacheHits(),
                "cacheMisses", cache.getMetrics().getCacheMisses()
        ));

        return response;
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "Repository Not Found",
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "error", "Rate Limit Exceeded",
                "message", ex.getMessage(),
                "resetTime", ex.getResetTime().toString(),
                "remaining", ex.getRemainingLimit()
        ));
    }

    @ExceptionHandler(GithubUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleGithubUnavailable(GithubUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "GitHub Unavailable",
                "message", ex.getMessage(),
                "statusCode", ex.getStatusCode()
        ));
    }
}
