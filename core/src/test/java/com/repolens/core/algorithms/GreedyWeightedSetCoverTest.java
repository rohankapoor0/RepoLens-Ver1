package com.repolens.core.algorithms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GreedyWeightedSetCoverTest {

    @Test
    @DisplayName("Solves weighted set cover greedily prioritizing cost-effective fixes")
    void shouldSelectOptimalFixSets() {
        // Universe: 5 points lost across repos
        Set<String> universe = new HashSet<>(Arrays.asList(
                "repoA#gitignore", "repoB#gitignore",
                "repoA#license", "repoB#license", "repoC#license"
        ));

        // Fix 1: "Add .gitignore to all repos" -> covers 2 items, cost = 1.0 (ratio = 0.5)
        GreedyWeightedSetCover.CandidateSet<String> fixGitignore = new GreedyWeightedSetCover.CandidateSet<>(
                "FIX_GITIGNORE", "Add .gitignore", 1.0,
                Set.of("repoA#gitignore", "repoB#gitignore")
        );

        // Fix 2: "Add LICENSE to all repos" -> covers 3 items, cost = 1.0 (ratio = 0.33 -> best first pick!)
        GreedyWeightedSetCover.CandidateSet<String> fixLicense = new GreedyWeightedSetCover.CandidateSet<>(
                "FIX_LICENSE", "Add LICENSE", 1.0,
                Set.of("repoA#license", "repoB#license", "repoC#license")
        );

        // Fix 3: "Expensive custom fix for repoA" -> covers repoA#gitignore and repoA#license, cost = 10.0
        GreedyWeightedSetCover.CandidateSet<String> expensiveFix = new GreedyWeightedSetCover.CandidateSet<>(
                "EXPENSIVE_FIX", "Manual audit", 10.0,
                Set.of("repoA#gitignore", "repoA#license")
        );

        GreedyWeightedSetCover.CoverResult<String> result = GreedyWeightedSetCover.solve(
                universe,
                List.of(fixGitignore, fixLicense, expensiveFix)
        );

        assertThat(result.isFullyCovered()).isTrue();
        assertThat(result.getTotalCost()).isEqualTo(2.0);
        assertThat(result.getSelectedSets()).hasSize(2);
        // First chosen should be fixLicense (best cost-per-element ratio 0.33), then fixGitignore (ratio 0.5)
        assertThat(result.getSelectedSets().get(0).getId()).isEqualTo("FIX_LICENSE");
        assertThat(result.getSelectedSets().get(1).getId()).isEqualTo("FIX_GITIGNORE");
    }

    @Test
    @DisplayName("Unequal costs: cost-per-newly-covered ratio picks a different set than 'largest set first'")
    void shouldPrioritizeRatioOverLargestSetSize() {
        // Universe of 6 elements: {e1, e2, e3, e4, e5, e6}
        Set<String> universe = Set.of("e1", "e2", "e3", "e4", "e5", "e6");

        // Set L (Largest): covers 5 elements {e1, e2, e3, e4, e5}, but has very high cost 100.0 (ratio = 100/5 = 20.0)
        GreedyWeightedSetCover.CandidateSet<String> largestSet = new GreedyWeightedSetCover.CandidateSet<>(
                "SET_LARGEST", "Largest set but very expensive", 100.0,
                Set.of("e1", "e2", "e3", "e4", "e5")
        );

        // Set S1: covers 2 elements {e1, e2}, low cost 1.0 (ratio = 1.0/2 = 0.5)
        GreedyWeightedSetCover.CandidateSet<String> smallSet1 = new GreedyWeightedSetCover.CandidateSet<>(
                "SET_S1", "Small set 1", 1.0,
                Set.of("e1", "e2")
        );

        // Set S2: covers 2 elements {e3, e4}, low cost 1.0 (ratio = 1.0/2 = 0.5)
        GreedyWeightedSetCover.CandidateSet<String> smallSet2 = new GreedyWeightedSetCover.CandidateSet<>(
                "SET_S2", "Small set 2", 1.0,
                Set.of("e3", "e4")
        );

        // Set S3: covers 2 elements {e5, e6}, low cost 1.0 (ratio = 1.0/2 = 0.5)
        GreedyWeightedSetCover.CandidateSet<String> smallSet3 = new GreedyWeightedSetCover.CandidateSet<>(
                "SET_S3", "Small set 3", 1.0,
                Set.of("e5", "e6")
        );

        // A naive "largest set first" strategy would pick SET_LARGEST first (size 5).
        // The cost-effective ratio-based greedy algorithm must pick S1, S2, S3 instead!
        GreedyWeightedSetCover.CoverResult<String> result = GreedyWeightedSetCover.solve(
                universe,
                List.of(largestSet, smallSet1, smallSet2, smallSet3)
        );

        assertThat(result.isFullyCovered()).isTrue();
        assertThat(result.getTotalCost()).isEqualTo(3.0);
        // Verify SET_LARGEST was NEVER selected because its cost ratio was 20.0 vs 0.5
        List<String> selectedIds = result.getSelectedSets().stream()
                .map(GreedyWeightedSetCover.CandidateSet::getId)
                .toList();
        assertThat(selectedIds).doesNotContain("SET_LARGEST");
        assertThat(selectedIds).containsExactlyInAnyOrder("SET_S1", "SET_S2", "SET_S3");
    }

    @Test
    @DisplayName("Compares greedy result with brute-force optimum on small inputs and verifies ln(n)+1 bound")
    void shouldBeWithinLnNPlusOneBoundOfBruteForceOptimum() {
        // Small universe: 7 elements
        Set<Integer> universe = Set.of(1, 2, 3, 4, 5, 6, 7);
        int n = universe.size();

        // Overlapping candidate sets with varied costs
        List<GreedyWeightedSetCover.CandidateSet<Integer>> candidates = List.of(
                new GreedyWeightedSetCover.CandidateSet<>("S1", "Set 1", 2.0, Set.of(1, 2, 3)),
                new GreedyWeightedSetCover.CandidateSet<>("S2", "Set 2", 3.0, Set.of(2, 3, 4, 5)),
                new GreedyWeightedSetCover.CandidateSet<>("S3", "Set 3", 2.5, Set.of(4, 5, 6)),
                new GreedyWeightedSetCover.CandidateSet<>("S4", "Set 4", 1.8, Set.of(6, 7)),
                new GreedyWeightedSetCover.CandidateSet<>("S5", "Set 5", 4.0, Set.of(1, 7)),
                new GreedyWeightedSetCover.CandidateSet<>("S6", "Set 6", 5.0, Set.of(1, 2, 3, 4, 5, 6, 7))
        );

        // 1. Compute true global optimum via exhaustive brute-force search over 2^k subsets
        double optimalCost = computeBruteForceOptimum(universe, candidates);

        // 2. Compute greedy approximation
        GreedyWeightedSetCover.CoverResult<Integer> greedyResult = GreedyWeightedSetCover.solve(universe, candidates);

        assertThat(greedyResult.isFullyCovered()).isTrue();
        double greedyCost = greedyResult.getTotalCost();

        // 3. Greedy cost must be >= optimalCost
        assertThat(greedyCost).isGreaterThanOrEqualTo(optimalCost);

        // 4. Theoretical approximation guarantee: greedyCost <= optimalCost * (ln(n) + 1)
        double theoreticalBound = optimalCost * (Math.log(n) + 1.0 + 1e-9);
        assertThat(greedyCost).isLessThanOrEqualTo(theoreticalBound);
    }

    @Test
    @DisplayName("Handles un-coverable elements and empty inputs gracefully")
    void shouldHandleEdgeCases() {
        Set<String> universe = Set.of("unfixable_item");
        GreedyWeightedSetCover.CoverResult<String> result = GreedyWeightedSetCover.solve(universe, List.of());

        assertThat(result.isFullyCovered()).isFalse();
        assertThat(result.getUncoveredElements()).containsExactly("unfixable_item");
        assertThat(result.getSelectedSets()).isEmpty();
    }

    /**
     * Brute-force solver for small inputs ($k \le 16$) that checks all $2^k$ subsets to find the exact minimum cost cover.
     */
    private <E> double computeBruteForceOptimum(Set<E> universe, List<GreedyWeightedSetCover.CandidateSet<E>> candidates) {
        int k = candidates.size();
        int totalCombinations = 1 << k;
        double minCost = Double.POSITIVE_INFINITY;

        for (int mask = 1; mask < totalCombinations; mask++) {
            Set<E> covered = new HashSet<>();
            double currentCost = 0.0;

            for (int i = 0; i < k; i++) {
                if ((mask & (1 << i)) != 0) {
                    GreedyWeightedSetCover.CandidateSet<E> set = candidates.get(i);
                    covered.addAll(set.getElements());
                    currentCost += set.getCost();
                }
            }

            if (covered.containsAll(universe)) {
                if (currentCost < minCost) {
                    minCost = currentCost;
                }
            }
        }
        return minCost;
    }
}
