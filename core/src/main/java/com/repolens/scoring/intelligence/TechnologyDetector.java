package com.repolens.scoring.intelligence;

import com.repolens.github.model.GithubRepo;
import com.repolens.scoring.RepoSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic technology, framework, database, and DevOps detector.
 * <p>
 * Evaluates repository file tree, configuration manifests, build descriptors,
 * and README text. Never guesses or fabricates technologies without evidence.
 */
public class TechnologyDetector {

    public List<DetectedTechnology> detect(RepoSnapshot snapshot) {
        if (snapshot == null) {
            return List.of();
        }

        List<DetectedTechnology> technologies = new ArrayList<>();
        Set<String> detectedNames = new LinkedHashSet<>();

        List<String> paths = snapshot.filePaths();
        GithubRepo repo = snapshot.repo();
        String readme = snapshot.readmeText() != null ? snapshot.readmeText().toLowerCase(Locale.ROOT) : "";
        String repoLang = repo != null && repo.language() != null ? repo.language().trim() : "";
        String repoDesc = repo != null && repo.description() != null ? repo.description().toLowerCase(Locale.ROOT) : "";

        // 1. Language Detection from metadata & file extensions
        detectLanguages(paths, repoLang, technologies, detectedNames);

        // 2. Build Tools & Package Managers
        detectBuildTools(paths, technologies, detectedNames);

        // 3. Frontend Frameworks & UI Libraries
        detectFrontend(paths, readme, technologies, detectedNames);

        // 4. Backend Frameworks & Runtimes
        detectBackend(paths, readme, technologies, detectedNames);

        // 5. AI, Machine Learning & Data Science
        detectAiMl(paths, readme, repoDesc, technologies, detectedNames);

        // 6. Mobile Application Frameworks
        detectMobile(paths, technologies, detectedNames);

        // 7. Databases, ORMs & Caches
        detectDataLayer(paths, readme, technologies, detectedNames);

        // 8. Testing Frameworks
        detectTesting(paths, technologies, detectedNames);

        // 9. DevOps & Containerization
        detectDevOps(paths, technologies, detectedNames);

        return technologies;
    }

    private void detectLanguages(List<String> paths, String repoLang, List<DetectedTechnology> tech, Set<String> names) {
        boolean hasJava = hasExtension(paths, ".java") || repoLang.equalsIgnoreCase("Java");
        if (hasJava && names.add("Java")) {
            tech.add(new DetectedTechnology("Java", TechnologyCategory.LANGUAGE, Confidence.HIGH, "Detected *.java source files"));
        }

        boolean hasPython = hasExtension(paths, ".py") || hasExtension(paths, ".ipynb") || repoLang.equalsIgnoreCase("Python");
        if (hasPython && names.add("Python")) {
            tech.add(new DetectedTechnology("Python", TechnologyCategory.LANGUAGE, Confidence.HIGH, "Detected *.py / *.ipynb source files"));
        }

        boolean hasTs = hasExtension(paths, ".ts") || hasExtension(paths, ".tsx") || hasFile(paths, "tsconfig.json");
        if (hasTs && names.add("TypeScript")) {
            tech.add(new DetectedTechnology("TypeScript", TechnologyCategory.LANGUAGE, Confidence.HIGH, "Detected *.ts / *.tsx files and tsconfig.json"));
        }

        boolean hasJs = hasExtension(paths, ".js") || hasExtension(paths, ".jsx") || hasExtension(paths, ".mjs") || repoLang.equalsIgnoreCase("JavaScript");
        if (hasJs && !hasTs && names.add("JavaScript")) {
            tech.add(new DetectedTechnology("JavaScript", TechnologyCategory.LANGUAGE, Confidence.HIGH, "Detected *.js / *.jsx source files"));
        }

        boolean hasSwift = hasExtension(paths, ".swift") || repoLang.equalsIgnoreCase("Swift");
        if (hasSwift && names.add("Swift")) {
            tech.add(new DetectedTechnology("Swift", TechnologyCategory.LANGUAGE, Confidence.HIGH, "Detected *.swift source files"));
        }

        boolean hasGo = hasExtension(paths, ".go") || hasFile(paths, "go.mod") || repoLang.equalsIgnoreCase("Go");
        if (hasGo && names.add("Go")) {
            tech.add(new DetectedTechnology("Go", TechnologyCategory.LANGUAGE, Confidence.HIGH, "Detected *.go source files and go.mod"));
        }

        boolean hasRust = hasExtension(paths, ".rs") || hasFile(paths, "Cargo.toml") || repoLang.equalsIgnoreCase("Rust");
        if (hasRust && names.add("Rust")) {
            tech.add(new DetectedTechnology("Rust", TechnologyCategory.LANGUAGE, Confidence.HIGH, "Detected *.rs source files and Cargo.toml"));
        }
    }

