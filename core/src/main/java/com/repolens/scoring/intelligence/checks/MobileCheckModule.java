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
 * Project-specific engineering intelligence for Mobile applications (iOS, Android, Flutter).
 */
public class MobileCheckModule implements ProjectCheckModule {

    @Override
    public boolean appliesTo(Set<ProjectType> types, List<DetectedTechnology> technologies) {
        return types.contains(ProjectType.MOBILE_APP);
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

        boolean isFlutter = paths.stream().anyMatch(p -> p.equalsIgnoreCase("pubspec.yaml"));
        boolean isIos = paths.stream().anyMatch(p -> p.endsWith(".xcodeproj") || p.endsWith(".xcworkspace") || p.endsWith(".swift"));

        if (isFlutter) {
            observations.add(new ArchitectureObservation(
                    "Cross-Platform Flutter Architecture",
                    "Application uses Flutter framework with Dart for unified cross-platform mobile delivery.",
                    "Mobile Architecture",
                    "Detected pubspec.yaml and Dart package layout."
            ));
        } else if (isIos) {
            observations.add(new ArchitectureObservation(
                    "Native Apple Swift Architecture",
                    "Application is engineered natively for iOS using Apple Swift and Xcode workspace tooling.",
                    "Mobile Architecture",
                    "Detected Xcode project structures and native Swift files."
            ));
        }

        // Check for dedicated asset catalogs / assets directory
        boolean hasAssets = hasPath(paths, "assets/") || hasPath(paths, "xcassets");
        if (hasAssets) {
            positiveFindings.add(new PositiveFinding(
                    "MOBILE_ASSET_MANAGEMENT",
                    "Dedicated Mobile Asset Management",
                    "Visual and font assets are structured in dedicated asset bundles or catalogs.",
                    "Asset Management",
                    "Found assets/ directory or xcassets catalog."
            ));
        }

        // Check for unit or widget tests
        boolean hasMobileTests = hasPath(paths, "test/") || hasPath(paths, "tests/") || hasPath(paths, "uitests");
        if (hasMobileTests) {
            positiveFindings.add(new PositiveFinding(
                    "MOBILE_AUTOMATED_TESTS",
                    "Mobile Unit / Widget Test Suites",
                    "Mobile workflows and view components are verified through automated tests.",
                    "Quality Assurance",
                    "Detected mobile test directories."
            ));
        } else {
            recommendations.add(new ProjectImprovement(
                    "MOBILE_AUTOMATED_TESTS",
                    "Add Widget and Screen Flow Tests",
                    "Mobile applications undergo frequent UI state transitions. Automated widget/flow tests prevent broken screens across OS versions.",
                    "No test/ or XCTest test files detected in repository.",
                    "Add unit and widget tests (e.g. `flutter test` or XCTest) verifying screen rendering and critical state changes.",
                    ImprovementPriority.HIGH,
                    30,
                    "test/",
                    "// Add automated widget/unit test"
            ));
        }
    }

    private static boolean hasPath(List<String> paths, String part) {
        String lower = part.toLowerCase(Locale.ROOT);
        for (String p : paths) {
            if (p.toLowerCase(Locale.ROOT).contains(lower)) return true;
        }
        return false;
    }
}
