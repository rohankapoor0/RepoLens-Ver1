package com.repolens.scoring.rules;

import com.repolens.scoring.CategoryResult;
import com.repolens.scoring.CategoryScorer;
import com.repolens.scoring.CheckResult;
import com.repolens.scoring.Deduction;
import com.repolens.scoring.RepoSnapshot;
import com.repolens.scoring.ScoreCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Evaluates engineering standards: test suite presence, CI workflows, .gitignore, and build files (25 points max).
 */
public class EngineeringHygieneScorer implements CategoryScorer {

    private static final Set<String> BUILD_FILES = Set.of(
            "pom.xml", "build.gradle", "build.gradle.kts",
            "package.json", "requirements.txt", "pipfile", "pyproject.toml",
            "cargo.toml", "go.mod", "cmakelists.txt", "makefile", "package.swift"
    );

    @Override
    public ScoreCategory getCategory() {
        return ScoreCategory.ENGINEERING_HYGIENE;
    }

    @Override
    public CategoryResult score(RepoSnapshot snapshot) {
        int points = getCategory().getMaxPoints();
        List<Deduction> deductions = new ArrayList<>();
        List<CheckResult> passedChecks = new ArrayList<>();
        List<CheckResult> failedChecks = new ArrayList<>();

        RepoLanguageContext ctx = RepoLanguageContext.from(snapshot);
        int totalPaths = snapshot.filePaths().size();

        String detectedTestPath = null;
        String detectedCiPath = null;
        String detectedGitignorePath = null;
        String detectedBuildFile = null;

        for (String path : snapshot.filePaths()) {
            String lower = path.toLowerCase();
            String filename = extractFilename(lower);

            // 1. Automated Tests Detection
            if (detectedTestPath == null && (
                    lower.startsWith("test/") || lower.startsWith("tests/") || lower.startsWith("src/test/")
                            || lower.contains("/test/") || lower.contains("/tests/")
                            || lower.startsWith("spec/") || lower.contains("/spec/")
                            || filename.endsWith("test.java") || filename.endsWith("tests.java")
                            || filename.endsWith("test.py") || filename.endsWith("_test.py")
                            || filename.endsWith("_test.go") || filename.endsWith(".test.js")
                            || filename.endsWith(".test.ts") || filename.endsWith(".spec.js")
                            || filename.endsWith(".spec.ts") || filename.endsWith("tests.swift"))) {
                detectedTestPath = path;
            }

            // 2. CI/CD Workflow in .github/workflows
            if (detectedCiPath == null && lower.startsWith(".github/workflows/") && (lower.endsWith(".yml") || lower.endsWith(".yaml"))) {
                detectedCiPath = path;
            }

            // 3. .gitignore
            if (detectedGitignorePath == null && filename.equals(".gitignore")) {
                detectedGitignorePath = path;
            }

            // 4. Dependency / Build File
            if (detectedBuildFile == null && BUILD_FILES.contains(filename)) {
                detectedBuildFile = path;
            }
        }

        // 1. Automated Tests (8 pts)
        if (detectedTestPath == null) {
            points -= 8;
            String evidence = "Inspected " + totalPaths + " file paths; no automated test files found.";
            failedChecks.add(new CheckResult("AUTOMATED_TESTS", "Automated Test Suite", false, 0, 8, evidence));
            deductions.add(new Deduction(
                    getCategory(),
                    "AUTOMATED_TESTS",
                    evidence,
                    "Automated tests verify application correctness, prevent regressions, and give contributors confidence when refactoring.",
                    8, 8,
                    "Add automated unit or integration tests using " + ctx.getIdiomaticTestRecommendation() + ".",
                    List.of(
                            "Create standard test directory (" + ctx.getIdiomaticTestRecommendation() + ")",
                            "Add test file asserting core domain logic",
                            "Configure build tool test command to execute during CI runs"
                    ),
                    ctx.generateTestSnippet(),
                    45, 8
            ));
        } else {
            passedChecks.add(new CheckResult("AUTOMATED_TESTS", "Automated Test Suite", true, 8, 8,
                    "Automated test suite detected (" + detectedTestPath + ")."));
        }

        // 2. CI/CD Workflow (7 pts)
        if (detectedCiPath == null) {
            points -= 7;
            String evidence = "Inspected " + totalPaths + " file paths; no CI/CD automation workflow found in .github/workflows/*.yml.";
            failedChecks.add(new CheckResult("CI_CD_WORKFLOW", "CI/CD Automation", false, 0, 7, evidence));
            deductions.add(new Deduction(
                    getCategory(),
                    "CI_CD_WORKFLOW",
                    evidence,
                    "Continuous integration automatically builds, tests, and validates code changes on pull requests before merging.",
                    7, 7,
                    "Add a GitHub Actions workflow (.github/workflows/ci.yml) matching " + ctx.stackType() + ".",
                    List.of(
                            "Create directory .github/workflows in repository root",
                            "Add ci.yml configuration for " + ctx.primaryLanguage(),
                            "Commit and push to trigger automated build verification"
                    ),
                    ctx.generateCiWorkflowSnippet(),
                    20, 7
            ));
        } else {
            passedChecks.add(new CheckResult("CI_CD_WORKFLOW", "CI/CD Automation", true, 7, 7,
                    "CI/CD automation workflow detected (" + detectedCiPath + ")."));
        }

        // 3. .gitignore (6 pts)
        if (detectedGitignorePath == null) {
            points -= 6;
            String evidence = "Missing .gitignore file in repository.";
            failedChecks.add(new CheckResult("GITIGNORE_PRESENT", ".gitignore Configuration", false, 0, 6, evidence));
            deductions.add(new Deduction(
                    getCategory(),
                    "GITIGNORE_PRESENT",
                    evidence,
                    "A properly configured .gitignore prevents accidental commits of sensitive credentials, build artifacts, and OS bloat.",
                    6, 6,
                    "Add a " + ctx.getGitignoreTemplateName() + " file to the repository root.",
                    List.of(
                            "Create .gitignore at repository root",
                            "Include recommended ignores for " + ctx.primaryLanguage() + " and standard build tools",
                            "Commit and push"
                    ),
                    ctx.generateGitignoreSnippet(),
                    5, 6
            ));
        } else {
            passedChecks.add(new CheckResult("GITIGNORE_PRESENT", ".gitignore Configuration", true, 6, 6,
                    ".gitignore file present at " + detectedGitignorePath + "."));
        }

        // 4. Build File (4 pts)
        if (detectedBuildFile == null) {
            points -= 4;
            String evidence = "No standard build or dependency manifest detected.";
            failedChecks.add(new CheckResult("BUILD_MANIFEST", "Build Manifest", false, 0, 4, evidence));
            deductions.add(new Deduction(
                    getCategory(),
                    "BUILD_MANIFEST",
                    evidence,
                    "A standard build manifest allows other developers and automated tools to reproduce your build environment with a single command.",
                    4, 4,
                    "Add a build configuration (e.g. " + (ctx.buildFileName() != null ? ctx.buildFileName() : "pom.xml, package.json, requirements.txt") + ") so other developers can build your project.",
                    List.of(
                            "Add build configuration file at repository root",
                            "Declare required dependencies and run configurations"
                    ),
                    ctx.buildFileName() != null ? "# Recommended build file: " + ctx.buildFileName() : null,
                    15, 4
            ));
        } else {
            passedChecks.add(new CheckResult("BUILD_MANIFEST", "Build Manifest", true, 4, 4,
                    "Standard build or dependency manifest detected (" + detectedBuildFile + ")."));
        }

        return new CategoryResult(getCategory(), Math.max(0, points), getCategory().getMaxPoints(), passedChecks, failedChecks, deductions);
    }

    private String extractFilename(String path) {
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }
}
