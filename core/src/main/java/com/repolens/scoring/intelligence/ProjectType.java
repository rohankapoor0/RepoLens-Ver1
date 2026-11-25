package com.repolens.scoring.intelligence;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Deterministically classified high-level project archetype.
 */
@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum ProjectType {
    AI_MACHINE_LEARNING("AI / Machine Learning", "Neural networks, LLM integrations, training pipelines, and model inference", "psychology"),
    DATA_SCIENCE("Data Science & Analytics", "Data pipelines, exploratory notebooks, datasets, and analytical models", "analytics"),
    REACT_FRONTEND("React / Modern Frontend", "Single-page or SSR frontend web application with reusable component tree", "devices"),
    BACKEND_API("Backend / REST API", "Server-side services, endpoints, data controllers, and application middleware", "dns"),
    FULLSTACK_APP("Full-Stack Application", "Integrated frontend client and server-side backend services", "layers"),
    JAVA_ENTERPRISE("Java / JVM Application", "Enterprise Java/Kotlin services built with Maven or Gradle", "coffee"),
    PYTHON_APP("Python Application", "Modular Python software package, backend, or script suite", "terminal"),
    MOBILE_APP("Mobile Application", "Native or cross-platform iOS/Android mobile application", "smartphone"),
    CLI_TOOL("CLI / Developer Tool", "Command-line utility, developer tooling, or automated script suite", "build"),
    LIBRARY_PACKAGE("Open-Source Library / Package", "Reusable public library, SDK, or dependency package for developers", "inventory_2"),
    DOCUMENTATION_PROJECT("Documentation / Static Project", "Documentation repository, portfolio, or static content site", "article"),
    GENERAL_SOFTWARE("General Software Project", "Software project adhering to standard engineering hygiene", "code");

    private final String displayName;
    private final String description;
    private final String iconName;

    ProjectType(String displayName, String description, String iconName) {
        this.displayName = displayName;
        this.description = description;
        this.iconName = iconName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getIconName() {
        return iconName;
    }
}
