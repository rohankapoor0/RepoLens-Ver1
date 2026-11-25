package com.repolens.scoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.List;

/**
 * Detailed breakdown of a scoring category including points earned, available,
 * and lists of passed and failed checks with concrete evidence.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CategoryBreakdown(
        ScoreCategory category,
        String categoryName,
        int pointsEarned,
        int pointsAvailable,
        List<CheckResult> passedChecks,
        List<CheckResult> failedChecks
) {
    public CategoryBreakdown {
        if (passedChecks == null) {
            passedChecks = Collections.emptyList();
        }
        if (failedChecks == null) {
            failedChecks = Collections.emptyList();
        }
    }
}
