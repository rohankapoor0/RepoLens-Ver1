package com.repolens.scoring.intelligence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Highlight of a healthy engineering practice or clean convention present in the repository.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PositiveFinding(
        String ruleId,
        String title,
        String description,
        String category,
        String evidence
) {}
