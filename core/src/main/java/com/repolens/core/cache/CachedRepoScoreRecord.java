package com.repolens.core.cache;

import java.time.Instant;

/**
 * Immutable record representing a cached repository score.
 * Keyed by owner, repo, and commit SHA / latest pushed_at timestamp.
 */
public record CachedRepoScoreRecord(
        String repoKey,
        String owner,
        String repo,
        String commitOrPushedAt,
        String snapshotSummaryJson,
        String scoreJson,
        Instant fetchedAt
) {
    public static String buildKey(String owner, String repo, String commitOrPushedAt) {
        String cleanOwner = (owner != null) ? owner.trim().toLowerCase() : "unknown";
        String cleanRepo = (repo != null) ? repo.trim().toLowerCase() : "unknown";
        String cleanCommit = (commitOrPushedAt != null && !commitOrPushedAt.isBlank()) ? commitOrPushedAt.trim() : "HEAD";
        return cleanOwner + "/" + cleanRepo + "@" + cleanCommit;
    }
}
