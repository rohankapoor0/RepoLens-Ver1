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
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller providing full quality score breakdown and deductions for a single repository.
 */
@RestController
@RequestMapping("/api")
public class RepoDetailController {

    private final GithubClient githubClient;
    private final BloomFilterRepoScoreCache repoCache;
    private final RepoScorer repoScorer;
    private final ObjectMapper objectMapper;

    public RepoDetailController(GithubClient githubClient, BloomFilterRepoScoreCache repoCache) {
        this.githubClient = githubClient;
        this.repoCache = repoCache;
        this.repoScorer = new RepoScorer();
        this.objectMapper = new ObjectMapper();
    }

    @GetMapping("/repo/{owner}/{repo}")
    public ResponseEntity<Map<String, Object>> getRepoDetail(
            @PathVariable("owner") String owner,
            @PathVariable("repo") String repoName
    ) {
        String cleanOwner = owner != null && owner.trim().startsWith("@") ? owner.trim().substring(1).trim() : (owner != null ? owner.trim() : "");
        GithubRepo repo;
        try {
            repo = githubClient.getRepo(cleanOwner, repoName);
        } catch (RateLimitException rle) {
            Optional<CachedRepoScoreRecord> fallback = repoCache.get(cleanOwner, repoName, "LATEST");
            if (fallback.isPresent()) {
                return ResponseEntity.ok(buildCachedDetailResponse(fallback.get(), rle.getMessage()));
            }
            throw rle;
        }

        String commitOrPushedAt = repo.pushedAt() != null ? repo.pushedAt().toString() : "HEAD";

        // Check Bloom + H2 cache (only if record has new schema with non-null ruleId, evidence and intelligence)
        Optional<CachedRepoScoreRecord> cachedRecord = repoCache.get(owner, repoName, commitOrPushedAt);
        if (cachedRecord.isPresent()) {
            try {
                RepoScore score = objectMapper.readValue(cachedRecord.get().scoreJson(), RepoScore.class);
                if (score.summary() != null && score.intelligence() != null && (score.deductions().isEmpty() || score.deductions().get(0).evidence() != null)) {
                    return ResponseEntity.ok(buildCachedDetailResponse(cachedRecord.get(), null));
                }
            } catch (Exception ignored) {
            }
        }

        // Fresh evaluation
        GithubTree tree;
        try {
            tree = githubClient.getTree(owner, repoName, repo.defaultBranch());
        } catch (Exception e) {
            tree = new GithubTree("", false, List.of());
        }

        List<String> filePaths = tree.entries().stream().map(GithubTreeEntry::path).toList();
        Map<String, Long> fileSizes = new HashMap<>();
        for (GithubTreeEntry entry : tree.entries()) {
            if (entry.size() != null) fileSizes.put(entry.path(), entry.size());
        }

        String readme = githubClient.getReadme(owner, repoName).orElse(null);
        List<Instant> commits = githubClient.getRecentCommitDates(owner, repoName);

        RepoSnapshot snapshot = new RepoSnapshot(repo, filePaths, fileSizes, readme, commits);
        RepoScore score = repoScorer.score(snapshot);

        // Put into cache
        try {
            String scoreJson = objectMapper.writeValueAsString(score);
            String summaryJson = objectMapper.writeValueAsString(Map.of(
                    "filesScanned", filePaths.size(),
                    "hasReadme", readme != null && !readme.isBlank()
            ));
            String key = CachedRepoScoreRecord.buildKey(owner, repoName, commitOrPushedAt);
            repoCache.put(new CachedRepoScoreRecord(key, owner, repoName, commitOrPushedAt, summaryJson, scoreJson, Instant.now()));
        } catch (Exception ignored) {}

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("repository", repo.fullName());
        response.put("owner", repo.owner());
        response.put("repo", repo.name());
        response.put("cached", false);
        response.put("totalScore", score.totalScore());
        response.put("note", score.note());
        response.put("summary", score.summary());
        response.put("intelligence", score.intelligence());

        Map<String, Object> categories = new LinkedHashMap<>();
        for (Map.Entry<ScoreCategory, Integer> entry : score.categoryScores().entrySet()) {
            categories.put(entry.getKey().name(), Map.of(
                    "score", entry.getValue(),
                    "maxPoints", entry.getKey().getMaxPoints(),
                    "categoryName", entry.getKey().getDisplayName()
            ));
        }
        response.put("categories", categories);
        response.put("deductionsCount", score.deductions().size());
        response.put("deductions", score.deductions());

        response.put("snapshot", Map.of(
                "filesScanned", filePaths.size(),
                "treeTruncated", tree.truncated(),
                "hasReadme", readme != null && !readme.isBlank(),
                "readmeLength", readme != null ? readme.length() : 0,
                "recentCommitsCount", commits.size(),
                "stars", repo.stargazersCount(),
                "forks", repo.forksCount(),
                "openIssues", repo.openIssuesCount(),
                "archived", repo.archived()
        ));

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> buildCachedDetailResponse(CachedRepoScoreRecord record, String fallbackReason) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("repository", record.owner() + "/" + record.repo());
        response.put("owner", record.owner());
        response.put("repo", record.repo());
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
            response.put("intelligence", score.intelligence());

            Map<String, Object> categories = new LinkedHashMap<>();
            for (Map.Entry<ScoreCategory, Integer> entry : score.categoryScores().entrySet()) {
                categories.put(entry.getKey().name(), Map.of(
                        "score", entry.getValue(),
                        "maxPoints", entry.getKey().getMaxPoints(),
                        "categoryName", entry.getKey().getDisplayName()
                ));
            }
            response.put("categories", categories);
            response.put("deductionsCount", score.deductions().size());
            response.put("deductions", score.deductions());
        } catch (Exception e) {
            response.put("rawScoreJson", record.scoreJson());
        }
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
                "error", "GitHub Rate Limit Exceeded",
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
