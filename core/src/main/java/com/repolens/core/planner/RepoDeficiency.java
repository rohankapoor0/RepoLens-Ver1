package com.repolens.core.planner;

import java.util.Objects;

/**
 * An atomic deficiency element in the Weighted Set Cover universe,
 * uniquely identifying a failed check in a specific repository.
 */
public record RepoDeficiency(
        String repoName,
        String checkKey,
        int pointsLost,
        String suggestedFix
) {
    public RepoDeficiency {
        Objects.requireNonNull(repoName, "repoName cannot be null");
        Objects.requireNonNull(checkKey, "checkKey cannot be null");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RepoDeficiency that)) return false;
        return repoName.equals(that.repoName) && checkKey.equals(that.checkKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repoName, checkKey);
    }
}
