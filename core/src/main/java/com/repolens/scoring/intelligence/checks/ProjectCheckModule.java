package com.repolens.scoring.intelligence.checks;

import com.repolens.scoring.RepoSnapshot;
import com.repolens.scoring.intelligence.ArchitectureObservation;
import com.repolens.scoring.intelligence.DetectedTechnology;
import com.repolens.scoring.intelligence.PositiveFinding;
import com.repolens.scoring.intelligence.ProjectImprovement;
import com.repolens.scoring.intelligence.ProjectType;

import java.util.List;
import java.util.Set;

/**
 * Contract for archetype-specific engineering quality and architecture inspection.
 */
public interface ProjectCheckModule {

    /**
     * Determines whether this check module is relevant to the classified project types.
     */
    boolean appliesTo(Set<ProjectType> types, List<DetectedTechnology> technologies);

    /**
     * Evaluates repository artifacts and appends observations, positive findings, and specific recommendations.
     */
    void evaluate(
            RepoSnapshot snapshot,
            Set<ProjectType> types,
            List<DetectedTechnology> technologies,
            List<ArchitectureObservation> observations,
            List<PositiveFinding> positiveFindings,
            List<ProjectImprovement> recommendations
    );
}
