package com.repolens.github.model;

import java.util.Collections;
import java.util.List;

/**
 * Recursive Git tree response from GitHub.
 */
public record GithubTree(
        String sha,
        boolean truncated,
        List<GithubTreeEntry> entries
) {
    public GithubTree {
        if (entries == null) {
            entries = Collections.emptyList();
        }
    }
}
