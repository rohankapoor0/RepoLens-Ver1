package com.repolens.scoring.rules;

import com.repolens.scoring.CategoryResult;
import com.repolens.scoring.CategoryScorer;
import com.repolens.scoring.CheckResult;
import com.repolens.scoring.Deduction;
import com.repolens.scoring.RepoSnapshot;
import com.repolens.scoring.ScoreCategory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates repository maintenance, commit cadence, and freshness (20 points max).
 */
public class MaintenanceScorer implements CategoryScorer {

    @Override
    public ScoreCategory getCategory() {
        return ScoreCategory.MAINTENANCE;
    }

    @Override
    public CategoryResult score(RepoSnapshot snapshot) {
        int points = getCategory().getMaxPoints();
        List<Deduction> deductions = new ArrayList<>();
        List<CheckResult> passedChecks = new ArrayList<>();
        List<CheckResult> failedChecks = new ArrayList<>();

        // 1. Not Archived (6 pts)
        boolean isArchived = snapshot.repo() != null && snapshot.repo().archived();
        if (isArchived) {
            points -= 6;
            String evidence = "Repository is marked as archived / read-only on GitHub.";
            failedChecks.add(new CheckResult("REPO_NOT_ARCHIVED", "Active Repository Status", false, 0, 6, evidence));
            deductions.add(new Deduction(
                    getCategory(),
                    "REPO_NOT_ARCHIVED",
                    evidence,
                    "Archived repositories cannot receive pull requests, new issues, or security patches.",
                    6, 6,
                    "Unarchive the repository or note in README if this project has reached end-of-life.",
                    List.of(
                            "Navigate to repository Settings on GitHub",
                            "Scroll down to 'Danger Zone'",
                            "Click 'Unarchive this repository' if it is under active maintenance"
                    ),
                    null,
                    5, 6
            ));
        } else {
            passedChecks.add(new CheckResult("REPO_NOT_ARCHIVED", "Active Repository Status", true, 6, 6,
                    "Repository is active and not archived."));
        }

        // 2. Last Push Recency (8 pts)
        Instant now = Instant.now();
        Instant pushedAt = snapshot.repo() != null ? snapshot.repo().pushedAt() : null;

        if (pushedAt == null) {
            points -= 8;
            String evidence = "No push activity recorded on default branch.";
            failedChecks.add(new CheckResult("LAST_PUSH_RECENCY", "Recent Push Activity", false, 0, 8, evidence));
            deductions.add(new Deduction(
                    getCategory(),
                    "LAST_PUSH_RECENCY",
                    evidence,
                    "Repositories without recorded push activity appear dormant or abandoned to reviewers.",
                    8, 8,
                    "Push active commits to your default branch.",
                    List.of(
                            "Make local commits",
                            "Push changes to default branch"
                    ),
                    "git push origin main",
                    30, 8
            ));
        } else {
            long daysSincePush = Math.max(0, Duration.between(pushedAt, now).toDays());
            if (daysSincePush > 365) {
                points -= 8;
                String evidence = "No commits pushed in over 1 year (" + daysSincePush + " days ago).";
                failedChecks.add(new CheckResult("LAST_PUSH_RECENCY", "Recent Push Activity", false, 0, 8, evidence));
                deductions.add(new Deduction(
                        getCategory(),
                        "LAST_PUSH_RECENCY",
                        evidence,
                        "Projects with over a year of inactivity frequently contain outdated dependencies and unpatched vulnerabilities.",
                        8, 8,
                        "Resume active development or update dependencies to prevent obsolescence.",
                        List.of(
                                "Audit dependencies for updates",
                                "Verify code builds on current runtime versions",
                                "Commit and push maintenance updates"
                        ),
                        null,
                        30, 8
                ));
            } else if (daysSincePush > 180) {
                points -= 5;
                String evidence = "Last commit was between 6 and 12 months ago (" + daysSincePush + " days ago).";
                failedChecks.add(new CheckResult("LAST_PUSH_RECENCY", "Recent Push Activity", false, 3, 8, evidence));
                deductions.add(new Deduction(
                        getCategory(),
                        "LAST_PUSH_RECENCY",
                        evidence,
                        "Inactivity between 6 and 12 months suggests development has slowed or paused.",
                        5, 8,
                        "Review open issues or dependencies to maintain project health.",
                        List.of(
                                "Review open pull requests or issues",
                                "Apply dependency updates and push"
                        ),
                        null,
                        20, 5
                ));
            } else if (daysSincePush > 90) {
                points -= 2;
                String evidence = "Last commit was between 3 and 6 months ago (" + daysSincePush + " days ago).";
                failedChecks.add(new CheckResult("LAST_PUSH_RECENCY", "Recent Push Activity", false, 6, 8, evidence));
                deductions.add(new Deduction(
                        getCategory(),
                        "LAST_PUSH_RECENCY",
                        evidence,
                        "Quarterly updates signal vitality and ongoing maintenance.",
                        2, 8,
                        "Keep regular commit activity to demonstrate project vitality.",
                        List.of("Perform periodic maintenance updates"),
                        null,
                        15, 2
                ));
            } else {
                passedChecks.add(new CheckResult("LAST_PUSH_RECENCY", "Recent Push Activity", true, 8, 8,
                        "Recent commit activity detected (" + daysSincePush + " days ago)."));
            }
        }

        // 3. Recent Commit Cadence (6 pts)
        List<Instant> commits = snapshot.recentCommitDates();
        int commitCount = commits != null ? commits.size() : 0;

        if (commitCount == 0) {
            points -= 6;
            String evidence = "No recent commits found in commit history.";
            failedChecks.add(new CheckResult("COMMIT_CADENCE", "Commit Cadence", false, 0, 6, evidence));
            deductions.add(new Deduction(
                    getCategory(),
                    "COMMIT_CADENCE",
                    evidence,
                    "An empty commit history gives no insight into the development progression or iteration cadence.",
                    6, 6,
                    "Commit your work incrementally rather than in single bulk uploads.",
                    List.of(
                            "Break work into logical, atomic commits",
                            "Write descriptive commit messages following Conventional Commits"
                    ),
                    null,
                    30, 6
            ));
        } else if (commitCount < 3) {
            points -= 4;
            String evidence = "Very few recent commits recorded (" + commitCount + " commits).";
            failedChecks.add(new CheckResult("COMMIT_CADENCE", "Commit Cadence", false, 2, 6, evidence));
            deductions.add(new Deduction(
                    getCategory(),
                    "COMMIT_CADENCE",
                    evidence,
                    "Fewer than 3 recent commits indicates sporadic or monolithic update habits.",
                    4, 6,
                    "Adopt an iterative development cadence with clear, descriptive commit messages.",
                    List.of("Commit smaller iterative units of work frequently"),
                    null,
                    20, 4
            ));
        } else if (commitCount < 10) {
            points -= 2;
            String evidence = "Moderate recent commit activity (" + commitCount + " commits recorded).";
            failedChecks.add(new CheckResult("COMMIT_CADENCE", "Commit Cadence", false, 4, 6, evidence));
            deductions.add(new Deduction(
                    getCategory(),
                    "COMMIT_CADENCE",
                    evidence,
                    "Maintaining 10+ recent commits shows steady progress and active iteration.",
                    2, 6,
                    "Continue maintaining regular commit activity.",
                    List.of("Maintain steady commit cadence across development milestones"),
                    null,
                    15, 2
            ));
        } else {
            passedChecks.add(new CheckResult("COMMIT_CADENCE", "Commit Cadence", true, 6, 6,
                    "Strong commit cadence with " + commitCount + " recent commits recorded."));
        }

        return new CategoryResult(getCategory(), Math.max(0, points), getCategory().getMaxPoints(), passedChecks, failedChecks, deductions);
    }
}
