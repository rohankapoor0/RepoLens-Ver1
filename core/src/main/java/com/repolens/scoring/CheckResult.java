package com.repolens.scoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents the evaluation outcome of an individual hygiene check.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CheckResult(
        String ruleId,
        String checkName,
        boolean passed,
        int pointsEarned,
        int pointsAvailable,
        String evidence
) {}
