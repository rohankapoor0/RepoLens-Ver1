package com.repolens.scoring.rules;

import com.repolens.github.model.GithubRepo;
import com.repolens.scoring.RepoSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic language, framework, and build tool detector for a repository snapshot.
 */
public class RepoLanguageContext {

    public enum StackType {
        MAVEN, GRADLE, NPM, PYTHON, SWIFT, GO, RUST, GENERIC
    }

    private final String repoName;
    private final String owner;
    private final String description;
    private final String primaryLanguage;
    private final StackType stackType;
    private final String buildFileName;

    private RepoLanguageContext(
            String repoName,
            String owner,
            String description,
            String primaryLanguage,
            StackType stackType,
            String buildFileName
    ) {
        this.repoName = repoName;
        this.owner = owner;
        this.description = description;
        this.primaryLanguage = primaryLanguage;
        this.stackType = stackType;
        this.buildFileName = buildFileName;
    }

    public static RepoLanguageContext from(RepoSnapshot snapshot) {
        GithubRepo repo = snapshot.repo();
        String name = repo != null && repo.name() != null ? repo.name() : "repository";
        String owner = repo != null && repo.owner() != null ? repo.owner() : "user";
        String desc = repo != null && repo.description() != null ? repo.description() : "";

        String lang = repo != null && repo.language() != null ? repo.language().trim() : "";
        List<String> paths = snapshot.filePaths();

        // 1. Detect build files from tree
        boolean hasPom = hasFile(paths, "pom.xml");
        boolean hasGradle = hasFile(paths, "build.gradle") || hasFile(paths, "build.gradle.kts");
        boolean hasNpm = hasFile(paths, "package.json");
        boolean hasPython = hasFile(paths, "requirements.txt") || hasFile(paths, "pyproject.toml")
                || hasFile(paths, "pipfile") || hasFile(paths, "setup.py");
        boolean hasSwift = hasFile(paths, "package.swift");
        boolean hasGo = hasFile(paths, "go.mod");
        boolean hasRust = hasFile(paths, "cargo.toml");

        StackType stack;
        String buildFile;

        if (hasPom) {
            stack = StackType.MAVEN;
            buildFile = "pom.xml";
            if (lang.isEmpty()) lang = "Java";
        } else if (hasGradle) {
            stack = StackType.GRADLE;
            buildFile = hasFile(paths, "build.gradle.kts") ? "build.gradle.kts" : "build.gradle";
            if (lang.isEmpty()) lang = "Java";
        } else if (hasNpm) {
            stack = StackType.NPM;
            buildFile = "package.json";
            if (lang.isEmpty()) lang = "JavaScript";
        } else if (hasPython) {
            stack = StackType.PYTHON;
            buildFile = hasFile(paths, "pyproject.toml") ? "pyproject.toml" : "requirements.txt";
            if (lang.isEmpty()) lang = "Python";
        } else if (hasSwift) {
            stack = StackType.SWIFT;
            buildFile = "Package.swift";
            if (lang.isEmpty()) lang = "Swift";
        } else if (hasGo) {
            stack = StackType.GO;
            buildFile = "go.mod";
            if (lang.isEmpty()) lang = "Go";
        } else if (hasRust) {
            stack = StackType.RUST;
            buildFile = "Cargo.toml";
            if (lang.isEmpty()) lang = "Rust";
        } else {
            // Check repo.language() hints
            String lowerLang = lang.toLowerCase(Locale.ROOT);
            if (lowerLang.contains("java") || lowerLang.contains("kotlin") || lowerLang.contains("scala")) {
                stack = StackType.MAVEN;
                buildFile = "pom.xml";
            } else if (lowerLang.contains("python")) {
                stack = StackType.PYTHON;
                buildFile = "requirements.txt";
            } else if (lowerLang.contains("javascript") || lowerLang.contains("typescript") || lowerLang.contains("node")) {
                stack = StackType.NPM;
                buildFile = "package.json";
            } else if (lowerLang.contains("swift")) {
                stack = StackType.SWIFT;
                buildFile = "Package.swift";
            } else if (lowerLang.contains("go")) {
                stack = StackType.GO;
                buildFile = "go.mod";
            } else if (lowerLang.contains("rust")) {
                stack = StackType.RUST;
                buildFile = "Cargo.toml";
            } else {
                stack = StackType.GENERIC;
                buildFile = null;
            }
        }

        if (lang.isEmpty()) {
            lang = "Generic";
        }

        return new RepoLanguageContext(name, owner, desc, lang, stack, buildFile);
    }

