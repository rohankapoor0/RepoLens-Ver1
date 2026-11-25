package com.repolens.scoring;

import com.repolens.github.model.GithubRepo;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of all data collected for scoring a repository.
 */
public record RepoSnapshot(
        GithubRepo repo,
        List<String> filePaths,
        Map<String, Long> fileSizes,
        String readmeText,
        List<Instant> recentCommitDates
) {
    public RepoSnapshot {
        if (filePaths == null) {
            filePaths = Collections.emptyList();
        }
        if (fileSizes == null) {
            fileSizes = Collections.emptyMap();
        }
        if (recentCommitDates == null) {
            recentCommitDates = Collections.emptyList();
        }
    }

    public boolean hasFile(String path) {
        return filePaths.contains(path);
    }
}
