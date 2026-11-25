package com.repolens.scoring.intelligence;

/**
 * Categorization of detected technologies.
 */
public enum TechnologyCategory {
    FRAMEWORK("Framework"),
    LANGUAGE("Language"),
    BUILD_TOOL("Build Tool"),
    DATABASE_ORM("Database & ORM"),
    TESTING("Testing & QA"),
    AI_ML("AI & Machine Learning"),
    CI_DEVOPS("CI / DevOps"),
    STYLING_UI("UI & Styling");

    private final String displayName;

    TechnologyCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
