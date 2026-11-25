package com.repolens.scoring.rules;

import com.repolens.scoring.CategoryResult;
import com.repolens.scoring.CategoryScorer;
import com.repolens.scoring.CheckResult;
import com.repolens.scoring.Deduction;
import com.repolens.scoring.RepoSnapshot;
import com.repolens.scoring.ScoreCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates repository directory layout and detects single massive files (5 points max).
 */
public class StructureScorer implements CategoryScorer {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L; // 5 MB

    private static final List<String> RECOGNIZED_SOURCE_DIRS = List.of(
            "src/", "lib/", "app/", "pkg/", "internal/", "cmd/", "core/", "test/", "tests/", "docs/", "scripts/", "packages/"
    );

    @Override
    public ScoreCategory getCategory() {
        return ScoreCategory.STRUCTURE;
    }

    @Override
    public CategoryResult score(RepoSnapshot snapshot) {
        int points = getCategory().getMaxPoints();
        List<Deduction> deductions = new ArrayList<>();
        List<CheckResult> passedChecks = new ArrayList<>();
        List<CheckResult> failedChecks = new ArrayList<>();

        List<String> paths = snapshot.filePaths();
        int totalFiles = paths.size();

        // 1. Sensible Directory Layout (3 pts)
        if (totalFiles > 15) {
            boolean hasOrganizedSourceDir = false;
            String foundDir = null;
            for (String p : paths) {
                String lower = p.toLowerCase();
                for (String dir : RECOGNIZED_SOURCE_DIRS) {
                    if (lower.startsWith(dir) || lower.contains("/" + dir)) {
                        hasOrganizedSourceDir = true;
                        foundDir = dir;
                        break;
                    }
                }
                if (hasOrganizedSourceDir) {
                    break;
                }
            }

            if (!hasOrganizedSourceDir) {
                points -= 3;
                String evidence = "Unorganized flat root layout with " + totalFiles + " files and no standard source directories (e.g. src/, lib/, app/).";
                failedChecks.add(new CheckResult("DIRECTORY_ORGANIZATION", "Directory Layout", false, 0, 3, evidence));
                deductions.add(new Deduction(
                        getCategory(),
                        "DIRECTORY_ORGANIZATION",
                        evidence,
                        "Dumping all files at root creates cognitive overload and makes larger projects difficult to navigate.",
                        3, 3,
                        "Organize code into standard directories such as src/, lib/, app/, or docs/ rather than dumping all files at the repository root.",
                        List.of(
                                "Create standard subdirectories: `mkdir -p src`",
                                "Move source code files into `src/`",
                                "Keep only configuration and root documentation at the top level",
                                "Commit restructured tree"
                        ),
                        "mkdir -p src docs",
                        25, 3
                ));
            } else {
                passedChecks.add(new CheckResult("DIRECTORY_ORGANIZATION", "Directory Layout", true, 3, 3,
                        "Organized source directory structure detected (" + foundDir + ")."));
            }
        } else {
            passedChecks.add(new CheckResult("DIRECTORY_ORGANIZATION", "Directory Layout", true, 3, 3,
                    "Repository file count (" + totalFiles + " files) is compact; flat layout acceptable."));
        }

        // 2. Huge Single File Detection (2 pts)
        String hugeFile = null;
        long hugeFileSize = 0;

        for (Map.Entry<String, Long> entry : snapshot.fileSizes().entrySet()) {
            if (entry.getValue() != null && entry.getValue() > MAX_FILE_SIZE_BYTES) {
                hugeFile = entry.getKey();
                hugeFileSize = entry.getValue();
                break;
            }
        }

        if (hugeFile != null) {
            points -= 2;
            long mb = hugeFileSize / (1024 * 1024);
            String evidence = "Large single file detected in repository: " + hugeFile + " (~" + mb + " MB).";
            failedChecks.add(new CheckResult("LARGE_FILES", "Single File Size Limit", false, 0, 2, evidence));
            deductions.add(new Deduction(
                    getCategory(),
                    "LARGE_FILES",
                    evidence,
                    "Committing multi-megabyte binaries directly to git permanently inflates clone sizes for every collaborator.",
                    2, 2,
                    "Avoid committing massive files or dataset binaries directly to git; use Git LFS or external release assets.",
                    List.of(
                            "Remove large file from git history",
                            "Configure Git LFS: `git lfs track \"*.bin\"`",
                            "Store large binaries in GitHub Releases or S3"
                    ),
                    "git lfs install\ngit lfs track \"" + hugeFile + "\"",
                    20, 2
            ));
        } else {
            passedChecks.add(new CheckResult("LARGE_FILES", "Single File Size Limit", true, 2, 2,
                    "All inspected repository files are within reasonable size limits (< 5 MB)."));
        }

        return new CategoryResult(getCategory(), Math.max(0, points), getCategory().getMaxPoints(), passedChecks, failedChecks, deductions);
    }
}
