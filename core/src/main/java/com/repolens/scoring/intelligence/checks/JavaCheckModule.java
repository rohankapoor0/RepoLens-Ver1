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
 * Project-specific engineering intelligence for Java and JVM enterprise repositories.
 */
public class JavaCheckModule implements ProjectCheckModule {

    @Override
    public boolean appliesTo(Set<ProjectType> types, List<DetectedTechnology> technologies) {
        return types.contains(ProjectType.JAVA_ENTERPRISE);
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

        // 1. Maven / Gradle Standard Directory Layout
        boolean hasMainJava = hasPath(paths, "src/main/java");
        boolean hasTestJava = hasPath(paths, "src/test/java");
        boolean hasPomOrGradle = hasFile(paths, "pom.xml") || hasFile(paths, "build.gradle") || hasFile(paths, "build.gradle.kts");

        if (hasMainJava && hasPomOrGradle) {
            observations.add(new ArchitectureObservation(
                    "Standard Maven / Gradle Source Tree",
                    "Repository strictly adheres to canonical JVM layout conventions with root build manifest and isolated src/main/java package tree.",
                    "Java Standards",
                    "Detected standard src/main/java structure with build descriptor."
            ));
            positiveFindings.add(new PositiveFinding(
                    "JAVA_STANDARD_LAYOUT",
                    "Canonical Maven / Gradle Directory Layout",
                    "Project follows the standard JVM conventions, enabling zero-configuration toolchain and IDE integration.",
                    "Build & Structure",
                    "Found standard src/main/java package tree."
            ));
        }

        // 2. Unit and Integration Test Organization
        if (hasTestJava) {
            positiveFindings.add(new PositiveFinding(
                    "JAVA_TEST_SEPARATION",
                    "Isolated Test Sources in src/test/java",
                    "Test suites are cleanly isolated from production classes in src/test/java, preventing test utilities from leaking into production JARs.",
                    "Quality Assurance",
                    "Found dedicated src/test/java source tree."
            ));
        } else if (hasMainJava) {
            recommendations.add(new ProjectImprovement(
                    "JAVA_TEST_SEPARATION",
                    "Add JUnit / AssertJ Test Suite in src/test/java",
                    "Java applications without unit tests risk class-loading failures and regressions across refactoring cycles.",
                    "Detected src/main/java production sources, but no src/test/java test directory was found.",
                    "Create `src/test/java/` and add JUnit 5 test classes mirroring your production package hierarchy.",
                    ImprovementPriority.HIGH,
                    30,
                    "src/test/java/",
                    """
                    package com.example;

                    import org.junit.jupiter.api.Test;
                    import static org.assertj.core.api.Assertions.assertThat;

                    class ApplicationTest {
                        @Test
                        void contextLoads() {
                            assertThat(true).isTrue();
                        }
                    }
                    """.stripIndent()
            ));
        }

        // 3. Maven Wrapper / Gradle Wrapper Present
        boolean hasWrapper = hasFile(paths, "mvnw") || hasFile(paths, "gradlew");
        if (hasWrapper) {
            positiveFindings.add(new PositiveFinding(
                    "JAVA_BUILD_WRAPPER",
                    "Committed Build Tool Wrapper",
                    "Included wrapper script (mvnw / gradlew) guarantees builds succeed on any machine without pre-installed local build tools.",
                    "Reproducibility",
                    "Detected mvnw or gradlew wrapper script in root directory."
            ));
        } else if (hasPomOrGradle) {
            recommendations.add(new ProjectImprovement(
                    "JAVA_BUILD_WRAPPER",
                    "Add Maven or Gradle Wrapper (mvnw / gradlew)",
                    "A build wrapper pins the exact build tool version, ensuring deterministic builds across local developer machines and CI runners.",
                    "Found build descriptor (pom.xml / build.gradle), but no wrapper executable (mvnw / gradlew) was detected.",
                    "Generate and commit the wrapper: run `mvn wrapper:wrapper` (or `gradle wrapper`) and push the wrapper files to git.",
                    ImprovementPriority.LOW,
                    10,
                    "mvnw",
                    "mvn wrapper:wrapper\ngit add mvnw mvnw.cmd .mvn/\ngit commit -m \"Add Maven wrapper\""
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
