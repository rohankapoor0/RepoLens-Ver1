package com.repolens.core.planner;

import com.repolens.core.algorithms.GreedyWeightedSetCover;
import com.repolens.core.algorithms.GreedyWeightedSetCover.CandidateSet;
import com.repolens.core.algorithms.GreedyWeightedSetCover.CoverResult;
import com.repolens.scoring.Deduction;
import com.repolens.scoring.ScoreCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates an optimal, prioritized Fix Plan across all evaluated repositories
 * using the hand-crafted {@link GreedyWeightedSetCover} algorithm.
 * <p>
 * Universe = (repo, failed check) pairs.
 * Each candidate fix covers every repo that failed that check.
 * Candidate sets are weighted by effort costs (e.g. add LICENSE = 1, .gitignore = 1, README install section = 2, CI = 3, tests = 5).
 */
public class FixPlanner {

    public static final Map<String, Double> DEFAULT_EFFORT_COSTS;

    static {
        Map<String, Double> costs = new LinkedHashMap<>();
        costs.put("ADD_LICENSE", 1.0);
        costs.put("ADD_GITIGNORE", 1.0);
        costs.put("ADD_TOPICS", 1.0);
        costs.put("ADD_DESCRIPTION", 1.0);
        costs.put("ADD_CONTRIBUTING", 1.0);
        costs.put("ADD_TEMPLATES", 1.0);
        costs.put("ADD_INSTALL_USAGE", 2.0);
        costs.put("ADD_DEMO_IMAGE", 2.0);
        costs.put("CREATE_README", 2.0);
        costs.put("ADD_BUILD_CONFIG", 2.0);
        costs.put("CLEAN_COMMITTED_SECRETS", 2.0);
        costs.put("REORGANIZE_LAYOUT", 2.0);
        costs.put("ADD_CI", 3.0);
        costs.put("ADD_TESTS", 5.0);
        DEFAULT_EFFORT_COSTS = Collections.unmodifiableMap(costs);
    }

    private final Map<String, Double> effortCosts;

    public FixPlanner() {
        this(DEFAULT_EFFORT_COSTS);
    }

    public FixPlanner(Map<String, Double> customCosts) {
        if (customCosts == null || customCosts.isEmpty()) {
            this.effortCosts = DEFAULT_EFFORT_COSTS;
        } else {
            Map<String, Double> merged = new LinkedHashMap<>(DEFAULT_EFFORT_COSTS);
            merged.putAll(customCosts);
            this.effortCosts = Collections.unmodifiableMap(merged);
        }
    }

    /**
     * Plans prioritized fixes using Chvátal's Greedy Weighted Set Cover algorithm.
     *
     * @param deductionsByRepo map of repository name to its itemized deductions
     * @param totalReposCount  total number of repositories analyzed
     * @return an optimal, ordered {@link FixPlan}
     */
    public FixPlan plan(Map<String, List<Deduction>> deductionsByRepo, int totalReposCount) {
        if (deductionsByRepo == null || deductionsByRepo.isEmpty() || totalReposCount <= 0) {
            return new FixPlan("No repositories analyzed", 0, totalReposCount, 0, 0.0, List.of());
        }

        Set<RepoDeficiency> universe = new HashSet<>();
        Map<String, Set<RepoDeficiency>> elementsByCheck = new HashMap<>();
        Map<String, String> titleByCheck = new HashMap<>();
        Map<String, String> suggestedActionByCheck = new HashMap<>();

        for (Map.Entry<String, List<Deduction>> entry : deductionsByRepo.entrySet()) {
            String repoName = entry.getKey();
            List<Deduction> deductions = entry.getValue();
            if (deductions == null) continue;

            for (Deduction d : deductions) {
                String checkKey = classifyCheckKey(d);
                RepoDeficiency deficiency = new RepoDeficiency(repoName, checkKey, d.pointsLost(), d.suggestedFix());
                universe.add(deficiency);

                elementsByCheck.computeIfAbsent(checkKey, k -> new HashSet<>()).add(deficiency);
                titleByCheck.putIfAbsent(checkKey, getFixTitle(checkKey));
                suggestedActionByCheck.putIfAbsent(checkKey, d.suggestedFix());
            }
        }

        if (universe.isEmpty()) {
            return new FixPlan(
                    String.format("All %d repositories meet standard hygiene guidelines!", totalReposCount),
                    0, totalReposCount, 0, 0.0, List.of()
            );
        }

        // Construct candidate sets for GreedyWeightedSetCover
        List<CandidateSet<RepoDeficiency>> candidateSets = new ArrayList<>();
        for (Map.Entry<String, Set<RepoDeficiency>> entry : elementsByCheck.entrySet()) {
            String checkKey = entry.getKey();
            Set<RepoDeficiency> elements = entry.getValue();
            double cost = effortCosts.getOrDefault(checkKey, 2.0);
            String title = titleByCheck.get(checkKey);

            candidateSets.add(new CandidateSet<>(checkKey, title, cost, elements));
        }

        // Run GreedyWeightedSetCover algorithm
        CoverResult<RepoDeficiency> coverResult = GreedyWeightedSetCover.solve(universe, candidateSets);

        // Build ordered fix items
        List<FixItem> orderedFixes = new ArrayList<>();
        Set<String> distinctImprovedRepos = new HashSet<>();
        int totalPointsRecoverable = 0;
        double totalEffort = 0.0;

        for (CandidateSet<RepoDeficiency> selectedSet : coverResult.getSelectedSets()) {
            String checkKey = selectedSet.getId();
            double cost = selectedSet.getCost();
            String title = selectedSet.getDescription();
            String action = suggestedActionByCheck.getOrDefault(checkKey, "Implement recommended hygiene improvements.");

            List<String> improvedRepos = new ArrayList<>();
            int pointsForThisFix = 0;

            for (RepoDeficiency def : selectedSet.getElements()) {
                improvedRepos.add(def.repoName());
                distinctImprovedRepos.add(def.repoName());
                pointsForThisFix += def.pointsLost();
            }

            Collections.sort(improvedRepos);
            orderedFixes.add(new FixItem(checkKey, title, cost, improvedRepos, pointsForThisFix, action));
            totalPointsRecoverable += pointsForThisFix;
            totalEffort += cost;
        }

        int improvedCount = distinctImprovedRepos.size();
        String headline = String.format("%d fixes improve %d of your %d repos",
                orderedFixes.size(), improvedCount, totalReposCount);

        return new FixPlan(
                headline,
                improvedCount,
                totalReposCount,
                totalPointsRecoverable,
                totalEffort,
                orderedFixes
        );
    }

