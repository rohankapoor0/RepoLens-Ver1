package com.repolens.core.planner;

import java.util.List;

/**
 * Result of the FixPlanner containing greedily ordered fixes and overall impact metrics.
 */
public record FixPlan(
        String headline,
        int distinctReposImproved,
        int totalReposCount,
        int totalPointsRecoverable,
        double totalEffort,
        List<FixItem> orderedFixes
) {}
