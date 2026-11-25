package com.repolens.scoring.intelligence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Project-specific, actionable engineering recommendation grounded in verified repository evidence.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectImprovement(
        String ruleId,
        String title,
        String whyItMatters,
        String evidence,
        String suggestedAction,
        ImprovementPriority priority,
        int effortMinutes,
        String relevantFilePath,
        String snippet
) {}
