package com.repolens.scoring.intelligence;

import com.repolens.github.model.GithubRepo;
import com.repolens.scoring.RepoSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic multi-archetype repository classifier.
 * <p>
 * Evaluates file tree topography, dependency configurations, frameworks,
 * and documentation keywords without arbitrary heuristics.
 */
public class RepositoryClassifier {

    public record ClassificationResult(
            ProjectType primaryType,
            List<ProjectType> allDetectedTypes,
            String summary
    ) {}

    public ClassificationResult classify(RepoSnapshot snapshot, List<DetectedTechnology> technologies) {
        if (snapshot == null) {
            return new ClassificationResult(ProjectType.GENERAL_SOFTWARE, List.of(ProjectType.GENERAL_SOFTWARE), "Standard software repository.");
        }

        List<String> paths = snapshot.filePaths();
        GithubRepo repo = snapshot.repo();
        String readme = snapshot.readmeText() != null ? snapshot.readmeText().toLowerCase(Locale.ROOT) : "";
        String repoDesc = repo != null && repo.description() != null ? repo.description().toLowerCase(Locale.ROOT) : "";
        String repoLang = repo != null && repo.language() != null ? repo.language().trim() : "";
        String repoTopics = repo != null && repo.topics() != null ? String.join(" ", repo.topics()).toLowerCase(Locale.ROOT) : "";
        String corpus = repoDesc + " " + readme + " " + repoTopics;

        Set<String> techNames = new LinkedHashSet<>();
        for (DetectedTechnology t : technologies) {
            techNames.add(t.name());
        }

        Set<ProjectType> detectedTypes = new LinkedHashSet<>();

        // 1. Mobile Application check
        boolean isMobile = techNames.contains("Flutter") || techNames.contains("iOS / Swift Native") || techNames.contains("Android Native")
                || hasExtension(paths, ".xcodeproj") || hasFile(paths, "pubspec.yaml");
        if (isMobile) {
            detectedTypes.add(ProjectType.MOBILE_APP);
        }

        // 2. AI / Machine Learning check
        boolean hasAiTech = techNames.contains("PyTorch") || techNames.contains("TensorFlow / Keras")
                || techNames.contains("Scikit-Learn") || techNames.contains("LangChain / RAG");
        boolean hasAiCorpus = corpus.contains("neural network") || corpus.contains("deep learning")
                || corpus.contains("llm") || corpus.contains("retrieval augmented") || corpus.contains("machine learning")
                || corpus.contains("computer vision") || corpus.contains("model weights");
        boolean hasModelArtifacts = hasExtension(paths, ".onnx") || hasExtension(paths, ".pt") || hasExtension(paths, ".bin")
                || hasPathContaining(paths, "weights/") || hasPathContaining(paths, "models/");

        if (hasAiTech || (hasAiCorpus && (hasModelArtifacts || techNames.contains("Python") || hasExtension(paths, ".ipynb")))) {
            detectedTypes.add(ProjectType.AI_MACHINE_LEARNING);
        }

        // 3. Data Science check
        boolean hasNotebooks = hasExtension(paths, ".ipynb");
        boolean hasDataDirs = hasPathContaining(paths, "dataset/") || hasPathContaining(paths, "datasets/") || hasPathContaining(paths, "data/");
        boolean hasDataCorpus = corpus.contains("exploratory data analysis") || corpus.contains("data science")
                || corpus.contains("data-driven") || corpus.contains("analytics");

        if (hasNotebooks || (hasDataDirs && hasDataCorpus)) {
            detectedTypes.add(ProjectType.DATA_SCIENCE);
        }

        // 4. React / Frontend check
        boolean hasReact = techNames.contains("React") || techNames.contains("Next.js") || techNames.contains("Vue.js");
        boolean hasFrontendDirs = hasPathContaining(paths, "src/components/") || hasPathContaining(paths, "src/pages/")
                || hasPathContaining(paths, "public/") || hasFile(paths, "index.html");
        boolean hasFrontend = hasReact || (hasFile(paths, "package.json") && hasFrontendDirs);

        if (hasFrontend) {
            detectedTypes.add(ProjectType.REACT_FRONTEND);
        }

        // 5. Backend / REST API check
        boolean hasBackendFramework = techNames.contains("Spring Boot") || techNames.contains("FastAPI")
                || techNames.contains("Flask") || techNames.contains("Express.js");
        boolean hasApiDirs = hasPathContaining(paths, "controller/") || hasPathContaining(paths, "controllers/")
                || hasPathContaining(paths, "routes/") || hasPathContaining(paths, "api/")
                || hasPathContaining(paths, "endpoints/") || hasPathContaining(paths, "service/")
                || hasPathContaining(paths, "services/");
        boolean hasApiCorpus = corpus.contains("rest api") || corpus.contains("backend api") || corpus.contains("microservice");

        boolean hasBackend = hasBackendFramework || (hasApiDirs && (techNames.contains("Java") || techNames.contains("Python") || techNames.contains("Go") || hasFile(paths, "server.js")))
                || hasApiCorpus;

        if (hasBackend) {
            detectedTypes.add(ProjectType.BACKEND_API);
        }

        // 6. Full-Stack check: if BOTH frontend and backend structures exist
        if (hasFrontend && hasBackend) {
            detectedTypes.add(ProjectType.FULLSTACK_APP);
        }

        // 7. Java / Enterprise JVM check
        boolean hasJava = techNames.contains("Java") || techNames.contains("Maven") || techNames.contains("Gradle")
                || hasExtension(paths, ".java") || repoLang.equalsIgnoreCase("Java");
        if (hasJava) {
            detectedTypes.add(ProjectType.JAVA_ENTERPRISE);
        }

        // 8. Python Application check
        boolean hasPython = techNames.contains("Python") || hasExtension(paths, ".py")
                || hasFile(paths, "requirements.txt") || hasFile(paths, "pyproject.toml") || repoLang.equalsIgnoreCase("Python");
        if (hasPython) {
            detectedTypes.add(ProjectType.PYTHON_APP);
        }

        // 9. CLI Tool check
        boolean hasCliDirs = hasPathContaining(paths, "cmd/") || hasPathContaining(paths, "cli/") || hasFile(paths, "cli.py");
        boolean hasCliCorpus = corpus.contains("command-line") || corpus.contains("cli tool") || corpus.contains("command line interface");
        if (hasCliDirs || hasCliCorpus) {
            detectedTypes.add(ProjectType.CLI_TOOL);
        }

        // 10. Open-Source Library check
        boolean hasLibraryMarkers = (hasFile(paths, "package.json") && !hasFrontendDirs && !hasFile(paths, "server.js"))
                || (hasFile(paths, "setup.py") && !hasApiDirs)
                || (hasPathContaining(paths, "examples/") && !hasApiDirs)
                || corpus.contains("reusable library") || corpus.contains("library") || corpus.contains("sdk") || corpus.contains("client library");
        if (hasLibraryMarkers) {
            detectedTypes.add(ProjectType.LIBRARY_PACKAGE);
        }

        // 11. Documentation Project
        boolean isDocSite = (paths.size() <= 8 && hasExtension(paths, ".html") && !hasBackend && !hasFrontend)
                || corpus.contains("documentation") || hasPathContaining(paths, "docs/");
        if (isDocSite && detectedTypes.isEmpty()) {
            detectedTypes.add(ProjectType.DOCUMENTATION_PROJECT);
        }

        // 12. Fallback
        if (detectedTypes.isEmpty()) {
            detectedTypes.add(ProjectType.GENERAL_SOFTWARE);
        }

        // Determine primary archetype by highest semantic specificity
        ProjectType primary = resolvePrimaryType(detectedTypes);

        String summary = generateClassificationSummary(primary, detectedTypes, technologies);

        return new ClassificationResult(primary, new ArrayList<>(detectedTypes), summary);
    }

