package com.repolens.scoring.rules;

import com.repolens.core.algorithms.KmpMatcher;
import com.repolens.core.algorithms.Trie;
import com.repolens.scoring.CategoryResult;
import com.repolens.scoring.CategoryScorer;
import com.repolens.scoring.CheckResult;
import com.repolens.scoring.Deduction;
import com.repolens.scoring.RepoSnapshot;
import com.repolens.scoring.ScoreCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates repository safety using hand-crafted {@link Trie} and {@link KmpMatcher}
 * algorithms on file tree paths and README content (15 points max).
 */
public class SafetyScorer implements CategoryScorer {

    private final Trie envTrie;
    private final Trie junkTrie;
    private final Trie keyTrie;

    public SafetyScorer() {
        this.envTrie = new Trie();
        envTrie.insert(".env", "ENV_FILE");
        envTrie.insert(".env.local", "ENV_FILE");
        envTrie.insert(".env.development", "ENV_FILE");
        envTrie.insert(".env.production", "ENV_FILE");
        envTrie.insert(".env.test", "ENV_FILE");
        envTrie.insert("credentials.json", "CREDS");
        envTrie.insert("secrets.yml", "CREDS");
        envTrie.insert("secrets.json", "CREDS");

        this.junkTrie = new Trie();
        junkTrie.insert("node_modules", "BLOAT");
        junkTrie.insert(".ds_store", "OS_JUNK");
        junkTrie.insert("thumbs.db", "OS_JUNK");
        junkTrie.insert("target/classes", "BUILD_JUNK");
        junkTrie.insert("dist/bundle", "BUILD_JUNK");

        this.keyTrie = new Trie();
        keyTrie.insert("id_rsa", "PRIVATE_KEY");
        keyTrie.insert("id_dsa", "PRIVATE_KEY");
        keyTrie.insert("id_ed25519", "PRIVATE_KEY");
        keyTrie.insert("id_ecdsa", "PRIVATE_KEY");
    }

    @Override
    public ScoreCategory getCategory() {
        return ScoreCategory.SAFETY;
    }

