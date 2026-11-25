package com.repolens.scoring.intelligence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.List;

/**
 * Complete repository-specific engineering intelligence audit.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RepoIntelligence(
        ProjectType primaryType,
        List<ProjectType> allDetectedTypes,
        List<DetectedTechnology> technologies,
        List<ArchitectureObservation> architectureObservations,
        List<PositiveFinding> positiveFindings,
        List<ProjectImprovement> recommendations,
        String architectureSummary
) {
    public RepoIntelligence {
        if (allDetectedTypes == null) {
            allDetectedTypes = primaryType != null ? List.of(primaryType) : List.of(ProjectType.GENERAL_SOFTWARE);
        }
        if (technologies == null) {
            technologies = Collections.emptyList();
        }
        if (architectureObservations == null) {
            architectureObservations = Collections.emptyList();
        }
        if (positiveFindings == null) {
            positiveFindings = Collections.emptyList();
        }
        if (recommendations == null) {
            recommendations = Collections.emptyList();
        }
    }
}
