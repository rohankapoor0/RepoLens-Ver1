package com.repolens.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.repolens.scoring.ScoreCategory;

import java.time.Instant;
import java.util.Map;

/**
 * Summary evaluation of a single repository within a user analysis profile.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvaluatedRepo(
        int rank,
        String name,
        String fullName,
        String owner,
        int score,
        String description,
        String language,
        String projectType,
        int stars,
        int forks,
        int openIssues,
        Instant pushedAt,
        boolean archived,
        Map<ScoreCategory, Integer> categoryScores,
        int deductionsCount,
        boolean cached
) {
    public EvaluatedRepo(
            int rank, String name, String fullName, String owner, int score,
            String description, String language, int stars, int forks,
            int openIssues, Instant pushedAt, boolean archived,
            Map<ScoreCategory, Integer> categoryScores, int deductionsCount, boolean cached
    ) {
        this(rank, name, fullName, owner, score, description, language, "General Software", stars, forks, openIssues, pushedAt, archived, categoryScores, deductionsCount, cached);
    }
}
