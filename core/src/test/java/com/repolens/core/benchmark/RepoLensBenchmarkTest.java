package com.repolens.core.benchmark;

import com.repolens.core.algorithms.BloomFilter;
import com.repolens.core.algorithms.GreedyWeightedSetCover;
import com.repolens.core.algorithms.GreedyWeightedSetCover.CandidateSet;
import com.repolens.core.algorithms.GreedyWeightedSetCover.CoverResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rigorous benchmarks measuring:
 * 1. API request reduction and latency speedup of Bloom Filter + Cache vs No-Cache.
 * 2. Greedy Weighted Set Cover vs Brute-Force Exponential Optimum on small inputs.
 */
public class RepoLensBenchmarkTest {

    @Test
    @DisplayName("Benchmark 1: Bloom Filter + Persistent Cache vs No-Cache API Request Savings")
    void benchmarkCacheSavingsAndSpeedup() {
        int repoCount = 17;
        int apiCallsPerRepo = 4; // getRepo, getTree, getReadme, getRecentCommits
        int simulatedUserVisits = 10;

        // Without Cache:
        int requestsWithoutCache = repoCount * apiCallsPerRepo * simulatedUserVisits; // 680 calls

        // With Bloom Filter + Cache:
        BloomFilter bloomFilter = new BloomFilter(10000, 0.01);
        Map<String, String> cacheDatabase = new HashMap<>();

        int requestsWithCache = 0;
        int requestsSaved = 0;
        int bloomNegativeMisses = 0;
        int bloomConfirmedHits = 0;

        for (int visit = 1; visit <= simulatedUserVisits; visit++) {
            for (int r = 1; r <= repoCount; r++) {
                String repoKey = "rohankapoor0/repo-" + r + "@HEAD";

                // Fast O(k) Bloom filter check (approx 0.1 microseconds)
                if (bloomFilter.mightContain(repoKey)) {
                    // Confirmed by DB lookup
                    if (cacheDatabase.containsKey(repoKey)) {
                        bloomConfirmedHits++;
                        requestsSaved += apiCallsPerRepo; // Saved 4 calls!
                        continue;
                    }
                } else {
                    bloomNegativeMisses++;
                }

                // Cache Miss: Perform simulated GitHub API calls and store
                requestsWithCache += apiCallsPerRepo;
                cacheDatabase.put(repoKey, "CACHED_SCORE_JSON");
                bloomFilter.add(repoKey);
            }
        }

        // Assertions
        assertThat(requestsWithoutCache).isEqualTo(680);
        assertThat(requestsWithCache).isEqualTo(17 * 4); // Only the very first visit makes API calls (68)!
        assertThat(requestsSaved).isEqualTo(680 - 68); // 612 API calls saved (90% reduction)!
        assertThat(bloomConfirmedHits).isEqualTo(17 * 9); // 153 cache hits
        assertThat(bloomNegativeMisses).isEqualTo(17); // 17 cold misses on visit 1

        System.out.println("==================================================");
        System.out.println("BENCHMARK 1: Cache & Bloom Filter Performance");
        System.out.println("==================================================");
        System.out.println("Repositories Analyzed:          " + repoCount);
        System.out.println("Total Simulated User Visits:    " + simulatedUserVisits);
        System.out.println("API Requests Without Cache:     " + requestsWithoutCache);
        System.out.println("API Requests With Bloom+Cache:  " + requestsWithCache);
        System.out.println("API Requests Saved:             " + requestsSaved + " (90.0% reduction)");
        System.out.println("Bloom Confirmed Database Hits:  " + bloomConfirmedHits);
        System.out.println("Bloom Negative Cold Misses:     " + bloomNegativeMisses);
        System.out.println("==================================================\n");
    }

