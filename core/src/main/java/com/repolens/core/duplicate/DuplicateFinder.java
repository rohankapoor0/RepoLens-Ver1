package com.repolens.core.duplicate;

import com.repolens.core.algorithms.DisjointSetUnion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects duplicate or near-duplicate repositories using README text hashing,
 * file-tree Jaccard similarity, and name similarity, grouping them via {@link DisjointSetUnion}.
 */
public class DuplicateFinder {

    private static final double MIN_TREE_JACCARD_SIMILARITY = 0.70;

    /**
     * Finds and groups duplicate repositories from a collection of repository profiles.
     *
     * @param profiles list of repository profiles to analyze
     * @return a {@link DuplicateReport} detailing all duplicate clusters and reasons
     */
    public DuplicateReport findDuplicates(List<RepoProfile> profiles) {
        if (profiles == null || profiles.size() < 2) {
            return new DuplicateReport(0, 0, List.of());
        }

        DisjointSetUnion<String> dsu = new DisjointSetUnion<>();
        for (RepoProfile p : profiles) {
            dsu.makeSet(p.repoName());
        }

        // Pairwise reasons map: key is repoName -> set of reasons
        Map<String, Set<String>> repoReasons = new HashMap<>();

        int n = profiles.size();
        for (int i = 0; i < n; i++) {
            RepoProfile p1 = profiles.get(i);
            for (int j = i + 1; j < n; j++) {
                RepoProfile p2 = profiles.get(j);

                List<String> matchReasons = evaluatePairSimilarity(p1, p2);
                if (!matchReasons.isEmpty()) {
                    dsu.union(p1.repoName(), p2.repoName());

                    for (String r : matchReasons) {
                        repoReasons.computeIfAbsent(p1.repoName(), k -> new HashSet<>()).add(r);
                        repoReasons.computeIfAbsent(p2.repoName(), k -> new HashSet<>()).add(r);
                    }
                }
            }
        }

        // Retrieve connected components from DSU
        Map<String, List<String>> components = dsu.getComponents();
        List<DuplicateGroup> duplicateGroups = new ArrayList<>();
        int duplicateReposCount = 0;
        int groupCounter = 1;

        for (List<String> groupMembers : components.values()) {
            if (groupMembers.size() >= 2) {
                Collections.sort(groupMembers);
                Set<String> aggregatedReasons = new HashSet<>();
                for (String m : groupMembers) {
                    Set<String> r = repoReasons.get(m);
                    if (r != null) aggregatedReasons.addAll(r);
                }

                List<String> reasonsList = new ArrayList<>(aggregatedReasons);
                Collections.sort(reasonsList);
                String primaryReason = determinePrimaryReason(reasonsList);

                duplicateGroups.add(new DuplicateGroup(groupCounter++, groupMembers, reasonsList, primaryReason));
                duplicateReposCount += groupMembers.size();
            }
        }

        return new DuplicateReport(duplicateGroups.size(), duplicateReposCount, duplicateGroups);
    }

