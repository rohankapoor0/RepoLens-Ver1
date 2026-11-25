package com.repolens.scoring.rules;

import com.repolens.scoring.CategoryResult;
import com.repolens.scoring.CategoryScorer;
import com.repolens.scoring.CheckResult;
import com.repolens.scoring.Deduction;
import com.repolens.scoring.RepoSnapshot;
import com.repolens.scoring.ScoreCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Evaluates documentation, discoverability, README completeness, and usage guides (25 points max).
 */
public class DocumentationScorer implements CategoryScorer {

    private static final Pattern INSTALL_USAGE_HEADING = Pattern.compile(
            "(?im)^#{1,6}\\s+.*(install|installation|setup|getting\\s+started|quickstart|usage|how\\s+to\\s+run|running|build)"
    );

    private static final Pattern IMAGE_PATTERN = Pattern.compile(
            "(?i)(!\\[.*?\\]\\(.*?\\)|<img\\s+[^>]*src=.*?|https?://\\S+\\.(png|jpe?g|gif|svg|webp))"
    );

    @Override
    public ScoreCategory getCategory() {
        return ScoreCategory.DOCUMENTATION;
    }

    @Override
    public CategoryResult score(RepoSnapshot snapshot) {
        int points = getCategory().getMaxPoints();
        List<Deduction> deductions = new ArrayList<>();
        List<CheckResult> passedChecks = new ArrayList<>();
        List<CheckResult> failedChecks = new ArrayList<>();

        RepoLanguageContext ctx = RepoLanguageContext.from(snapshot);
        String readme = snapshot.readmeText();
        boolean hasReadme = readme != null && !readme.isBlank();
        int totalPaths = snapshot.filePaths().size();

        // 1. README Existence & Content (20 pts)
        if (!hasReadme) {
            points -= 20;
            String evidence = "Inspected " + totalPaths + " repository paths; no README file (.md, .txt, .rst) found.";
            failedChecks.add(new CheckResult("README_EXISTS", "README Presence", false, 0, 20, evidence));
            deductions.add(new Deduction(
                    getCategory(),
                    "README_EXISTS",
                    evidence,
                    "A README is the primary entry point for users, recruiters, and contributors to understand the project purpose and execution.",
                    20, 20,
                    "Create a comprehensive README.md in the root directory covering project overview, installation, usage, and visual demo.",
                    List.of(
                            "Create README.md at the repository root",
                            "Add project overview and feature summary",
                            "Document installation commands and prerequisites",
                            "Add usage examples showing how to run the project",
                            "Include a visual screenshot or diagram"
                    ),
                    ctx.generateReadmeSkeleton(),
                    20, 20
            ));
        } else {
            int len = readme.trim().length();
            passedChecks.add(new CheckResult("README_EXISTS", "README Presence", true, 8, 8,
                    "README file found with " + len + " characters."));

            // 2. README Length & Substance (4 pts)
            if (len < 200) {
                points -= 4;
                String evidence = "README is " + len + " characters (below 200 character threshold).";
                failedChecks.add(new CheckResult("README_LENGTH", "README Substance", false, 0, 4, evidence));
                deductions.add(new Deduction(
                        getCategory(),
                        "README_LENGTH",
                        evidence,
                        "A concise or stub README lacks necessary context for developers to understand and evaluate the repository.",
                        4, 4,
                        "Expand README with project overview, key architecture details, and prerequisites.",
                        List.of(
                                "Add an Overview section detailing what problem the project solves",
                                "List technology stack and prerequisites",
                                "Provide architectural summary"
                        ),
                        ctx.generateReadmeSkeleton(),
                        15, 4
                ));
            } else if (len < 500) {
                points -= 2;
                String evidence = "README is " + len + " characters (below recommended 500+ characters).";
                failedChecks.add(new CheckResult("README_LENGTH", "README Substance", false, 2, 4, evidence));
                deductions.add(new Deduction(
                        getCategory(),
                        "README_LENGTH",
                        evidence,
                        "A relatively short README may leave out configuration options, troubleshooting, and background context.",
                        2, 4,
                        "Add more detailed documentation, usage examples, and configuration guides.",
                        List.of(
                                "Expand configuration details",
                                "Add troubleshooting and FAQ sections"
                        ),
                        ctx.generateReadmeSkeleton(),
                        10, 2
                ));
            } else {
                passedChecks.add(new CheckResult("README_LENGTH", "README Substance", true, 4, 4,
                        "README is " + len + " characters with substantial documentation."));
            }

            // 3. Installation or Usage Headings (5 pts)
            if (!INSTALL_USAGE_HEADING.matcher(readme).find()) {
                points -= 5;
                String evidence = "README lacks dedicated 'Installation' or 'Usage' section headings.";
                failedChecks.add(new CheckResult("README_INSTALL_USAGE", "Installation & Usage Instructions", false, 0, 5, evidence));
                deductions.add(new Deduction(
                        getCategory(),
                        "README_INSTALL_USAGE",
                        evidence,
                        "Clear setup and execution instructions are essential for reproducible builds and developer onboarding.",
                        5, 5,
                        "Add 'Installation' and 'Usage' sections with step-by-step instructions to clone and run the project.",
                        List.of(
                                "Add '## Installation' section with build and dependency commands",
                                "Add '## Usage' section with sample execution commands and flags"
                        ),
                        ctx.generateReadmeSkeleton(),
                        15, 5
                ));
            } else {
                passedChecks.add(new CheckResult("README_INSTALL_USAGE", "Installation & Usage Instructions", true, 5, 5,
                        "README contains dedicated Installation or Usage instructions."));
            }

            // 4. Visual Screenshot / Diagram Present (3 pts)
            if (!IMAGE_PATTERN.matcher(readme).find()) {
                points -= 3;
                String evidence = "README contains no visual screenshots or architecture diagrams.";
                failedChecks.add(new CheckResult("README_VISUALS", "Visual Demo & Diagrams", false, 0, 3, evidence));
                deductions.add(new Deduction(
                        getCategory(),
                        "README_VISUALS",
                        evidence,
                        "Visual media such as architecture diagrams or UI screenshots significantly improve project comprehension.",
                        3, 3,
                        "Include an image or GIF demonstrating the running application or an architecture diagram.",
                        List.of(
                                "Take a screenshot or record a short GIF of the running project",
                                "Place the image file in docs/ or assets/",
                                "Embed using Markdown syntax: ![Demo](docs/demo.png)"
                        ),
                        "![Application Demo](docs/demo.png)",
                        15, 3
                ));
            } else {
                passedChecks.add(new CheckResult("README_VISUALS", "Visual Demo & Diagrams", true, 3, 3,
                        "README contains visual screenshots or diagrams."));
            }
        }

        // 5. Repository Description (3 pts)
        boolean hasDesc = snapshot.repo() != null && snapshot.repo().description() != null && !snapshot.repo().description().isBlank();
        if (!hasDesc) {
            points -= 3;
            String evidence = "Repository description is empty on GitHub metadata.";
            failedChecks.add(new CheckResult("REPO_DESCRIPTION", "Repository Metadata Description", false, 0, 3, evidence));
            deductions.add(new Deduction(
                    getCategory(),
                    "REPO_DESCRIPTION",
                    evidence,
                    "A concise repository description appears in GitHub search results and profile overviews.",
                    3, 3,
                    "Add a clear 1-2 sentence description in the repository header on GitHub.",
                    List.of(
                            "Navigate to repository homepage on GitHub",
                            "Click the gear icon next to 'About'",
                            "Enter a concise 1-2 sentence summary of what the project does",
                            "Click 'Save changes'"
                    ),
                    ctx.description().isBlank() ? "A " + ctx.primaryLanguage() + " project demonstrating clean architecture and automated testing." : ctx.description(),
                    5, 3
            ));
        } else {
            passedChecks.add(new CheckResult("REPO_DESCRIPTION", "Repository Metadata Description", true, 3, 3,
                    "Repository description provided: \"" + snapshot.repo().description() + "\""));
        }

        // 6. Topics / Tags Defined (2 pts)
        boolean hasTopics = snapshot.repo() != null && snapshot.repo().topics() != null && !snapshot.repo().topics().isEmpty();
        if (!hasTopics) {
            points -= 2;
            List<String> suggestedTopics = ctx.inferTopics(readme);
            String evidence = "Repository has 0 GitHub topics/tags defined.";
            failedChecks.add(new CheckResult("REPO_TOPICS", "GitHub Topics & Tags", false, 0, 2, evidence));
            deductions.add(new Deduction(
                    getCategory(),
                    "REPO_TOPICS",
                    evidence,
                    "Topics help other developers and recruiters discover the repository by technology stack and domain.",
                    2, 2,
                    "Add 3-5 relevant topics (" + String.join(", ", suggestedTopics) + ") to improve discoverability.",
                    List.of(
                            "Open repository settings in 'About' section on GitHub",
                            "Add recommended topics: " + String.join(", ", suggestedTopics),
                            "Save changes"
                    ),
                    String.join(", ", suggestedTopics),
                    5, 2
            ));
        } else {
            passedChecks.add(new CheckResult("REPO_TOPICS", "GitHub Topics & Tags", true, 2, 2,
                    "Repository defines " + snapshot.repo().topics().size() + " topics: " + String.join(", ", snapshot.repo().topics())));
        }

        return new CategoryResult(getCategory(), Math.max(0, points), getCategory().getMaxPoints(), passedChecks, failedChecks, deductions);
    }
}