    private void detectBuildTools(List<String> paths, List<DetectedTechnology> tech, Set<String> names) {
        if (hasFile(paths, "pom.xml") && names.add("Maven")) {
            tech.add(new DetectedTechnology("Maven", TechnologyCategory.BUILD_TOOL, Confidence.HIGH, "Found pom.xml build descriptor"));
        }
        if ((hasFile(paths, "build.gradle") || hasFile(paths, "build.gradle.kts")) && names.add("Gradle")) {
            tech.add(new DetectedTechnology("Gradle", TechnologyCategory.BUILD_TOOL, Confidence.HIGH, "Found build.gradle build descriptor"));
        }
        if (hasFile(paths, "package-lock.json") && names.add("npm")) {
            tech.add(new DetectedTechnology("npm", TechnologyCategory.BUILD_TOOL, Confidence.HIGH, "Found package-lock.json lockfile"));
        } else if (hasFile(paths, "package.json") && names.add("npm")) {
            tech.add(new DetectedTechnology("npm", TechnologyCategory.BUILD_TOOL, Confidence.MEDIUM, "Found package.json manifest"));
        }
        if (hasFile(paths, "yarn.lock") && names.add("Yarn")) {
            tech.add(new DetectedTechnology("Yarn", TechnologyCategory.BUILD_TOOL, Confidence.HIGH, "Found yarn.lock dependency lockfile"));
        }
        if (hasFile(paths, "pnpm-lock.yaml") && names.add("pnpm")) {
            tech.add(new DetectedTechnology("pnpm", TechnologyCategory.BUILD_TOOL, Confidence.HIGH, "Found pnpm-lock.yaml lockfile"));
        }
        if (hasFile(paths, "poetry.lock") && names.add("Poetry")) {
            tech.add(new DetectedTechnology("Poetry", TechnologyCategory.BUILD_TOOL, Confidence.HIGH, "Found poetry.lock environment lockfile"));
        }
    }

    private void detectFrontend(List<String> paths, String readme, List<DetectedTechnology> tech, Set<String> names) {
        boolean hasNext = hasFile(paths, "next.config.js") || hasFile(paths, "next.config.mjs") || hasFile(paths, "next.config.ts")
                || hasPathStartingWith(paths, ".next/") || hasPathContaining(paths, "app/layout.");
        if (hasNext && names.add("Next.js")) {
            tech.add(new DetectedTechnology("Next.js", TechnologyCategory.FRAMEWORK, Confidence.HIGH, "Found Next.js configuration and layout tree"));
        }

        boolean hasReact = hasNext || hasExtension(paths, ".jsx") || hasExtension(paths, ".tsx")
                || hasPathContaining(paths, "src/components/") || readme.contains("react");
        if (hasReact && names.add("React")) {
            tech.add(new DetectedTechnology("React", TechnologyCategory.FRAMEWORK, Confidence.HIGH, "Detected React component hierarchy and JSX/TSX elements"));
        }

        boolean hasVue = hasExtension(paths, ".vue") || hasFile(paths, "vue.config.js");
        if (hasVue && names.add("Vue.js")) {
            tech.add(new DetectedTechnology("Vue.js", TechnologyCategory.FRAMEWORK, Confidence.HIGH, "Detected *.vue single-file components"));
        }

        boolean hasTailwind = hasFile(paths, "tailwind.config.js") || hasFile(paths, "tailwind.config.ts");
        if (hasTailwind && names.add("Tailwind CSS")) {
            tech.add(new DetectedTechnology("Tailwind CSS", TechnologyCategory.STYLING_UI, Confidence.HIGH, "Found tailwind.config file"));
        }

        boolean hasVite = hasFile(paths, "vite.config.js") || hasFile(paths, "vite.config.ts");
        if (hasVite && names.add("Vite")) {
            tech.add(new DetectedTechnology("Vite", TechnologyCategory.BUILD_TOOL, Confidence.HIGH, "Found vite.config build configuration"));
        }
    }

