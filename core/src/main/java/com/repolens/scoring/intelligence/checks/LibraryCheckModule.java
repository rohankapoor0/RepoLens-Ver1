package com.repolens.scoring.intelligence.checks;

import com.repolens.scoring.RepoSnapshot;
import com.repolens.scoring.intelligence.ArchitectureObservation;
import com.repolens.scoring.intelligence.DetectedTechnology;
import com.repolens.scoring.intelligence.ImprovementPriority;
import com.repolens.scoring.intelligence.PositiveFinding;
import com.repolens.scoring.intelligence.ProjectImprovement;
import com.repolens.scoring.intelligence.ProjectType;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Project-specific engineering intelligence for Open-Source Libraries and Reusable Packages.
 */
public class LibraryCheckModule implements ProjectCheckModule {

    @Override
    public boolean appliesTo(Set<ProjectType> types, List<DetectedTechnology> technologies) {
        return types.contains(ProjectType.LIBRARY_PACKAGE);
    }

    @Override
    public void evaluate(
            RepoSnapshot snapshot,
            Set<ProjectType> types,
            List<DetectedTechnology> technologies,
            List<ArchitectureObservation> observations,
            List<PositiveFinding> positiveFindings,
            List<ProjectImprovement> recommendations
    ) {
        List<String> paths = snapshot.filePaths();
        String readme = snapshot.readmeText() != null ? snapshot.readmeText().toLowerCase(Locale.ROOT) : "";

        // 1. Dedicated Examples Directory
        boolean hasExamples = hasPath(paths, "examples/") || hasPath(paths, "samples/") || hasPath(paths, "example/");
        if (hasExamples) {
            positiveFindings.add(new PositiveFinding(
                    "LIB_EXAMPLES_DIRECTORY",
                    "Dedicated Runnable Examples",
                    "Provides dedicated example projects and code samples demonstrating real-world library integration.",
                    "Developer Experience",
                    "Detected examples/ directory with runnable integration samples."
            ));
        } else {
            recommendations.add(new ProjectImprovement(
                    "LIB_EXAMPLES_DIRECTORY",
                    "Add an `examples/` Directory with Quickstart Samples",
                    "Consumers evaluating an open-source library prioritize copy-pasteable runnable examples over abstract API docs.",
                    "No examples/ or samples/ directory detected in repository tree.",
                    "Create an `examples/` directory containing small, runnable code samples demonstrating primary library capabilities.",
                    ImprovementPriority.MEDIUM,
                    20,
                    "examples/",
                    "mkdir -p examples\n# Add self-contained usage sample"
            ));
        }

        // 2. Changelog / Release History
        boolean hasChangelog = hasFile(paths, "CHANGELOG.md") || hasFile(paths, "CHANGES.md")
                || hasFile(paths, "HISTORY.md") || hasFile(paths, "RELEASES.md");

        if (hasChangelog) {
            positiveFindings.add(new PositiveFinding(
                    "LIB_CHANGELOG_MAINTENANCE",
                    "Versioned Release Changelog",
                    "Maintains an explicit history of breaking changes, new features, and bug fixes across releases.",
                    "Release Management",
                    "Detected CHANGELOG.md in repository root."
            ));
        } else {
            recommendations.add(new ProjectImprovement(
                    "LIB_CHANGELOG_MAINTENANCE",
                    "Maintain a CHANGELOG.md Following Keep a Changelog Standard",
                    "Library consumers need visibility into breaking changes and deprecated APIs between major/minor version bumps.",
                    "No CHANGELOG.md or release history file detected in repository root.",
                    "Add a `CHANGELOG.md` documenting recent changes under Semantic Versioning headers (e.g. [1.0.0], [Unreleased]).",
                    ImprovementPriority.LOW,
                    15,
                    "CHANGELOG.md",
                    """
                    # Changelog
                    All notable changes to this project will be documented in this file.

                    ## [1.0.0] - 2026-11-25
                    ### Added
                    - Initial public release of core library APIs
                    """.stripIndent()
            ));
        }

        // 3. Quickstart / Installation in README
        boolean hasInstallGuide = readme.contains("installation") || readme.contains("getting started")
                || readme.contains("npm i") || readme.contains("pip install") || readme.contains("<dependency>");

        if (hasInstallGuide) {
            positiveFindings.add(new PositiveFinding(
                    "LIB_INSTALLATION_GUIDE",
                    "Clear Package Installation Snippets",
                    "Documentation provides clear, copy-pasteable installation snippets for package managers.",
                    "Documentation",
                    "README includes package installation commands."
            ));
        }
    }

    private static boolean hasFile(List<String> paths, String target) {
        for (String p : paths) {
            int idx = p.lastIndexOf('/');
            String filename = idx >= 0 ? p.substring(idx + 1) : p;
            if (filename.equalsIgnoreCase(target)) return true;
        }
        return false;
    }

    private static boolean hasPath(List<String> paths, String part) {
        String lower = part.toLowerCase(Locale.ROOT);
        for (String p : paths) {
            if (p.toLowerCase(Locale.ROOT).contains(lower)) return true;
        }
        return false;
    }
}
