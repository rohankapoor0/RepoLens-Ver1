package com.repolens.scoring;

/**
 * Score categories for the deterministic 100-point RepoLens rubric.
 */
public enum ScoreCategory {
    DOCUMENTATION(25, "Documentation & Discoverability"),
    LICENSING_COMMUNITY(10, "Licensing & Community Standards"),
    MAINTENANCE(20, "Maintenance & Activity"),
    ENGINEERING_HYGIENE(25, "Engineering Hygiene"),
    SAFETY(15, "Safety & Secret Prevention"),
    STRUCTURE(5, "Repository Structure");

    private final int maxPoints;
    private final String displayName;

    ScoreCategory(int maxPoints, String displayName) {
        this.maxPoints = maxPoints;
        this.displayName = displayName;
    }

    public int getMaxPoints() {
        return maxPoints;
    }

    public String getDisplayName() {
        return displayName;
    }
}
