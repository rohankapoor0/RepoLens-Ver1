package com.repolens.core.planner;

import java.util.List;

/**
 * An itemized fix recommendation selected by the Greedy Weighted Set Cover algorithm.
 */
public record FixItem(
        String checkKey,
        String title,
        double effortCost,
        List<String> improvedRepos,
        int totalPointsGained,
        String suggestedAction
) {}
