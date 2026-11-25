package com.repolens.scoring;

import com.repolens.github.model.GithubRepo;
import com.repolens.scoring.intelligence.ImprovementPriority;
import com.repolens.scoring.intelligence.ProjectType;
import com.repolens.scoring.intelligence.RepoIntelligence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RepoScorerTest {

    private final RepoScorer scorer = new RepoScorer();

    @Test
    @DisplayName("Fixture 1 - Great Repo: scores 100/100, passed checks with concrete evidence, zero deductions")
    void shouldScoreGreatRepo100PointsWithZeroDeductions() {
        GithubRepo repo = new GithubRepo(
                "awesome-service", "testuser/awesome-service", "testuser",
                "A production-ready microservice demonstrating best engineering practices.",
                List.of("java", "spring-boot", "quality"), "MIT", "Java",
                120, 15, 2,
                Instant.now().minus(Duration.ofDays(5)),
                Instant.now().minus(Duration.ofDays(100)),
                Instant.now().minus(Duration.ofDays(5)),
                "main", 2048, false, false
        );

        String readme = """
                # Awesome Service
                A production-ready microservice demonstrating best engineering practices.

                ![Architecture Diagram](https://example.com/assets/diagram.png)

                ## Overview
                This service provides high-performance data processing pipelines with clean architecture.
                It adheres to strict engineering hygiene standards, automated testing, and CI/CD automation.

                ## Installation
                Clone the repository and compile using Maven:
                ```bash
                git clone https://github.com/testuser/awesome-service.git
                mvn clean install
                ```

                ## Usage
                Run the application with default configuration:
                ```bash
                java -jar target/awesome-service.jar
                curl -s http://localhost:8080/api/health
                ```
                """;

        List<String> filePaths = List.of(
                "README.md", "LICENSE", "CONTRIBUTING.md",
                ".github/ISSUE_TEMPLATE/bug_report.yml",
                ".github/PULL_REQUEST_TEMPLATE.md",
                ".github/workflows/ci.yml", ".gitignore", "pom.xml",
                "src/main/java/com/example/App.java",
                "src/test/java/com/example/AppTest.java"
        );

        Map<String, Long> fileSizes = Map.of(
                "pom.xml", 2500L,
                "src/main/java/com/example/App.java", 8000L,
                "src/test/java/com/example/AppTest.java", 4500L
        );

        List<Instant> recentCommits = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            recentCommits.add(Instant.now().minus(Duration.ofDays(i * 2)));
        }

        RepoSnapshot snapshot = new RepoSnapshot(repo, filePaths, fileSizes, readme, recentCommits);
        RepoScore score = scorer.score(snapshot);

        // Verification of 100/100 points
        assertThat(score.totalScore()).isEqualTo(100);
        assertThat(score.categoryScores().get(ScoreCategory.DOCUMENTATION)).isEqualTo(25);
        assertThat(score.categoryScores().get(ScoreCategory.LICENSING_COMMUNITY)).isEqualTo(10);
        assertThat(score.categoryScores().get(ScoreCategory.MAINTENANCE)).isEqualTo(20);
        assertThat(score.categoryScores().get(ScoreCategory.ENGINEERING_HYGIENE)).isEqualTo(25);
        assertThat(score.categoryScores().get(ScoreCategory.SAFETY)).isEqualTo(15);
        assertThat(score.categoryScores().get(ScoreCategory.STRUCTURE)).isEqualTo(5);
        assertThat(score.deductions()).isEmpty();
        assertThat(score.note()).isEqualTo(RepoScore.HYGIENE_DISCLAIMER);

        // Verification of RepoSummary
        RepoSummary summary = score.summary();
        assertThat(summary).isNotNull();
        assertThat(summary.score()).isEqualTo(100);
        assertThat(summary.gradeBand()).isEqualTo("Excellent");
        assertThat(summary.projectedScoreAfterTop3()).isEqualTo(100);
        assertThat(summary.topDeductions()).isEmpty();
        assertThat(summary.explanation()).contains("high engineering standards");

        // Verification of CategoryBreakdown and passing checks evidence
        CategoryBreakdown docBreakdown = score.categoryBreakdowns().get(ScoreCategory.DOCUMENTATION);
        assertThat(docBreakdown.pointsEarned()).isEqualTo(25);
        assertThat(docBreakdown.failedChecks()).isEmpty();
        assertThat(docBreakdown.passedChecks()).isNotEmpty();
        assertThat(docBreakdown.passedChecks().stream().map(CheckResult::evidence))
                .anyMatch(e -> e.contains("README file found with"));
    }

    @Test
    @DisplayName("Fixture 2 - Empty Repo: scores 20/100 with comprehensive actionable deductions and projected arithmetic")
    void shouldScoreEmptyRepoWithExactSubtotalsAndDeductions() {
        GithubRepo emptyRepo = new GithubRepo(
                "empty-repo", "testuser/empty-repo", "testuser",
                null, // missing description
                Collections.emptyList(), // missing topics
                null, // no license
                null, 0, 0, 0,
                null, null, null,
                "main", 0,
                true, // archived
                false
        );

        RepoSnapshot snapshot = new RepoSnapshot(emptyRepo, Collections.emptyList(), Collections.emptyMap(), null, Collections.emptyList());
        RepoScore score = scorer.score(snapshot);

        assertThat(score.totalScore()).isEqualTo(20);
        assertThat(score.categoryScores().get(ScoreCategory.DOCUMENTATION)).isEqualTo(0);
        assertThat(score.categoryScores().get(ScoreCategory.LICENSING_COMMUNITY)).isEqualTo(0);
        assertThat(score.categoryScores().get(ScoreCategory.MAINTENANCE)).isEqualTo(0);
        assertThat(score.categoryScores().get(ScoreCategory.ENGINEERING_HYGIENE)).isEqualTo(0);
        assertThat(score.categoryScores().get(ScoreCategory.SAFETY)).isEqualTo(15);
        assertThat(score.categoryScores().get(ScoreCategory.STRUCTURE)).isEqualTo(5);

        // Check specific evidence strings
        List<String> evidenceList = score.deductions().stream().map(Deduction::evidence).toList();
        assertThat(evidenceList).anyMatch(e -> e.contains("Inspected 0 repository paths; no README file (.md, .txt, .rst) found."));
        assertThat(evidenceList).anyMatch(e -> e.contains("Repository description is empty on GitHub metadata."));
        assertThat(evidenceList).anyMatch(e -> e.contains("Repository has 0 GitHub topics/tags defined."));
        assertThat(evidenceList).anyMatch(e -> e.contains("no open-source LICENSE detected."));
        assertThat(evidenceList).anyMatch(e -> e.contains("No CONTRIBUTING guidelines found in repository."));
        assertThat(evidenceList).anyMatch(e -> e.contains("No Issue or Pull Request templates found in .github/."));
        assertThat(evidenceList).anyMatch(e -> e.contains("Repository is marked as archived / read-only on GitHub."));
        assertThat(evidenceList).anyMatch(e -> e.contains("No push activity recorded on default branch."));
        assertThat(evidenceList).anyMatch(e -> e.contains("No recent commits found in commit history."));
        assertThat(evidenceList).anyMatch(e -> e.contains("no automated test files found."));
        assertThat(evidenceList).anyMatch(e -> e.contains("no CI/CD automation workflow found in .github/workflows/*.yml."));
        assertThat(evidenceList).anyMatch(e -> e.contains("Missing .gitignore file in repository."));
        assertThat(evidenceList).anyMatch(e -> e.contains("No standard build or dependency manifest detected."));

        // Verify BinaryHeap ordering by efficiency: pointsGainIfFixed / effortMinutes descending
        List<Deduction> deductions = score.deductions();
        for (int i = 0; i < deductions.size() - 1; i++) {
            double effA = (double) deductions.get(i).pointsGainIfFixed() / deductions.get(i).effortMinutes();
            double effB = (double) deductions.get(i + 1).pointsGainIfFixed() / deductions.get(i + 1).effortMinutes();
            assertThat(effA).isGreaterThanOrEqualTo(effB);
        }

        // Verify RepoSummary and projected score arithmetic
        RepoSummary summary = score.summary();
        assertThat(summary.score()).isEqualTo(20);
        assertThat(summary.gradeBand()).isEqualTo("Needs Attention");
        assertThat(summary.topDeductions()).hasSize(3);

        int sumTop3Gains = summary.topDeductions().stream().mapToInt(Deduction::pointsGainIfFixed).sum();
        int expectedProjected = Math.min(100, 20 + sumTop3Gains);
        assertThat(summary.projectedScoreAfterTop3()).isEqualTo(expectedProjected);

        // Explanatory paragraph contains 3-5 sentences
        assertThat(summary.explanation()).contains("hygiene score of 20/100");
        assertThat(summary.explanation()).contains("Needs Attention");
    }

    @Test
    @DisplayName("Fixture 3 - Messy Java Repo: detects pom.xml, generates Maven CI snippet, Java.gitignore, and JUnit test steps")
    void shouldScoreMessyJavaRepoAndProvideMavenSnippets() {
        GithubRepo repo = new GithubRepo(
                "inventory-service", "testuser/inventory-service", "testuser",
                "Java backend microservice for inventory tracking",
                List.of(), null, "Java",
                12, 1, 0,
                Instant.now().minus(Duration.ofDays(10)),
                Instant.now().minus(Duration.ofDays(60)),
                Instant.now().minus(Duration.ofDays(10)),
                "main", 50000, false, false
        );

        List<String> files = new ArrayList<>();
        files.add("pom.xml");
        for (int i = 1; i <= 20; i++) {
            files.add("InventoryClass" + i + ".java"); // flat layout
        }
        // Safety clutter
        files.add(".env");
        files.add("id_rsa");
        files.add("target/classes/App.class");
        files.add(".DS_Store");
        files.add("large_dump.bin");

        Map<String, Long> sizes = Map.of(
                "large_dump.bin", 10L * 1024L * 1024L // 10MB
        );

        String readme = "# Inventory Service\n\nRun with key ghp_SecretToken1234567890\n";

        RepoSnapshot snapshot = new RepoSnapshot(repo, files, sizes, readme, List.of(Instant.now()));
        RepoScore score = scorer.score(snapshot);

        // Safety should be 0/15
        assertThat(score.categoryScores().get(ScoreCategory.SAFETY)).isEqualTo(0);
        // Structure should be 0/5 (flat root + 10MB file)
        assertThat(score.categoryScores().get(ScoreCategory.STRUCTURE)).isEqualTo(0);

        // Engineering hygiene should fail CI and tests, detecting Java / Maven
        Deduction ciDeduction = score.deductions().stream()
                .filter(d -> "CI_CD_WORKFLOW".equals(d.ruleId()))
                .findFirst().orElseThrow();
        assertThat(ciDeduction.snippet()).contains("Java CI with Maven");
        assertThat(ciDeduction.snippet()).contains("mvn clean verify");

        Deduction gitignoreDeduction = score.deductions().stream()
                .filter(d -> "GITIGNORE_PRESENT".equals(d.ruleId()))
                .findFirst().orElseThrow();
        assertThat(gitignoreDeduction.suggestedFix()).contains("Java.gitignore");
        assertThat(gitignoreDeduction.snippet()).contains("target/");

        Deduction testDeduction = score.deductions().stream()
                .filter(d -> "AUTOMATED_TESTS".equals(d.ruleId()))
                .findFirst().orElseThrow();
        assertThat(testDeduction.suggestedFix()).contains("src/test/java with JUnit 5 and AssertJ");
        assertThat(testDeduction.snippet()).contains("org.junit.jupiter.api.Test");

        // Verify arithmetic of projected score
        RepoSummary summary = score.summary();
        int expectedProjected = Math.min(100, score.totalScore() + summary.topDeductions().stream().mapToInt(Deduction::pointsGainIfFixed).sum());
        assertThat(summary.projectedScoreAfterTop3()).isEqualTo(expectedProjected);
    }

    @Test
    @DisplayName("Fixture 4 - Python Repo without tests: detects requirements.txt, suggests pytest, generates Python CI")
    void shouldScorePythonRepoWithoutTestsAndSuggestPytest() {
        GithubRepo repo = new GithubRepo(
                "data-pipeline", "pyuser/data-pipeline", "pyuser",
                "ETL data pipeline for analytics",
                List.of("python", "etl", "analytics"), "MIT", "Python",
                45, 5, 1,
                Instant.now().minus(Duration.ofDays(2)),
                Instant.now().minus(Duration.ofDays(30)),
                Instant.now().minus(Duration.ofDays(2)),
                "main", 1500, false, false
        );

        String readme = """
                # Data Pipeline
                ETL data pipeline for analytics.

                ![Pipeline Architecture](docs/pipeline.png)

                ## Overview
                This pipeline consumes stream events and writes partitioned parquet datasets.

                ## Installation
                Install requirements:
                ```bash
                pip install -r requirements.txt
                ```

                ## Usage
                Run the pipeline:
                ```bash
                python main.py
                ```
                """;

        List<String> files = List.of(
                "README.md", "LICENSE", ".gitignore", "requirements.txt",
                "src/pipeline.py", "src/extract.py", "src/transform.py", "src/load.py"
        );

        List<Instant> commits = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            commits.add(Instant.now().minus(Duration.ofDays(i)));
        }

        RepoSnapshot snapshot = new RepoSnapshot(repo, files, Map.of(), readme, commits);
        RepoScore score = scorer.score(snapshot);

        // Missing tests (-8 pts) and contributing/issue templates (-5 pts)
        Deduction testDeduction = score.deductions().stream()
                .filter(d -> "AUTOMATED_TESTS".equals(d.ruleId()))
                .findFirst().orElseThrow();

        assertThat(testDeduction.evidence()).contains("no automated test files found.");
        assertThat(testDeduction.suggestedFix()).contains("tests/ directory with pytest");
        assertThat(testDeduction.snippet()).contains("def test_sample():");

        // Verify CI workflow check passed? In this snapshot there's no CI workflow
        Deduction ciDeduction = score.deductions().stream()
                .filter(d -> "CI_CD_WORKFLOW".equals(d.ruleId()))
                .findFirst().orElseThrow();
        assertThat(ciDeduction.snippet()).contains("Python CI");
        assertThat(ciDeduction.snippet()).contains("pytest");

        // Category breakdown confirms passed checks
        CategoryBreakdown engineering = score.categoryBreakdowns().get(ScoreCategory.ENGINEERING_HYGIENE);
        assertThat(engineering.passedChecks().stream().map(CheckResult::ruleId))
                .contains("GITIGNORE_PRESENT", "BUILD_MANIFEST");

        // Projected score arithmetic
        RepoSummary summary = score.summary();
        int sumTop3 = summary.topDeductions().stream().mapToInt(Deduction::pointsGainIfFixed).sum();
        assertThat(summary.projectedScoreAfterTop3()).isEqualTo(Math.min(100, score.totalScore() + sumTop3));
    }

    @Test
    @DisplayName("V2 Archetype 1 - React Frontend: detects React, Vite, Tailwind, lockfile, and UI test suggestions")
    void shouldClassifyAndInspectReactFrontendApp() {
        GithubRepo repo = new GithubRepo(
                "portfolio-dashboard", "reactdev/portfolio-dashboard", "reactdev",
                "Interactive React dashboard with Vite and Tailwind CSS",
                List.of("react", "vite", "frontend", "tailwindcss"), "MIT", "TypeScript",
                88, 12, 1,
                Instant.now().minus(Duration.ofDays(1)),
                Instant.now().minus(Duration.ofDays(30)),
                Instant.now().minus(Duration.ofDays(1)),
                "main", 2048, false, false
        );

        String readme = """
                # Portfolio Dashboard
                An interactive React dashboard built with Vite and Tailwind CSS.
                
                ## Installation
                ```bash
                npm install
                npm run dev
                ```
                """;

        List<String> files = List.of(
                "README.md", "LICENSE", ".gitignore", "package.json", "package-lock.json",
                "vite.config.ts", "tailwind.config.js",
                "src/App.tsx", "src/main.tsx",
                "src/components/Header.tsx", "src/components/MetricCard.tsx",
                "src/pages/Home.tsx", "src/pages/Settings.tsx"
        );

        RepoSnapshot snapshot = new RepoSnapshot(repo, files, Map.of(), readme, List.of(Instant.now()));
        RepoScore score = scorer.score(snapshot);
        RepoIntelligence intel = score.intelligence();

        assertThat(intel).isNotNull();
        assertThat(intel.primaryType()).isEqualTo(ProjectType.REACT_FRONTEND);
        assertThat(intel.technologies().stream().map(t -> t.name().toLowerCase()))
                .anyMatch(n -> n.contains("react"));

        // Verifies positive findings: component tree and lockfile detected
        assertThat(intel.positiveFindings().stream().map(p -> p.title()))
                .anyMatch(t -> t.contains("Component Architecture") || t.contains("Lockfile"));

        // Verifies tailored engineering recommendations for frontend (e.g. UI tests)
        assertThat(intel.recommendations()).isNotEmpty();
        assertThat(intel.recommendations().stream().map(r -> r.title()))
                .anyMatch(t -> t.contains("Component Unit") || t.contains(".env.example"));
    }

    @Test
    @DisplayName("V2 Archetype 2 - Spring Boot Backend: detects Maven, Spring Boot, layered architecture observations")
    void shouldClassifyAndInspectSpringBootBackendApp() {
        GithubRepo repo = new GithubRepo(
                "order-service", "backendteam/order-service", "backendteam",
                "Spring Boot REST API for managing retail customer orders",
                List.of("java", "spring-boot", "api", "microservice"), "Apache-2.0", "Java",
                150, 20, 2,
                Instant.now().minus(Duration.ofDays(3)),
                Instant.now().minus(Duration.ofDays(60)),
                Instant.now().minus(Duration.ofDays(3)),
                "main", 4096, false, false
        );

        String readme = """
                # Order Service
                Spring Boot backend service handling orders and payment webhooks.
                
                ## Setup
                ```bash
                mvn clean install
                mvn spring-boot:run
                ```
                """;

        List<String> files = List.of(
                "README.md", "LICENSE", ".gitignore", "pom.xml", "mvnw",
                "src/main/resources/application.yml",
                "src/main/java/com/example/OrderApplication.java",
                "src/main/java/com/example/controller/OrderController.java",
                "src/main/java/com/example/service/OrderService.java",
                "src/main/java/com/example/repository/OrderRepository.java",
                "src/main/java/com/example/dto/OrderRequest.java",
                "src/test/java/com/example/OrderApplicationTest.java"
        );

        RepoSnapshot snapshot = new RepoSnapshot(repo, files, Map.of(), readme, List.of(Instant.now()));
        RepoScore score = scorer.score(snapshot);
        RepoIntelligence intel = score.intelligence();

        assertThat(intel).isNotNull();
        assertThat(intel.allDetectedTypes()).contains(ProjectType.BACKEND_API);
        assertThat(intel.technologies().stream().map(t -> t.name()))
                .contains("Java", "Maven", "Spring Boot");

        // Positive findings: layered architecture and maven wrapper
        assertThat(intel.positiveFindings().stream().map(p -> p.title()))
                .anyMatch(t -> t.contains("Separation of Concerns") || t.contains("Build Tool Wrapper"));

        // Architecture summary describes the backend stack
        assertThat(intel.architectureSummary()).contains("Backend / REST API");
    }

    @Test
    @DisplayName("V2 Archetype 3 - AI / Machine Learning: detects PyTorch, Jupyter, model storage, dataset recommendations")
    void shouldClassifyAndInspectAiMlProject() {
        GithubRepo repo = new GithubRepo(
                "transformer-llm-finetuning", "mlresearch/transformer-llm-finetuning", "mlresearch",
                "PyTorch training scripts and evaluation notebooks for LLM parameter-efficient fine-tuning",
                List.of("pytorch", "deep-learning", "nlp", "llm"), "MIT", "Python",
                240, 35, 4,
                Instant.now().minus(Duration.ofDays(2)),
                Instant.now().minus(Duration.ofDays(45)),
                Instant.now().minus(Duration.ofDays(2)),
                "main", 8192, false, false
        );

        String readme = """
                # Transformer LLM Fine-Tuning
                PyTorch pipeline for LoRA fine-tuning and inference.
                
                ## Requirements
                ```bash
                pip install -r requirements.txt
                ```
                """;

        List<String> files = List.of(
                "README.md", "LICENSE", ".gitignore", "requirements.txt",
                "train.py", "inference.py", "evaluate.py",
                "notebooks/exploratory_analysis.ipynb",
                "notebooks/benchmark_metrics.ipynb",
                "data/sample.csv"
        );

        RepoSnapshot snapshot = new RepoSnapshot(repo, files, Map.of(), readme, List.of(Instant.now()));
        RepoScore score = scorer.score(snapshot);
        RepoIntelligence intel = score.intelligence();

        assertThat(intel).isNotNull();
        assertThat(intel.primaryType()).isEqualTo(ProjectType.AI_MACHINE_LEARNING);
        assertThat(intel.technologies().stream().map(t -> t.name()))
                .contains("PyTorch", "Jupyter Notebooks");

        // Tailored AI/ML recommendations: dataset documentation or weights tracking
        assertThat(intel.recommendations().stream().map(r -> r.title()))
                .anyMatch(t -> t.contains("Dataset Documentation") || t.contains("Weights Storage") || t.contains("Model Evaluation"));
    }

    @Test
    @DisplayName("V2 Archetype 4 - Open-Source Library: detects examples directory, changelog, and library best practices")
    void shouldClassifyAndInspectOpenSourceLibrary() {
        GithubRepo repo = new GithubRepo(
                "fast-cache-lib", "open-tools/fast-cache-lib", "open-tools",
                "Lightweight in-memory cache library with TTL eviction and zero external dependencies",
                List.of("library", "cache", "sdk", "in-memory"), "Apache-2.0", "Java",
                310, 42, 0,
                Instant.now().minus(Duration.ofDays(4)),
                Instant.now().minus(Duration.ofDays(90)),
                Instant.now().minus(Duration.ofDays(4)),
                "main", 1024, false, false
        );

        String readme = """
                # Fast Cache Library
                A zero-dependency in-memory cache library.
                
                ## Quickstart
                Add dependency to pom.xml:
                ```xml
                <dependency>
                    <groupId>com.opentools</groupId>
                    <artifactId>fast-cache-lib</artifactId>
                    <version>1.0.0</version>
                </dependency>
                ```
                """;

        List<String> files = List.of(
                "README.md", "LICENSE", "CHANGELOG.md", ".gitignore", "pom.xml",
                "examples/BasicUsageDemo.java",
                "examples/TtlEvictionDemo.java",
                "src/main/java/com/opentools/cache/FastCache.java",
                "src/test/java/com/opentools/cache/FastCacheTest.java"
        );

        RepoSnapshot snapshot = new RepoSnapshot(repo, files, Map.of(), readme, List.of(Instant.now()));
        RepoScore score = scorer.score(snapshot);
        RepoIntelligence intel = score.intelligence();

        assertThat(intel).isNotNull();
        assertThat(intel.allDetectedTypes()).contains(ProjectType.LIBRARY_PACKAGE);
        // Positive finding: examples directory detected
        assertThat(intel.positiveFindings().stream().map(p -> p.title()))
                .anyMatch(t -> t.contains("Examples Directory") || t.contains("Changelog"));
    }

    @Test
    @DisplayName("V2 Archetype 5 - Documentation / Static Project: correctly classified and avoids false backend penalties")
    void shouldClassifyAndInspectDocumentationStaticProject() {
        GithubRepo repo = new GithubRepo(
                "engineering-wiki", "acme-corp/engineering-wiki", "acme-corp",
                "Comprehensive architecture guides, onboarding checklists, and internal engineering documentation",
                List.of("documentation", "wiki", "handbook", "guides"), "MIT", "Markdown",
                55, 3, 0,
                Instant.now().minus(Duration.ofDays(10)),
                Instant.now().minus(Duration.ofDays(180)),
                Instant.now().minus(Duration.ofDays(10)),
                "main", 512, false, false
        );

        String readme = """
                # Acme Engineering Wiki
                Welcome to our team's engineering guides and documentation.
                """;

        List<String> files = List.of(
                "README.md", "LICENSE", ".gitignore", "mkdocs.yml",
                "docs/index.md", "docs/architecture.md", "docs/deployment.md",
                "docs/onboarding.md"
        );

        RepoSnapshot snapshot = new RepoSnapshot(repo, files, Map.of(), readme, List.of(Instant.now()));
        RepoScore score = scorer.score(snapshot);
        RepoIntelligence intel = score.intelligence();

        assertThat(intel).isNotNull();
        assertThat(intel.allDetectedTypes()).contains(ProjectType.DOCUMENTATION_PROJECT);
        assertThat(intel.architectureSummary()).contains("Documentation");
    }
}