    private void detectBackend(List<String> paths, String readme, List<DetectedTechnology> tech, Set<String> names) {
        boolean hasSpringBoot = hasPathContaining(paths, "application.properties") || hasPathContaining(paths, "application.yml")
                || readme.contains("spring boot") || readme.contains("spring-boot");
        if (hasSpringBoot && names.add("Spring Boot")) {
            tech.add(new DetectedTechnology("Spring Boot", TechnologyCategory.FRAMEWORK, Confidence.HIGH, "Found Spring Boot configuration and annotations"));
        }

        boolean hasFastApi = (hasFile(paths, "main.py") && readme.contains("fastapi"))
                || hasPathContaining(paths, "routers/") || readme.contains("fastapi");
        if (hasFastApi && names.add("FastAPI")) {
            tech.add(new DetectedTechnology("FastAPI", TechnologyCategory.FRAMEWORK, Confidence.HIGH, "Detected FastAPI routing and documentation signature"));
        }

        boolean hasFlask = (hasFile(paths, "app.py") && (readme.contains("flask") || hasPathContaining(paths, "templates/")))
                || readme.contains("flask");
        if (hasFlask && !hasFastApi && names.add("Flask")) {
            tech.add(new DetectedTechnology("Flask", TechnologyCategory.FRAMEWORK, Confidence.MEDIUM, "Detected Flask application file and template tree"));
        }

        boolean hasExpress = hasFile(paths, "server.js") || hasFile(paths, "app.js") || readme.contains("express");
        if (hasExpress && names.add("Express.js")) {
            tech.add(new DetectedTechnology("Express.js", TechnologyCategory.FRAMEWORK, Confidence.MEDIUM, "Detected Express server file and route entrypoint"));
        }
    }

    private void detectAiMl(List<String> paths, String readme, String desc, List<DetectedTechnology> tech, Set<String> names) {
        boolean hasNotebooks = hasExtension(paths, ".ipynb");
        if (hasNotebooks && names.add("Jupyter Notebooks")) {
            tech.add(new DetectedTechnology("Jupyter Notebooks", TechnologyCategory.AI_ML, Confidence.HIGH, "Detected *.ipynb interactive notebooks"));
        }

        String corpus = readme + " " + desc;
        boolean hasPyTorch = corpus.contains("pytorch") || corpus.contains("torch") || hasExtension(paths, ".pt") || hasExtension(paths, ".pth");
        if (hasPyTorch && names.add("PyTorch")) {
            tech.add(new DetectedTechnology("PyTorch", TechnologyCategory.AI_ML, Confidence.HIGH, "Found PyTorch weights/references"));
        }

        boolean hasTf = corpus.contains("tensorflow") || corpus.contains("keras") || hasExtension(paths, ".h5");
        if (hasTf && names.add("TensorFlow / Keras")) {
            tech.add(new DetectedTechnology("TensorFlow / Keras", TechnologyCategory.AI_ML, Confidence.HIGH, "Found TensorFlow/Keras artifacts"));
        }

        boolean hasSklearn = corpus.contains("scikit-learn") || corpus.contains("sklearn") || corpus.contains("randomforest");
        if (hasSklearn && names.add("Scikit-Learn")) {
            tech.add(new DetectedTechnology("Scikit-Learn", TechnologyCategory.AI_ML, Confidence.HIGH, "Found Scikit-Learn training references"));
        }

        boolean hasLangchain = corpus.contains("langchain") || corpus.contains("llamaindex") || corpus.contains("rag ") || corpus.contains("retrieval augmented");
        if (hasLangchain && names.add("LangChain / RAG")) {
            tech.add(new DetectedTechnology("LangChain / RAG", TechnologyCategory.AI_ML, Confidence.HIGH, "Found RAG / LangChain pipeline architecture"));
        }
    }

    private void detectMobile(List<String> paths, List<DetectedTechnology> tech, Set<String> names) {
        if (hasFile(paths, "pubspec.yaml") && names.add("Flutter")) {
            tech.add(new DetectedTechnology("Flutter", TechnologyCategory.FRAMEWORK, Confidence.HIGH, "Found pubspec.yaml Flutter project descriptor"));
        }

        boolean hasIosNative = hasExtension(paths, ".xcodeproj") || hasExtension(paths, ".xcworkspace") || (hasExtension(paths, ".swift") && hasPathContaining(paths, "app/"));
        if (hasIosNative && names.add("iOS / Swift Native")) {
            tech.add(new DetectedTechnology("iOS / Swift Native", TechnologyCategory.FRAMEWORK, Confidence.HIGH, "Detected Xcode project and native Swift UI app files"));
        }

        boolean hasAndroidNative = hasPathContaining(paths, "AndroidManifest.xml");
        if (hasAndroidNative && names.add("Android Native")) {
            tech.add(new DetectedTechnology("Android Native", TechnologyCategory.FRAMEWORK, Confidence.HIGH, "Found AndroidManifest.xml application descriptor"));
        }
    }

