package com.repolens.scoring.intelligence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Concrete architecture observation grounded in file tree hierarchy and configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArchitectureObservation(
        String title,
        String observation,
        String category,
        String evidence
) {}
