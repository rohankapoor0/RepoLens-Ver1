package com.repolens.scoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.List;

/**
 * Result of scoring a single category.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CategoryResult(
        ScoreCategory category,
        int score,
        int maxPoints,
        List<CheckResult> passedChecks,
        List<CheckResult> failedChecks,
        List<Deduction> deductions
) {
    public CategoryResult {
        if (passedChecks == null) {
            passedChecks = Collections.emptyList();
        }
        if (failedChecks == null) {
            failedChecks = Collections.emptyList();
        }
        if (deductions == null) {
            deductions = Collections.emptyList();
        }
    }

    public CategoryResult(ScoreCategory category, int score, List<Deduction> deductions) {
        this(category, score, category.getMaxPoints(), Collections.emptyList(), Collections.emptyList(), deductions);
    }
}
