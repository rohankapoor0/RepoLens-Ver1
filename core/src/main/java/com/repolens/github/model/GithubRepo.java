package com.repolens.github.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Immutable domain representation of a GitHub repository.
 */
public record GithubRepo(
        String name,
        String fullName,
        String owner,
        String description,
        List<String> topics,
        String license,
        String language,
        int stargazersCount,
        int forksCount,
        int openIssuesCount,
        Instant pushedAt,
        Instant createdAt,
        Instant updatedAt,
        String defaultBranch,
        int size,
        boolean archived,
        boolean fork
) {
    public GithubRepo {
        if (topics == null) {
            topics = Collections.emptyList();
        }
    }
}