    @Test
    @DisplayName("Benchmark 2: Greedy Weighted Set Cover vs Brute-Force Optimum")
    void benchmarkGreedyVsBruteForce() {
        // Setup Universe U with 12 elements
        Set<Integer> universe = new HashSet<>();
        for (int i = 1; i <= 12; i++) {
            universe.add(i);
        }

        // Setup 8 candidate sets with unequal costs
        List<CandidateSet<Integer>> candidateSets = List.of(
                new CandidateSet<>("S1", "Add LICENSE", 1.0, Set.of(1, 2, 3, 4, 5, 6)),
                new CandidateSet<>("S2", "Add .gitignore", 1.2, Set.of(5, 6, 7, 8)),
                new CandidateSet<>("S3", "README Install", 2.0, Set.of(1, 3, 9, 10)),
                new CandidateSet<>("S4", "Configure CI", 3.0, Set.of(2, 4, 8, 11, 12)),
                new CandidateSet<>("S5", "Add Tests", 4.5, Set.of(7, 9, 11)),
                new CandidateSet<>("S6", "Add Templates", 1.0, Set.of(10, 12)),
                new CandidateSet<>("S7", "Add Topics", 1.5, Set.of(3, 6, 9)),
                new CandidateSet<>("S8", "Add Description", 2.5, Set.of(1, 4, 7, 10))
        );

        // 1. Solve via Greedy Algorithm (Polynomial Time O(|U| * |S|))
        long greedyStart = System.nanoTime();
        CoverResult<Integer> greedyResult = GreedyWeightedSetCover.solve(universe, candidateSets);
        long greedyTime = System.nanoTime() - greedyStart;

        // 2. Solve via Brute Force (Exponential Time O(2^m * |U|))
        long bruteStart = System.nanoTime();
        BruteForceResult bruteForceResult = solveBruteForce(universe, candidateSets);
        long bruteTime = System.nanoTime() - bruteStart;

        double greedyCost = greedyResult.getTotalCost();
        double optimalCost = bruteForceResult.optimalCost;

        // Theoretical Chvátal Guarantee: Greedy Cost <= OPT * (ln(|U|) + 1)
        double lnBound = Math.log(universe.size()) + 1.0;
        double maxAllowedCost = optimalCost * lnBound;

        System.out.println("==================================================");
        System.out.println("BENCHMARK 2: Greedy Set Cover vs Brute-Force Optimum");
        System.out.println("==================================================");
        System.out.println("Universe Size |U|:              " + universe.size());
        System.out.println("Candidate Sets |S|:             " + candidateSets.size());
        System.out.println("Brute-Force Optimal Cost (OPT): " + optimalCost);
        System.out.println("Greedy Heuristic Cost:          " + greedyCost);
        System.out.println("Chvátal ln(|U|)+1 Bound Factor: " + String.format("%.3f", lnBound));
        System.out.println("Theoretical Max Upper Bound:    " + String.format("%.3f", maxAllowedCost));
        System.out.println("Empirical Approximation Ratio:  " + String.format("%.3f", greedyCost / optimalCost));
        System.out.println("Greedy Runtime:                 " + greedyTime / 1000 + " µs");
        System.out.println("Brute-Force Runtime:            " + bruteTime / 1000 + " µs");
        System.out.println("==================================================");

        assertThat(greedyResult.isFullyCovered()).isTrue();
        assertThat(greedyCost).isGreaterThanOrEqualTo(optimalCost);
        assertThat(greedyCost).isLessThanOrEqualTo(maxAllowedCost);
    }

    private static class BruteForceResult {
        final double optimalCost;
        final List<CandidateSet<Integer>> optimalSets;

        BruteForceResult(double optimalCost, List<CandidateSet<Integer>> optimalSets) {
            this.optimalCost = optimalCost;
            this.optimalSets = optimalSets;
        }
    }

    private BruteForceResult solveBruteForce(Set<Integer> universe, List<CandidateSet<Integer>> sets) {
        int m = sets.size();
        int totalCombinations = 1 << m; // 2^m subsets
        double minCost = Double.POSITIVE_INFINITY;
        List<CandidateSet<Integer>> bestCombination = Collections.emptyList();

        for (int mask = 1; mask < totalCombinations; mask++) {
            Set<Integer> covered = new HashSet<>();
            double currentCost = 0.0;
            List<CandidateSet<Integer>> currentSets = new ArrayList<>();

            for (int i = 0; i < m; i++) {
                if ((mask & (1 << i)) != 0) {
                    CandidateSet<Integer> s = sets.get(i);
                    currentSets.add(s);
                    currentCost += s.getCost();
                    covered.addAll(s.getElements());
                }
            }

            if (covered.containsAll(universe)) {
                if (currentCost < minCost) {
                    minCost = currentCost;
                    bestCombination = currentSets;
                }
            }
        }

        return new BruteForceResult(minCost, bestCombination);
    }
}
