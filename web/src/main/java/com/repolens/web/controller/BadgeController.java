package com.repolens.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repolens.core.cache.BloomFilterRepoScoreCache;
import com.repolens.core.cache.CachedRepoScoreRecord;
import com.repolens.github.GithubClient;
import com.repolens.github.model.GithubRepo;
import com.repolens.github.model.GithubTree;
import com.repolens.github.model.GithubTreeEntry;
import com.repolens.scoring.RepoScore;
import com.repolens.scoring.RepoScorer;
import com.repolens.scoring.RepoSnapshot;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller generating dynamic SVG README badges for repository hygiene scores.
 */
@RestController
public class BadgeController {

    private final GithubClient githubClient;
    private final BloomFilterRepoScoreCache repoCache;
    private final RepoScorer repoScorer;
    private final ObjectMapper objectMapper;

    public BadgeController(GithubClient githubClient, BloomFilterRepoScoreCache repoCache) {
        this.githubClient = githubClient;
        this.repoCache = repoCache;
        this.repoScorer = new RepoScorer();
        this.objectMapper = new ObjectMapper();
    }

    @GetMapping(value = "/badge/{owner}/{repo}.svg", produces = "image/svg+xml")
    public ResponseEntity<String> getRepoScoreBadge(
            @PathVariable("owner") String owner,
            @PathVariable("repo") String repoName
    ) {
        String cleanOwner = owner != null && owner.trim().startsWith("@") ? owner.trim().substring(1).trim() : (owner != null ? owner.trim() : "");
        int score = resolveScore(cleanOwner, repoName);
        String svg = generateShieldSvg("repolens", score >= 0 ? score + "/100" : "unknown", score);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("image/svg+xml"));
        headers.setCacheControl("max-age=300, s-maxage=300"); // 5 minutes cache

        return ResponseEntity.ok().headers(headers).body(svg);
    }

    private int resolveScore(String owner, String repoName) {
        // 1. Try cache lookup
        Optional<CachedRepoScoreRecord> cached = repoCache.get(owner, repoName, "LATEST");
        if (cached.isPresent()) {
            try {
                RepoScore s = objectMapper.readValue(cached.get().scoreJson(), RepoScore.class);
                return s.totalScore();
            } catch (Exception ignored) {}
        }

        // 2. Fetch repo metadata
        try {
            GithubRepo repo = githubClient.getRepo(owner, repoName);
            String commitOrPushedAt = repo.pushedAt() != null ? repo.pushedAt().toString() : "HEAD";

            Optional<CachedRepoScoreRecord> commitCached = repoCache.get(owner, repoName, commitOrPushedAt);
            if (commitCached.isPresent()) {
                RepoScore s = objectMapper.readValue(commitCached.get().scoreJson(), RepoScore.class);
                return s.totalScore();
            }

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
            RepoScore freshScore = repoScorer.score(snapshot);

            // Put in cache
            try {
                String scoreJson = objectMapper.writeValueAsString(freshScore);
                String summaryJson = objectMapper.writeValueAsString(Map.of(
                        "filesScanned", filePaths.size(),
                        "hasReadme", readme != null && !readme.isBlank()
                ));
                String key = CachedRepoScoreRecord.buildKey(owner, repoName, commitOrPushedAt);
                repoCache.put(new CachedRepoScoreRecord(key, owner, repoName, commitOrPushedAt, summaryJson, scoreJson, Instant.now()));
            } catch (Exception ignored) {}

            return freshScore.totalScore();
        } catch (Exception e) {
            return -1;
        }
    }

    private String generateShieldSvg(String label, String value, int score) {
        String color;
        if (score >= 80) color = "#4c1"; // Green
        else if (score >= 65) color = "#007ec6"; // Blue
        else if (score >= 50) color = "#dfb317"; // Yellow
        else if (score >= 0) color = "#e05d44"; // Red
        else color = "#9f9f9f"; // Gray (unknown)

        int labelWidth = 62;
        int valueWidth = 52;
        int totalWidth = labelWidth + valueWidth;

        int labelTextX = labelWidth / 2;
        int valueTextX = labelWidth + (valueWidth / 2);

        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="20" role="img" aria-label="%s: %s">
                  <title>%s: %s</title>
                  <linearGradient id="s" x2="0" y2="100%%">
                    <stop offset="0" stop-color="#bbb" stop-opacity=".1"/>
                    <stop offset="1" stop-opacity=".1"/>
                  </linearGradient>
                  <clipPath id="r">
                    <rect width="%d" height="20" rx="3" fill="#fff"/>
                  </clipPath>
                  <g clip-path="url(#r)">
                    <rect width="%d" height="20" fill="#555"/>
                    <rect x="%d" width="%d" height="20" fill="%s"/>
                    <rect width="%d" height="20" fill="url(#s)"/>
                  </g>
                  <g fill="#fff" text-anchor="middle" font-family="-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif" text-rendering="geometricPrecision" font-size="110">
                    <text aria-hidden="true" x="%d0" y="150" fill="#010101" fill-opacity=".3" transform="scale(.1)" textLength="%d0">%s</text>
                    <text x="%d0" y="140" transform="scale(.1)" fill="#fff" textLength="%d0">%s</text>
                    <text aria-hidden="true" x="%d0" y="150" fill="#010101" fill-opacity=".3" transform="scale(.1)" textLength="%d0">%s</text>
                    <text x="%d0" y="140" transform="scale(.1)" fill="#fff" textLength="%d0">%s</text>
                  </g>
                </svg>
                """.formatted(
                totalWidth, label, value,
                label, value,
                totalWidth,
                labelWidth,
                labelWidth, valueWidth, color,
                totalWidth,
                labelTextX, (labelWidth - 14), label,
                labelTextX, (labelWidth - 14), label,
                valueTextX, (valueWidth - 14), value,
                valueTextX, (valueWidth - 14), value
        );
    }
}