    private ProjectType resolvePrimaryType(Set<ProjectType> types) {
        if (types.contains(ProjectType.FULLSTACK_APP)) return ProjectType.FULLSTACK_APP;
        if (types.contains(ProjectType.AI_MACHINE_LEARNING)) return ProjectType.AI_MACHINE_LEARNING;
        if (types.contains(ProjectType.MOBILE_APP)) return ProjectType.MOBILE_APP;
        if (types.contains(ProjectType.DATA_SCIENCE)) return ProjectType.DATA_SCIENCE;
        if (types.contains(ProjectType.REACT_FRONTEND)) return ProjectType.REACT_FRONTEND;
        if (types.contains(ProjectType.BACKEND_API)) return ProjectType.BACKEND_API;
        if (types.contains(ProjectType.CLI_TOOL)) return ProjectType.CLI_TOOL;
        if (types.contains(ProjectType.LIBRARY_PACKAGE)) return ProjectType.LIBRARY_PACKAGE;
        if (types.contains(ProjectType.JAVA_ENTERPRISE)) return ProjectType.JAVA_ENTERPRISE;
        if (types.contains(ProjectType.PYTHON_APP)) return ProjectType.PYTHON_APP;
        if (types.contains(ProjectType.DOCUMENTATION_PROJECT)) return ProjectType.DOCUMENTATION_PROJECT;
        return ProjectType.GENERAL_SOFTWARE;
    }

    private String generateClassificationSummary(ProjectType primary, Set<ProjectType> allTypes, List<DetectedTechnology> tech) {
        StringBuilder sb = new StringBuilder();
        sb.append("Classified primarily as ").append(primary.getDisplayName()).append(". ");
        if (allTypes.size() > 1) {
            List<String> others = new ArrayList<>();
            for (ProjectType t : allTypes) {
                if (t != primary) others.add(t.getDisplayName());
            }
            sb.append("Additional verified traits: ").append(String.join(", ", others)).append(". ");
        }
        if (!tech.isEmpty()) {
            List<String> keyTech = tech.stream().map(DetectedTechnology::name).limit(4).toList();
            sb.append("Powered by ").append(String.join(", ", keyTech)).append(".");
        }
        return sb.toString();
    }

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
}
