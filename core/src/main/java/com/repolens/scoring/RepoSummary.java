package com.repolens.scoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.List;

/**
 * High-level summary of repository score, grade band, categories, plain-English explanation,
 * top actionable deductions, and projected score.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RepoSummary(
        int score,
        String gradeBand,
        ScoreCategory strongestCategory,
        ScoreCategory weakestCategory,
        String explanation,
        List<Deduction> topDeductions,
        int projectedScoreAfterTop3
) {
    public RepoSummary {
        if (topDeductions == null) {
            topDeductions = Collections.emptyList();
        }
    }
}
