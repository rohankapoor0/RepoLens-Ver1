package com.repolens.scoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.repolens.scoring.intelligence.RepoIntelligence;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive deterministic score for a repository including repository-specific engineering intelligence.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RepoScore(
        int totalScore,
        Map<ScoreCategory, Integer> categoryScores,
        Map<ScoreCategory, Integer> categoryMaxPoints,
        Map<ScoreCategory, CategoryBreakdown> categoryBreakdowns,
        List<Deduction> deductions,
        RepoSummary summary,
        RepoIntelligence intelligence,
        String note
) {
    public static final String HYGIENE_DISCLAIMER =
            "This score measures repository hygiene, engineering practices, and documentation standards, not algorithmic correctness or code quality.";

    public RepoScore {
        if (deductions == null) {
            deductions = Collections.emptyList();
        }
        if (categoryScores == null) {
            categoryScores = Collections.emptyMap();
        }
        if (categoryMaxPoints == null) {
            categoryMaxPoints = Collections.emptyMap();
        }
        if (categoryBreakdowns == null) {
            categoryBreakdowns = Collections.emptyMap();
        }
    }

    public RepoScore(
            int totalScore,
            Map<ScoreCategory, Integer> categoryScores,
            Map<ScoreCategory, Integer> categoryMaxPoints,
            Map<ScoreCategory, CategoryBreakdown> categoryBreakdowns,
            List<Deduction> deductions,
            RepoSummary summary,
            String note
    ) {
        this(totalScore, categoryScores, categoryMaxPoints, categoryBreakdowns, deductions, summary, null, note);
    }

    public RepoScore(
            int totalScore,
            Map<ScoreCategory, Integer> categoryScores,
            Map<ScoreCategory, Integer> categoryMaxPoints,
            List<Deduction> deductions,
            String note
    ) {
        this(totalScore, categoryScores, categoryMaxPoints, Collections.emptyMap(), deductions, null, null, note);
    }
}
