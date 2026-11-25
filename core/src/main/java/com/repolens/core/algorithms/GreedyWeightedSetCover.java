package com.repolens.core.algorithms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Hand-crafted Greedy Approximation Algorithm for the Weighted Set Cover Problem.
 * <p>
 * In the classical Weighted Set Cover problem, given a universe of elements $U$ and a collection of candidate
 * sets $\mathcal{S} = \{S_1, S_2, \dots, S_m\}$ with non-negative costs $c(S_i)$, the goal is to find a subcollection
 * of sets covering $U$ that minimizes the total cost $\sum c(S_i)$.
 * <p>
 * Used in RepoLens for:
 * <ul>
 *   <li>The <b>"Fix Plan" Generator</b>: Given all lost hygiene points across all of a user's repositories (the universe),
 *       we model each candidate fix (e.g. "Add .gitignore", "Add License", "Configure CI") as a set covering specific
 *       points lost in specific repos. The greedy algorithm picks the fewest fixes that recover the most points across the most repos.</li>
 * </ul>
 *
 * <h3>Complexity Analysis (Viva Reference):</h3>
 * <ul>
 *   <li><b>Greedy Strategy:</b> At each iteration, select the candidate set $S$ that minimizes the cost-per-newly-covered-element ratio:
 *       $$\text{ratio}(S) = \frac{\text{cost}(S)}{|S \cap U_{\text{uncovered}}|}$$
 *   </li>
 *   <li><b>Time Complexity:</b> $O(|U| \cdot |\mathcal{S}|)$ in worst case where each iteration scans available candidate sets and covers at least one element.</li>
 *   <li><b>Approximation Guarantee:</b> Approximates the NP-hard optimal solution within a factor of $H(|U|) = \sum_{k=1}^{|U|} \frac{1}{k} \le \ln |U| + 1$ (Chvátal's Theorem, 1979).</li>
 *   <li><b>Space Complexity:</b> $O(|U| + \sum |S_i|)$ memory to maintain universe and candidate sets.</li>
 * </ul>
 */
public class GreedyWeightedSetCover {

    /**
     * Represents a candidate set in the Weighted Set Cover formulation.
     *
     * @param <E> the type of elements covered
     */
    public static class CandidateSet<E> {
        private final String id;
        private final String description;
        private final double cost; // Effort or weight (must be > 0)
        private final Set<E> elements;

        public CandidateSet(String id, String description, double cost, Set<E> elements) {
            if (cost <= 0) {
                throw new IllegalArgumentException("Set cost must be positive, got: " + cost);
            }
            this.id = Objects.requireNonNull(id, "Set id cannot be null");
            this.description = description != null ? description : id;
            this.cost = cost;
            this.elements = elements != null ? new HashSet<>(elements) : Collections.emptySet();
        }

        public String getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }

        public double getCost() {
            return cost;
        }

        public Set<E> getElements() {
            return Collections.unmodifiableSet(elements);
        }

        @Override
        public String toString() {
            return "CandidateSet{" + "id='" + id + '\'' + ", cost=" + cost + ", size=" + elements.size() + '}';
        }
    }

    /**
     * Result of executing the Weighted Set Cover greedy algorithm.
     *
     * @param <E> element type
     */
    public static class CoverResult<E> {
        private final List<CandidateSet<E>> selectedSets;
        private final Set<E> coveredElements;
        private final Set<E> uncoveredElements;
        private final double totalCost;

        public CoverResult(List<CandidateSet<E>> selectedSets, Set<E> coveredElements, Set<E> uncoveredElements, double totalCost) {
            this.selectedSets = selectedSets;
            this.coveredElements = coveredElements;
            this.uncoveredElements = uncoveredElements;
            this.totalCost = totalCost;
        }

        public List<CandidateSet<E>> getSelectedSets() {
            return selectedSets;
        }

        public Set<E> getCoveredElements() {
            return coveredElements;
        }

        public Set<E> getUncoveredElements() {
            return uncoveredElements;
        }

        public double getTotalCost() {
            return totalCost;
        }

        public boolean isFullyCovered() {
            return uncoveredElements.isEmpty();
        }
    }

    /**
     * Solves the Weighted Set Cover problem using Chvátal's greedy heuristic.
     * <p>Time Complexity: $O(|U| \cdot |\mathcal{S}|)$</p>
     *
     * @param universe      the universe of elements to be covered
     * @param candidateSets available candidate sets
     * @param <E>           element type
     * @return the greedy solution with selected sets and coverage summary
     */
    public static <E> CoverResult<E> solve(Set<E> universe, List<CandidateSet<E>> candidateSets) {
        if (universe == null || universe.isEmpty()) {
            return new CoverResult<>(Collections.emptyList(), Collections.emptySet(), Collections.emptySet(), 0.0);
        }
        if (candidateSets == null || candidateSets.isEmpty()) {
            return new CoverResult<>(Collections.emptyList(), Collections.emptySet(), new HashSet<>(universe), 0.0);
        }

        Set<E> uncovered = new HashSet<>(universe);
        Set<E> covered = new HashSet<>();
        List<CandidateSet<E>> selectedSets = new ArrayList<>();
        List<CandidateSet<E>> remainingSets = new ArrayList<>(candidateSets);
        double totalCost = 0.0;

        while (!uncovered.isEmpty()) {
            CandidateSet<E> bestSet = null;
            double bestRatio = Double.POSITIVE_INFINITY;
            int bestNewCoveredCount = 0;

            // Greedily find the candidate set with minimal cost per newly covered element
            for (CandidateSet<E> candidate : remainingSets) {
                int newCoveredCount = 0;
                for (E element : candidate.getElements()) {
                    if (uncovered.contains(element)) {
                        newCoveredCount++;
                    }
                }

                if (newCoveredCount > 0) {
                    double ratio = candidate.getCost() / newCoveredCount;
                    if (ratio < bestRatio) {
                        bestRatio = ratio;
                        bestSet = candidate;
                        bestNewCoveredCount = newCoveredCount;
                    }
                }
            }

            // If no candidate set can cover any remaining element, we stop
            if (bestSet == null || bestNewCoveredCount == 0) {
                break;
            }

            selectedSets.add(bestSet);
            remainingSets.remove(bestSet);
            totalCost += bestSet.getCost();

            for (E elem : bestSet.getElements()) {
                if (uncovered.remove(elem)) {
                    covered.add(elem);
                }
            }
        }

        return new CoverResult<>(selectedSets, covered, uncovered, totalCost);
    }
}