    private void detectDataLayer(List<String> paths, String readme, List<DetectedTechnology> tech, Set<String> names) {
        if (hasFile(paths, "schema.prisma") && names.add("Prisma ORM")) {
            tech.add(new DetectedTechnology("Prisma ORM", TechnologyCategory.DATABASE_ORM, Confidence.HIGH, "Found schema.prisma database schema"));
        }
        if (hasFile(paths, "alembic.ini") && names.add("Alembic Migrations")) {
            tech.add(new DetectedTechnology("Alembic Migrations", TechnologyCategory.DATABASE_ORM, Confidence.HIGH, "Found alembic.ini database migration configuration"));
        }
        if (hasExtension(paths, ".sql") && names.add("SQL")) {
            tech.add(new DetectedTechnology("SQL", TechnologyCategory.DATABASE_ORM, Confidence.HIGH, "Detected *.sql schema migration scripts"));
        }
    }

    private void detectTesting(List<String> paths, List<DetectedTechnology> tech, Set<String> names) {
        boolean hasJunit = hasPathContaining(paths, "src/test/java") || hasPathContaining(paths, "Test.java");
        if (hasJunit && names.add("JUnit")) {
            tech.add(new DetectedTechnology("JUnit", TechnologyCategory.TESTING, Confidence.HIGH, "Detected JUnit test suites in src/test/java"));
        }

        boolean hasPytest = hasPathContaining(paths, "conftest.py") || hasFile(paths, "pytest.ini") || hasPathContaining(paths, "tests/test_");
        if (hasPytest && names.add("pytest")) {
            tech.add(new DetectedTechnology("pytest", TechnologyCategory.TESTING, Confidence.HIGH, "Detected pytest test layout and conftest.py"));
        }

        boolean hasJest = hasFile(paths, "jest.config.js") || hasFile(paths, "jest.config.ts") || hasExtension(paths, ".test.js") || hasExtension(paths, ".test.tsx");
        if (hasJest && names.add("Jest / Vitest")) {
            tech.add(new DetectedTechnology("Jest / Vitest", TechnologyCategory.TESTING, Confidence.HIGH, "Detected Jest/Vitest unit test specifications"));
        }
    }

    private void detectDevOps(List<String> paths, List<DetectedTechnology> tech, Set<String> names) {
        boolean hasDocker = hasFile(paths, "Dockerfile") || hasFile(paths, "docker-compose.yml") || hasFile(paths, "docker-compose.yaml");
        if (hasDocker && names.add("Docker")) {
            tech.add(new DetectedTechnology("Docker", TechnologyCategory.CI_DEVOPS, Confidence.HIGH, "Found Dockerfile and/or compose specifications"));
        }

        boolean hasGhActions = hasPathStartingWith(paths, ".github/workflows/");
        if (hasGhActions && names.add("GitHub Actions")) {
            tech.add(new DetectedTechnology("GitHub Actions", TechnologyCategory.CI_DEVOPS, Confidence.HIGH, "Found CI/CD automation in .github/workflows"));
        }
    }

    // Helper methods for fast deterministic path checking
    private static boolean hasFile(List<String> paths, String target) {
        for (String p : paths) {
            int idx = p.lastIndexOf('/');
            String filename = idx >= 0 ? p.substring(idx + 1) : p;
            if (filename.equalsIgnoreCase(target)) return true;
        }
        return false;
    }

    private static boolean hasExtension(List<String> paths, String ext) {
        for (String p : paths) {
            if (p.toLowerCase(Locale.ROOT).endsWith(ext.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static boolean hasPathContaining(List<String> paths, String part) {
        String lower = part.toLowerCase(Locale.ROOT);
        for (String p : paths) {
            if (p.toLowerCase(Locale.ROOT).contains(lower)) return true;
        }
        return false;
    }

    private static boolean hasPathStartingWith(List<String> paths, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String p : paths) {
            if (p.toLowerCase(Locale.ROOT).startsWith(lower)) return true;
        }
        return false;
    }
}