    @Override
    public CategoryResult score(RepoSnapshot snapshot) {
        int points = getCategory().getMaxPoints();
        List<Deduction> deductions = new ArrayList<>();
        List<CheckResult> passedChecks = new ArrayList<>();
        List<CheckResult> failedChecks = new ArrayList<>();

        List<String> committedEnvFiles = new ArrayList<>();
        List<String> committedKeys = new ArrayList<>();
        List<String> committedJunk = new ArrayList<>();

        for (String path : snapshot.filePaths()) {
            String lowerPath = path.toLowerCase();
            String filename = extractFilename(lowerPath);

            boolean isEnvTemplate = filename.endsWith(".example") || filename.endsWith(".sample") || filename.endsWith(".template");
            if (!isEnvTemplate && (envTrie.hasPrefixOf(filename) || envTrie.search(filename))) {
                committedEnvFiles.add(path);
            }

            if (keyTrie.hasPrefixOf(filename)
                    || KmpMatcher.contains(filename, ".pem")
                    || KmpMatcher.contains(filename, ".key")
                    || KmpMatcher.contains(filename, ".p12")
                    || KmpMatcher.contains(filename, ".pfx")) {
                committedKeys.add(path);
            }

            if (junkTrie.hasPrefixOf(lowerPath) || junkTrie.search(filename)
                    || KmpMatcher.contains(lowerPath, "node_modules/")
                    || filename.equals(".ds_store")) {
                committedJunk.add(path);
            }
        }

        // 1. Committed .env files (5 pts)
        if (!committedEnvFiles.isEmpty()) {
            points -= 5;
            String evidence = "Committed secret/environment file detected: " + String.join(", ", committedEnvFiles);
            failedChecks.add(new CheckResult("COMMITTED_ENV_SECRETS", "Secret & Environment Files", false, 0, 5, evidence));
            deductions.add(new Deduction(
                    getCategory(),
                    "COMMITTED_ENV_SECRETS",
                    evidence,
                    "Committed environment or credentials files risk immediate compromise of API keys, database passwords, and production secrets.",
                    5, 5,
                    "Remove committed .env or credentials files immediately from git history and add them to .gitignore.",
                    List.of(
                            "Untrack the file using `git rm --cached " + committedEnvFiles.get(0) + "`",
                            "Add the filename to .gitignore",
                            "Commit the removal and push",
                            "Rotate any secrets that were previously committed"
                    ),
                    "git rm --cached " + committedEnvFiles.get(0) + "\necho \"" + committedEnvFiles.get(0) + "\" >> .gitignore\ngit commit -m \"Remove committed secrets\"",
                    15, 5
            ));
        } else {
            passedChecks.add(new CheckResult("COMMITTED_ENV_SECRETS", "Secret & Environment Files", true, 5, 5,
                    "No committed environment files or credentials detected."));
        }

        // 2. Private Keys (4 pts)
        if (!committedKeys.isEmpty()) {
            points -= 4;
            String evidence = "Committed cryptographic private key or certificate: " + String.join(", ", committedKeys);
            failedChecks.add(new CheckResult("COMMITTED_PRIVATE_KEYS", "Cryptographic Keys & Certificates", false, 0, 4, evidence));
            deductions.add(new Deduction(
                    getCategory(),
                    "COMMITTED_PRIVATE_KEYS",
                    evidence,
                    "Private keys in git history permit impersonation and unauthorized cryptographic access across systems.",
                    4, 4,
                    "Revoke exposed keys immediately, remove them from repository history, and ignore in .gitignore.",
                    List.of(
                            "Revoke the compromised key with your certificate authority or server administrator",
                            "Untrack using `git rm --cached " + committedKeys.get(0) + "`",
                            "Add `*.key`, `*.pem`, `id_*` to .gitignore",
                            "Commit and push"
                    ),
                    "git rm --cached " + committedKeys.get(0),
                    15, 4
            ));
        } else {
            passedChecks.add(new CheckResult("COMMITTED_PRIVATE_KEYS", "Cryptographic Keys & Certificates", true, 4, 4,
                    "No private cryptographic keys or certificates found."));
        }

        // 3. Build Junk / Bloat (3 pts)
        if (!committedJunk.isEmpty()) {
            points -= 3;
            String evidence = "Committed build junk or dependencies detected: " + String.join(", ", committedJunk);
            failedChecks.add(new CheckResult("COMMITTED_BUILD_JUNK", "Build Artifacts & OS Junk", false, 0, 3, evidence));
            deductions.add(new Deduction(
                    getCategory(),
                    "COMMITTED_BUILD_JUNK",
                    evidence,
                    "Committed node_modules, build directories, or .DS_Store files cause repository bloat and merge conflicts.",
                    3, 3,
                    "Delete node_modules, .DS_Store, or build artifacts from git tracking and add them to .gitignore.",
                    List.of(
                            "Untrack build junk: `git rm -r --cached " + committedJunk.get(0) + "`",
                            "Add pattern to .gitignore",
                            "Commit and push cleanup"
                    ),
                    "git rm -r --cached " + committedJunk.get(0),
                    10, 3
            ));
        } else {
            passedChecks.add(new CheckResult("COMMITTED_BUILD_JUNK", "Build Artifacts & OS Junk", true, 3, 3,
                    "Repository tree is free of committed build bloat and OS junk."));
        }

        // 4. README Token Scan (3 pts)
        String readme = snapshot.readmeText();
        boolean hasSecretString = false;
        if (readme != null && !readme.isBlank()) {
            hasSecretString = KmpMatcher.contains(readme, "ghp_")
                    || KmpMatcher.contains(readme, "github_pat_")
                    || KmpMatcher.contains(readme, "AKIA")
                    || KmpMatcher.containsIgnoreCase(readme, "BEGIN PRIVATE KEY")
                    || KmpMatcher.containsIgnoreCase(readme, "BEGIN RSA PRIVATE KEY")
                    || KmpMatcher.containsIgnoreCase(readme, "AWS_SECRET_ACCESS_KEY");
        }

        if (hasSecretString) {
            points -= 3;
            String evidence = "Suspected live API token, AWS key, or private key string found in README text.";
            failedChecks.add(new CheckResult("README_SECRETS", "README Secret Scrubbing", false, 0, 3, evidence));
            deductions.add(new Deduction(
                    getCategory(),
                    "README_SECRETS",
                    evidence,
                    "Hardcoded API tokens or secret access keys in documentation are actively crawled and abused by automated bots.",
                    3, 3,
                    "Scrub all secrets, tokens, and private keys from documentation and immediately rotate any exposed credentials.",
                    List.of(
                            "Open README.md and replace secret strings with placeholders (e.g. YOUR_API_KEY)",
                            "Rotate the exposed secret immediately in your cloud provider console",
                            "Commit and push cleaned documentation"
                    ),
                    "export API_KEY=\"<YOUR_API_KEY>\"",
                    10, 3
            ));
        } else {
            passedChecks.add(new CheckResult("README_SECRETS", "README Secret Scrubbing", true, 3, 3,
                    "No suspected secret tokens or credentials found in README text."));
        }

        return new CategoryResult(getCategory(), Math.max(0, points), getCategory().getMaxPoints(), passedChecks, failedChecks, deductions);
    }

    private String extractFilename(String path) {
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }
}