    /**
     * Classifies a deduction into a canonical candidate check key.
     */
    public static String classifyCheckKey(Deduction d) {
        String reason = d.reason().toLowerCase();

        if (reason.contains("license")) return "ADD_LICENSE";
        if (reason.contains(".gitignore")) return "ADD_GITIGNORE";
        if (reason.contains("contributing")) return "ADD_CONTRIBUTING";
        if (reason.contains("template")) return "ADD_TEMPLATES";
        if (reason.contains("topic")) return "ADD_TOPICS";
        if (reason.contains("description is empty")) return "ADD_DESCRIPTION";
        if (reason.contains("no readme file")) return "CREATE_README";
        if (reason.contains("installation") || reason.contains("usage") || reason.contains("heading")) return "ADD_INSTALL_USAGE";
        if (reason.contains("image") || reason.contains("screenshot")) return "ADD_DEMO_IMAGE";
        if (reason.contains("build configuration") || reason.contains("package manifest")) return "ADD_BUILD_CONFIG";
        if (reason.contains("ci/cd") || reason.contains("workflow")) return "ADD_CI";
        if (reason.contains("automated test") || reason.contains("test files")) return "ADD_TESTS";
        if (d.category() == ScoreCategory.SAFETY) return "CLEAN_COMMITTED_SECRETS";
        if (d.category() == ScoreCategory.STRUCTURE) return "REORGANIZE_LAYOUT";

        return "GENERAL_" + d.category().name();
    }

    /**
     * Returns a human-friendly title for a check key.
     */
    public static String getFixTitle(String checkKey) {
        return switch (checkKey) {
            case "ADD_LICENSE" -> "Add Open Source LICENSE";
            case "ADD_GITIGNORE" -> "Add .gitignore File";
            case "ADD_CONTRIBUTING" -> "Add CONTRIBUTING Guidelines";
            case "ADD_TEMPLATES" -> "Add Issue & PR Templates";
            case "ADD_TOPICS" -> "Add GitHub Topics / Tags";
            case "ADD_DESCRIPTION" -> "Add Repository Description";
            case "CREATE_README" -> "Create Comprehensive README.md";
            case "ADD_INSTALL_USAGE" -> "Add Installation & Usage Sections to README";
            case "ADD_DEMO_IMAGE" -> "Add Demo Screenshot or Architecture Diagram";
            case "ADD_BUILD_CONFIG" -> "Add Build Configuration / Package Manifest";
            case "ADD_CI" -> "Configure CI/CD Automation Workflow";
            case "ADD_TESTS" -> "Add Automated Test Suite";
            case "CLEAN_COMMITTED_SECRETS" -> "Remove Committed Secrets & Build Junk";
            case "REORGANIZE_LAYOUT" -> "Organize Project Directory Structure";
            default -> "Resolve " + checkKey.replace('_', ' ');
        };
    }
}
