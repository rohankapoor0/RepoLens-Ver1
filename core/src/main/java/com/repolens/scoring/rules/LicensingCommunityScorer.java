package com.repolens.scoring.rules;

import com.repolens.scoring.CategoryResult;
import com.repolens.scoring.CategoryScorer;
import com.repolens.scoring.CheckResult;
import com.repolens.scoring.Deduction;
import com.repolens.scoring.RepoSnapshot;
import com.repolens.scoring.ScoreCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates open source licensing and community contribution standards (10 points max).
 */
public class LicensingCommunityScorer implements CategoryScorer {

    @Override
    public ScoreCategory getCategory() {
        return ScoreCategory.LICENSING_COMMUNITY;
    }

    @Override
    public CategoryResult score(RepoSnapshot snapshot) {
        int points = getCategory().getMaxPoints();
        List<Deduction> deductions = new ArrayList<>();
        List<CheckResult> passedChecks = new ArrayList<>();
        List<CheckResult> failedChecks = new ArrayList<>();

        RepoLanguageContext ctx = RepoLanguageContext.from(snapshot);
        int totalPaths = snapshot.filePaths().size();

        // 1. License Present (5 pts)
        String detectedLicense = null;
        if (snapshot.repo() != null && snapshot.repo().license() != null && !snapshot.repo().license().isBlank()) {
            detectedLicense = snapshot.repo().license();
        } else {
            for (String path : snapshot.filePaths()) {
                String lower = path.toLowerCase();
                if (lower.equals("license") || lower.startsWith("license.") || lower.equals("licence") || lower.startsWith("licence.")) {
                    detectedLicense = path;
                    break;
                }
            }
        }

        if (detectedLicense == null) {
            points -= 5;
            String evidence = "Inspected " + totalPaths + " paths and repository metadata; no open-source LICENSE detected.";
            failedChecks.add(new CheckResult("OPEN_SOURCE_LICENSE", "Open Source License", false, 0, 5, evidence));
            deductions.add(new Deduction(
                    getCategory(),
                    "OPEN_SOURCE_LICENSE",
                    evidence,
                    "Without an explicit open-source license, default copyright laws apply, which legally prohibits others from modifying or distributing the software.",
                    5, 5,
                    "Add an open-source license. " + RepoLanguageContext.getLicenseRecommendation(),
                    List.of(
                            "Choose MIT License (for broad permissiveness) or Apache-2.0 (for patent grant protection)",
                            "Create LICENSE file at repository root",
                            "Paste license text and update copyright year and author name",
                            "Commit and push to default branch"
                    ),
                    ctx.generateMitLicenseSnippet(),
                    5, 5
            ));
        } else {
            passedChecks.add(new CheckResult("OPEN_SOURCE_LICENSE", "Open Source License", true, 5, 5,
                    "Open-source license detected (" + detectedLicense + ")."));
        }

        // 2. CONTRIBUTING Guidelines (3 pts)
        String detectedContributing = null;
        for (String path : snapshot.filePaths()) {
            String lower = path.toLowerCase();
            if (lower.equals("contributing.md") || lower.equals("contributing") || lower.contains("/contributing.md")) {
                detectedContributing = path;
                break;
            }
        }

        if (detectedContributing == null) {
            points -= 3;
            String evidence = "No CONTRIBUTING guidelines found in repository.";
            failedChecks.add(new CheckResult("CONTRIBUTING_GUIDE", "Contribution Guidelines", false, 0, 3, evidence));
            deductions.add(new Deduction(
                    getCategory(),
                    "CONTRIBUTING_GUIDE",
                    evidence,
                    "Contribution guidelines explain how external developers can fork, set up environments, and submit pull requests cleanly.",
                    3, 3,
                    "Add a CONTRIBUTING.md file explaining how other developers can submit pull requests, report bugs, or request features.",
                    List.of(
                            "Create CONTRIBUTING.md file at repository root",
                            "Explain development workflow (fork, branch, commit, PR)",
                            "State test and code style expectations",
                            "Commit and push"
                    ),
                    """
                    # Contributing Guidelines

                    Thank you for considering contributing to %s!

                    ## How to Contribute
                    1. Fork the repository and create your branch from `main`.
                    2. Install dependencies and run existing automated tests.
                    3. Make your changes with clear, descriptive commit messages.
                    4. Ensure all tests pass.
                    5. Submit a Pull Request describing your changes.
                    """.formatted(ctx.repoName()).stripIndent(),
                    15, 3
            ));
        } else {
            passedChecks.add(new CheckResult("CONTRIBUTING_GUIDE", "Contribution Guidelines", true, 3, 3,
                    "Contributing guidelines found at " + detectedContributing + "."));
        }

        // 3. Issue / Pull Request Templates (2 pts)
        String detectedTemplate = null;
        for (String path : snapshot.filePaths()) {
            String lower = path.toLowerCase();
            if (lower.contains("issue_template") || lower.contains("pull_request_template")) {
                detectedTemplate = path;
                break;
            }
        }

        if (detectedTemplate == null) {
            points -= 2;
            String evidence = "No Issue or Pull Request templates found in .github/.";
            failedChecks.add(new CheckResult("ISSUE_TEMPLATES", "Issue & PR Templates", false, 0, 2, evidence));
            deductions.add(new Deduction(
                    getCategory(),
                    "ISSUE_TEMPLATES",
                    evidence,
                    "Issue and pull request templates ensure bug reports and feature requests contain necessary reproduction steps.",
                    2, 2,
                    "Add issue and PR templates in .github/ISSUE_TEMPLATE to standardize community bug reports and pull requests.",
                    List.of(
                            "Create directory .github/ISSUE_TEMPLATE/",
                            "Add bug_report.md and feature_request.md",
                            "Create .github/pull_request_template.md"
                    ),
                    """
                    ## Description
                    Please include a summary of the change and which issue is fixed.

                    ## Type of Change
                    - [ ] Bug fix
                    - [ ] New feature
                    - [ ] Breaking change
                    - [ ] Documentation update

                    ## How Has This Been Tested?
                    Please describe the tests that you ran to verify your changes.
                    """.stripIndent(),
                    10, 2
            ));
        } else {
            passedChecks.add(new CheckResult("ISSUE_TEMPLATES", "Issue & PR Templates", true, 2, 2,
                    "Issue or PR template detected at " + detectedTemplate + "."));
        }

        return new CategoryResult(getCategory(), Math.max(0, points), getCategory().getMaxPoints(), passedChecks, failedChecks, deductions);
    }
}