    private static boolean hasFile(List<String> paths, String target) {
        for (String p : paths) {
            int idx = p.lastIndexOf('/');
            String filename = idx >= 0 ? p.substring(idx + 1) : p;
            if (filename.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    public String repoName() {
        return repoName;
    }

    public String owner() {
        return owner;
    }

    public String description() {
        return description;
    }

    public String primaryLanguage() {
        return primaryLanguage;
    }

    public StackType stackType() {
        return stackType;
    }

    public String buildFileName() {
        return buildFileName;
    }

    /**
     * Generates a minimal GitHub Actions CI workflow matching the detected stack.
     */
    public String generateCiWorkflowSnippet() {
        return switch (stackType) {
            case MAVEN -> """
                    name: Java CI with Maven
                    on: [push, pull_request]
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - uses: actions/checkout@v4
                          - name: Set up JDK 17
                            uses: actions/setup-java@v4
                            with:
                              java-version: '17'
                              distribution: 'temurin'
                          - name: Build with Maven
                            run: mvn clean verify
                    """.stripIndent();
            case GRADLE -> """
                    name: Java CI with Gradle
                    on: [push, pull_request]
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - uses: actions/checkout@v4
                          - name: Set up JDK 17
                            uses: actions/setup-java@v4
                            with:
                              java-version: '17'
                              distribution: 'temurin'
                          - name: Build with Gradle
                            run: ./gradlew build
                    """.stripIndent();
            case NPM -> """
                    name: Node.js CI
                    on: [push, pull_request]
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - uses: actions/checkout@v4
                          - name: Use Node.js 20
                            uses: actions/setup-node@v4
                            with:
                              node-version: 20
                              cache: 'npm'
                          - run: npm ci
                          - run: npm test
                    """.stripIndent();
            case PYTHON -> """
                    name: Python CI
                    on: [push, pull_request]
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - uses: actions/checkout@v4
                          - name: Set up Python
                            uses: actions/setup-python@v5
                            with:
                              python-version: '3.11'
                          - name: Install dependencies
                            run: |
                              python -m pip install --upgrade pip
                              if [ -f requirements.txt ]; then pip install -r requirements.txt; fi
                              pip install pytest
                          - name: Test with pytest
                            run: pytest
                    """.stripIndent();
            case SWIFT -> """
                    name: Swift CI
                    on: [push, pull_request]
                    jobs:
                      build:
                        runs-on: macos-latest
                        steps:
                          - uses: actions/checkout@v4
                          - name: Build and Test
                            run: swift test
                    """.stripIndent();
            case GO -> """
                    name: Go CI
                    on: [push, pull_request]
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - uses: actions/checkout@v4
                          - name: Set up Go
                            uses: actions/setup-go@v5
                            with:
                              go-version: '1.21'
                          - name: Test
                            run: go test -v ./...
                    """.stripIndent();
            case RUST -> """
                    name: Rust CI
                    on: [push, pull_request]
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - uses: actions/checkout@v4
                          - name: Cargo test
                            run: cargo test --verbose
                    """.stripIndent();
            default -> """
                    name: CI
                    on: [push, pull_request]
                    jobs:
                      build:
                        runs-on: ubuntu-latest
                        steps:
                          - uses: actions/checkout@v4
                          - name: Run build and test
                            run: echo "Configure project build and test commands here"
                    """.stripIndent();
        };
    }

    /**
     * Recommends language-specific .gitignore template name and provides a starter snippet.
     */
    public String getGitignoreTemplateName() {
        return switch (stackType) {
            case MAVEN, GRADLE -> "Java.gitignore";
            case NPM -> "Node.gitignore";
            case PYTHON -> "Python.gitignore";
            case SWIFT -> "Swift.gitignore";
            case GO -> "Go.gitignore";
            case RUST -> "Rust.gitignore";
            default -> "github/gitignore (Language-specific template)";
        };
    }

    public String generateGitignoreSnippet() {
        return switch (stackType) {
            case MAVEN, GRADLE -> """
                    target/
                    *.class
                    .idea/
                    *.iml
                    .gradle/
                    build/
                    .DS_Store
                    """.stripIndent();
            case NPM -> """
                    node_modules/
                    dist/
                    build/
                    .env
                    .env.local
                    npm-debug.log*
                    yarn-error.log*
                    .DS_Store
                    """.stripIndent();
            case PYTHON -> """
                    __pycache__/
                    *.py[cod]
                    *$py.class
                    .env
                    .venv
                    env/
                    venv/
                    .pytest_cache/
                    .coverage
                    .DS_Store
                    """.stripIndent();
            case SWIFT -> """
                    .build/
                    Packages/
                    *.xcodeproj
                    *.xcworkspace
                    xcuserdata/
                    .DS_Store
                    """.stripIndent();
            case GO -> """
                    bin/
                    dist/
                    vendor/
                    *.exe
                    .DS_Store
                    """.stripIndent();
            case RUST -> """
                    target/
                    **/*.rs.bk
                    Cargo.lock
                    .DS_Store
                    """.stripIndent();
            default -> """
                    *.log
                    .env
                    dist/
                    build/
                    .DS_Store
                    """.stripIndent();
        };
    }

    /**
     * Recommends idiomatic test directory and framework for the language.
     */
    public String getIdiomaticTestRecommendation() {
        return switch (stackType) {
            case MAVEN, GRADLE -> "src/test/java with JUnit 5 and AssertJ";
            case NPM -> "__tests__/ or *.test.js with Jest / Vitest";
            case PYTHON -> "tests/ directory with pytest";
            case SWIFT -> "Tests/ directory with XCTest (swift test)";
            case GO -> "*_test.go with standard testing package (go test ./...)";
            case RUST -> "tests/ integration tests or inline #[cfg(test)] with cargo test";
            default -> "tests/ directory with language test runner";
        };
    }

    public String generateTestSnippet() {
        return switch (stackType) {
            case MAVEN, GRADLE -> """
                    package com.example;

                    import org.junit.jupiter.api.Test;
                    import static org.assertj.core.api.Assertions.assertThat;

                    class AppTest {
                        @Test
                        void sampleTest() {
                            assertThat(1 + 1).isEqualTo(2);
                        }
                    }
                    """.stripIndent();
            case NPM -> """
                    describe('Application Tests', () => {
                        it('should run sample test assertion', () => {
                            expect(1 + 1).toBe(2);
                        });
                    });
                    """.stripIndent();
            case PYTHON -> """
                    def test_sample():
                        assert 1 + 1 == 2
                    """.stripIndent();
            case SWIFT -> """
                    import XCTest
                    @testable import App

                    final class AppTests: XCTestCase {
                        func testExample() throws {
                            XCTAssertEqual(1 + 1, 2)
                        }
                    }
                    """.stripIndent();
            case GO -> """
                    package main

                    import "testing"

                    func TestSample(t *testing.T) {
                        if 1+1 != 2 {
                            t.Errorf("Expected 2, got %d", 1+1)
                        }
                    }
                    """.stripIndent();
            default -> """
                    // Add your first automated test here
                    """.stripIndent();
        };
    }

    /**
     * Generates a complete README skeleton pre-filled with repo name, description, and missing sections.
     */
    public String generateReadmeSkeleton() {
        String title = repoName.isEmpty() ? "My Project" : repoName;
        String desc = description.isBlank() ? "A software engineering project built with " + primaryLanguage + "." : description;

        String installCmd = switch (stackType) {
            case MAVEN -> "mvn clean install";
            case GRADLE -> "./gradlew build";
            case NPM -> "npm install";
            case PYTHON -> "pip install -r requirements.txt";
            case SWIFT -> "swift build";
            case GO -> "go build ./...";
            case RUST -> "cargo build --release";
            default -> "# Run build or setup command";
        };

        String runCmd = switch (stackType) {
            case MAVEN -> "mvn spring-boot:run # or java -jar target/*.jar";
            case GRADLE -> "./gradlew bootRun";
            case NPM -> "npm start";
            case PYTHON -> "python main.py";
            case SWIFT -> "swift run";
            case GO -> "go run main.go";
            case RUST -> "cargo run";
            default -> "# Execute main binary or script";
        };

        return """
                # %s

                > %s

                ## Overview
                %s

                ## Installation
                Clone the repository and install dependencies:
                ```bash
                git clone https://github.com/%s/%s.git
                cd %s
                %s
                ```

                ## Usage
                Run the application:
                ```bash
                %s
                ```

                ## Screenshots
                Add architecture diagrams or screenshots of the application in action:
                <!-- ![Screenshot](docs/screenshot.png) -->

                ## License
                This project is licensed under the MIT License - see the LICENSE file for details.
                """.formatted(title, desc, desc, owner, title, title, installCmd, runCmd).stripIndent();
    }

    /**
     * Recommends MIT or Apache-2.0 with a one-line reason for each.
     */
    public static String getLicenseRecommendation() {
        return "Recommended Licenses:\n" +
                "1. MIT License: Highly permissive, short, and allows anyone to use, modify, and distribute your code with minimal restriction.\n" +
                "2. Apache License 2.0: Permissive license that also provides explicit grants of patent rights and protects against patent litigation.";
    }

    public String generateMitLicenseSnippet() {
        int year = java.time.Year.now().getValue();
        String holder = owner.isBlank() ? "The Authors" : owner;
        return """
                MIT License

                Copyright (c) %d %s

                Permission is hereby granted, free of charge, to any person obtaining a copy
                of this software and associated documentation files (the "Software"), to deal
                in the Software without restriction, including without limitation the rights
                to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
                copies of the Software, and to permit persons to whom the Software is
                furnished to do so, subject to the following conditions:

                The above copyright notice and this permission notice shall be included in all
                copies or substantial portions of the Software.

                THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
                IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
                FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
                AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
                LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
                OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
                SOFTWARE.
                """.formatted(year, holder).stripIndent();
    }

    /**
     * Infers 3-5 relevant GitHub topics from language, build tools, and README keywords.
     */
    public List<String> inferTopics(String readmeText) {
        Set<String> topics = new LinkedHashSet<>();

        // 1. Language tag
        if (!primaryLanguage.equalsIgnoreCase("Generic")) {
            topics.add(primaryLanguage.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-"));
        }

        // 2. Stack tag
        switch (stackType) {
            case MAVEN -> topics.add("maven");
            case GRADLE -> topics.add("gradle");
            case NPM -> topics.add("nodejs");
            case PYTHON -> topics.add("python3");
            case SWIFT -> topics.add("swift-package-manager");
            case GO -> topics.add("golang");
            case RUST -> topics.add("rust-lang");
            default -> {}
        }

        // 3. Scan repo name, description, and readme for keywords
        String corpus = (repoName + " " + description + " " + (readmeText != null ? readmeText : "")).toLowerCase(Locale.ROOT);

        if (corpus.contains("spring") || corpus.contains("spring-boot")) topics.add("spring-boot");
        if (corpus.contains("react")) topics.add("react");
        if (corpus.contains("fastapi") || corpus.contains("flask") || corpus.contains("django")) topics.add("web-framework");
        if (corpus.contains("rest") || corpus.contains("api")) topics.add("rest-api");
        if (corpus.contains("cli") || corpus.contains("command-line")) topics.add("cli");
        if (corpus.contains("microservice")) topics.add("microservices");
        if (corpus.contains("docker") || corpus.contains("container")) topics.add("docker");
        if (corpus.contains("algorithm") || corpus.contains("dsa")) topics.add("algorithms");
        if (corpus.contains("database") || corpus.contains("sql")) topics.add("database");
        if (corpus.contains("automation") || corpus.contains("tool")) topics.add("developer-tools");

        // Guarantee at least 3 topics
        if (topics.size() < 3) topics.add("developer-tools");
        if (topics.size() < 3) topics.add("clean-code");
        if (topics.size() < 3) topics.add("software-engineering");

        // Cap at 5 topics
        List<String> result = new ArrayList<>();
        for (String t : topics) {
            result.add(t);
            if (result.size() == 5) break;
        }
        return result;
    }
}