    private List<String> evaluatePairSimilarity(RepoProfile p1, RepoProfile p2) {
        List<String> reasons = new ArrayList<>();

        // 1. README Text Hash Comparison
        String hash1 = computeReadmeHash(p1.readmeText());
        String hash2 = computeReadmeHash(p2.readmeText());
        if (hash1 != null && hash1.equals(hash2)) {
            reasons.add("Identical README content (same content hash)");
        }

        // 2. File-Tree Overlap (Jaccard Similarity)
        Set<String> tree1 = normalizeFilePaths(p1.filePaths());
        Set<String> tree2 = normalizeFilePaths(p2.filePaths());
        if (tree1.size() >= 3 && tree2.size() >= 3) {
            double jaccard = computeJaccardSimilarity(tree1, tree2);
            if (jaccard >= MIN_TREE_JACCARD_SIMILARITY) {
                reasons.add(String.format("High file-tree overlap (%.0f%% matching paths)", jaccard * 100));
            }
        }

        // 3. Similar Repository Names
        if (areNamesSimilar(p1.repoName(), p2.repoName())) {
            // Confirm with shared language, identical description, or non-empty path overlap
            boolean languageMatch = !p1.language().isBlank() && p1.language().equalsIgnoreCase(p2.language());
            boolean descMatch = !p1.description().isBlank() && p1.description().equalsIgnoreCase(p2.description());
            double jaccard = (tree1.isEmpty() || tree2.isEmpty()) ? 0.0 : computeJaccardSimilarity(tree1, tree2);

            if (languageMatch || descMatch || jaccard >= 0.25) {
                reasons.add(String.format("Similar repository names ('%s' vs '%s')", p1.repoName(), p2.repoName()));
            }
        }

        // 4. Identical Non-trivial Description
        if (!p1.description().isBlank() && p1.description().length() >= 25 &&
                p1.description().trim().equalsIgnoreCase(p2.description().trim())) {
            reasons.add("Identical repository description");
        }

        return reasons;
    }

    private String computeReadmeHash(String readme) {
        if (readme == null) return null;
        String normalized = readme.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        if (normalized.length() < 40) {
            return null; // Ignore short or boilerplate READMEs
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(normalized.hashCode());
        }
    }

    private Set<String> normalizeFilePaths(List<String> paths) {
        if (paths == null) return Collections.emptySet();
        Set<String> set = new HashSet<>();
        for (String p : paths) {
            if (p == null || p.isBlank()) continue;
            String lower = p.trim().toLowerCase();
            if (lower.startsWith("./")) lower = lower.substring(2);
            if (lower.startsWith("/")) lower = lower.substring(1);
            if (!lower.startsWith(".git/") && !lower.equals(".git") && !lower.contains(".ds_store")) {
                set.add(lower);
            }
        }
        return set;
    }

    private double computeJaccardSimilarity(Set<String> s1, Set<String> s2) {
        if (s1.isEmpty() && s2.isEmpty()) return 1.0;
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;

        int intersection = 0;
        for (String item : s1) {
            if (s2.contains(item)) {
                intersection++;
            }
        }

        int union = s1.size() + s2.size() - intersection;
        return (double) intersection / (double) union;
    }

    private boolean areNamesSimilar(String name1, String name2) {
        if (name1 == null || name2 == null) return false;
        String norm1 = normalizeName(name1);
        String norm2 = normalizeName(name2);

        if (norm1.equals(norm2)) return true;

        if (norm1.length() >= 5 && norm2.length() >= 5) {
            if (norm1.startsWith(norm2) || norm2.startsWith(norm1)) return true;
            return levenshteinDistance(norm1, norm2) <= 2;
        }

        return false;
    }

    private String normalizeName(String name) {
        String lower = name.toLowerCase();
        // Remove common versioning/suffix tokens
        lower = lower.replaceAll("[-_.]", "");
        lower = lower.replaceAll("(v\\d+|-\\d+|backup|copy|old|main|master)$", "");
        return lower;
    }

    private int levenshteinDistance(String s1, String s2) {
        int[] prev = new int[s2.length() + 1];
        int[] curr = new int[s2.length() + 1];

        for (int j = 0; j <= s2.length(); j++) prev[j] = j;

        for (int i = 1; i <= s1.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            System.arraycopy(curr, 0, prev, 0, prev.length);
        }
        return prev[s2.length()];
    }

    private String determinePrimaryReason(List<String> reasons) {
        for (String r : reasons) {
            if (r.contains("Identical README")) return "Identical README hash";
        }
        for (String r : reasons) {
            if (r.contains("file-tree overlap")) return "High file-tree overlap";
        }
        for (String r : reasons) {
            if (r.contains("Similar repository names")) return "Similar repository names";
        }
        return reasons.isEmpty() ? "Duplicate patterns detected" : reasons.get(0);
    }
}
