package com.repolens.scoring.intelligence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Concrete technology or framework detected from verified repository artifacts.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DetectedTechnology(
        String name,
        TechnologyCategory category,
        Confidence confidence,
        String evidence
) {}
