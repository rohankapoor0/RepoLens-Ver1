package com.repolens.core.duplicate;

import java.util.Collections;
import java.util.List;

/**
 * Snapshot of repository metadata, file tree, and README text used for duplicate detection.
 */
public record RepoProfile(
        String repoName,
        String description,
        List<String> filePaths,
        String readmeText,
        String language
) {
    public RepoProfile {
        if (filePaths == null) filePaths = Collections.emptyList();
        if (description == null) description = "";
        if (language == null) language = "";
    }
}
