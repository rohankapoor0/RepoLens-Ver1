package com.repolens.scoring.intelligence;

import com.repolens.scoring.RepoSnapshot;
import com.repolens.scoring.intelligence.checks.AiMlCheckModule;
import com.repolens.scoring.intelligence.checks.BackendCheckModule;
import com.repolens.scoring.intelligence.checks.FrontendCheckModule;
import com.repolens.scoring.intelligence.checks.JavaCheckModule;
import com.repolens.scoring.intelligence.checks.LibraryCheckModule;
import com.repolens.scoring.intelligence.checks.MobileCheckModule;
import com.repolens.scoring.intelligence.checks.ProjectCheckModule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrator for repository-specific engineering intelligence, archetype classification,
 * architecture inspection, and tailored recommendations.
 */
public class IntelligenceEngine {

    private final TechnologyDetector technologyDetector;
    private final RepositoryClassifier classifier;
    private final List<ProjectCheckModule> checkModules;

    public IntelligenceEngine() {
        this.technologyDetector = new TechnologyDetector();
        this.classifier = new RepositoryClassifier();
        this.checkModules = List.of(
                new AiMlCheckModule(),
                new FrontendCheckModule(),
                new BackendCheckModule(),
                new JavaCheckModule(),
                new LibraryCheckModule(),
                new MobileCheckModule()
        );
    }

    public RepoIntelligence analyze(RepoSnapshot snapshot) {
        if (snapshot == null) {
            return new RepoIntelligence(
                    ProjectType.GENERAL_SOFTWARE,
                    List.of(ProjectType.GENERAL_SOFTWARE),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    "No repository data available."
            );
        }

        // 1. Detect Technologies
        List<DetectedTechnology> technologies = technologyDetector.detect(snapshot);

        // 2. Classify Archetypes
        RepositoryClassifier.ClassificationResult classification = classifier.classify(snapshot, technologies);
        Set<ProjectType> allTypes = new LinkedHashSet<>(classification.allDetectedTypes());

        List<ArchitectureObservation> observations = new ArrayList<>();
        List<PositiveFinding> positiveFindings = new ArrayList<>();
        List<ProjectImprovement> recommendations = new ArrayList<>();

        // 3. Execute Applicable Project Check Modules
        for (ProjectCheckModule module : checkModules) {
            if (module.appliesTo(allTypes, technologies)) {
                module.evaluate(snapshot, allTypes, technologies, observations, positiveFindings, recommendations);
            }
        }

        // 4. Cross-cutting positive findings & observations
        evaluateCrossCuttingFindings(snapshot, technologies, observations, positiveFindings);

        // 5. Prioritize recommendations (CRITICAL -> HIGH -> MEDIUM -> LOW)
        recommendations.sort(Comparator.comparing(ProjectImprovement::priority)
                .thenComparingInt(ProjectImprovement::effortMinutes));

        return new RepoIntelligence(
                classification.primaryType(),
                new ArrayList<>(allTypes),
                technologies,
                observations,
                positiveFindings,
                recommendations,
                classification.summary()
        );
    }

    private void evaluateCrossCuttingFindings(
            RepoSnapshot snapshot,
            List<DetectedTechnology> technologies,
            List<ArchitectureObservation> observations,
            List<PositiveFinding> positiveFindings
    ) {
        List<String> paths = snapshot.filePaths();

        // CI/CD Workflow
        boolean hasCi = paths.stream().anyMatch(p -> p.startsWith(".github/workflows/") && (p.endsWith(".yml") || p.endsWith(".yaml")));
        if (hasCi) {
            positiveFindings.add(new PositiveFinding(
                    "CORE_CI_AUTOMATION",
                    "Continuous Integration Automation",
                    "Automated GitHub Actions workflows build, test, or validate changes on push and pull requests.",
                    "DevOps",
                    "Detected active workflow definitions in .github/workflows/."
            ));
        }

        // Clean Git Tracking
        boolean hasGitignore = paths.stream().anyMatch(p -> {
            int idx = p.lastIndexOf('/');
            String name = idx >= 0 ? p.substring(idx + 1) : p;
            return name.equalsIgnoreCase(".gitignore");
        });
        if (hasGitignore) {
            positiveFindings.add(new PositiveFinding(
                    "CORE_GITIGNORE_PRESENT",
                    "Version Control Hygiene (.gitignore)",
                    "A configured .gitignore prevents committing runtime caches, temporary binaries, and environment secrets.",
                    "Hygiene",
                    "Found .gitignore file at repository root."
            ));
        }

        // License
        boolean hasLicense = snapshot.repo() != null && snapshot.repo().license() != null && !snapshot.repo().license().isBlank();
        if (!hasLicense) {
            hasLicense = paths.stream().anyMatch(p -> p.toLowerCase().startsWith("license"));
        }
        if (hasLicense) {
            positiveFindings.add(new PositiveFinding(
                    "CORE_LICENSE_PRESENT",
                    "Open-Source Licensing",
                    "Clear licensing terms legally protect contributors and allow downstream consumers to use the code.",
                    "Licensing",
                    "Detected open source LICENSE."
            ));
        }

        // Comprehensive Readme
        String readme = snapshot.readmeText();
        if (readme != null && readme.trim().length() > 500) {
            positiveFindings.add(new PositiveFinding(
                    "CORE_README_SUBSTANCE",
                    "Substantial Documentation",
                    "README provides comprehensive guidance (>500 characters) outlining the project's purpose and usage.",
                    "Documentation",
                    "README file contains " + readme.trim().length() + " characters of documentation."
            ));
        }
    }
}
