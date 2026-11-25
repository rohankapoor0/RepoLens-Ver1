package com.repolens.scoring;

/**
 * Common contract for a category-specific hygiene scorer.
 */
public interface CategoryScorer {

    ScoreCategory getCategory();

    CategoryResult score(RepoSnapshot snapshot);
}
