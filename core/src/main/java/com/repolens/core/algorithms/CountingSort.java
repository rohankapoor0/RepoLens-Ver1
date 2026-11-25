package com.repolens.core.algorithms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Hand-crafted Counting Sort Algorithm.
 * <p>
 * A non-comparison-based integer sorting algorithm designed specifically for bounded integer domains.
 * <p>
 * In RepoLens, repository quality scores are deterministically bounded within the range:
 * $$0 \le \text{score} \le 100$$
 * Counting Sort allows RepoLens to sort all repositories in strictly linear time ($O(n)$),
 * completely bypassing the theoretical $O(n \log n)$ lower bound of comparison-based sorting algorithms (like Quicksort/Mergesort).
 *
 * <h3>Complexity Analysis (Viva Reference):</h3>
 * <ul>
 *   <li><b>Time Complexity:</b> $O(n + k)$ where $n$ is the number of elements and $k = (\max - \min + 1)$ is the range size.
 *       Since $k = 101$ is a small constant, the execution time is strictly $O(n)$ linear!</li>
 *   <li><b>Space Complexity:</b> $O(n + k)$ auxiliary memory for the frequency counts and the output array.</li>
 *   <li><b>Stability:</b> Fully stable. Elements with identical scores retain their original relative input order.</li>
 * </ul>
 */
public class CountingSort {

    /** Default minimum score for RepoLens hygiene scores */
    public static final int DEFAULT_MIN_SCORE = 0;
    /** Default maximum score for RepoLens hygiene scores */
    public static final int DEFAULT_MAX_SCORE = 100;

    /**
     * Sorts an array of hygiene scores in ascending order ($0 \le \text{score} \le 100$).
     * <p>Time Complexity: $O(n)$ linear</p>
     *
     * @param scores array of scores
     * @return newly sorted array in ascending order
     */
    public static int[] sort(int[] scores) {
        return sort(scores, DEFAULT_MIN_SCORE, DEFAULT_MAX_SCORE);
    }

    /**
     * Sorts an array of integers bounded within $[minVal, maxVal]$ in ascending order.
     * <p>Time Complexity: $O(n + k)$ where $k = maxVal - minVal + 1$</p>
     *
     * @param array  input array
     * @param minVal minimum possible value in array
     * @param maxVal maximum possible value in array
     * @return newly allocated sorted array
     */
    public static int[] sort(int[] array, int minVal, int maxVal) {
        if (array == null || array.length <= 1) {
            return array == null ? new int[0] : array.clone();
        }
        if (minVal > maxVal) {
            throw new IllegalArgumentException("minVal cannot exceed maxVal: " + minVal + " > " + maxVal);
        }

        int range = maxVal - minVal + 1;
        int[] counts = new int[range];

        // 1. Calculate frequency of each value
        for (int value : array) {
            if (value < minVal || value > maxVal) {
                throw new IllegalArgumentException("Value out of declared range [" + minVal + ", " + maxVal + "]: " + value);
            }
            counts[value - minVal]++;
        }

        // 2. Compute cumulative counts for stable placement
        for (int i = 1; i < range; i++) {
            counts[i] += counts[i - 1];
        }

        // 3. Build sorted output array traversing backward to preserve stability
        int[] sorted = new int[array.length];
        for (int i = array.length - 1; i >= 0; i--) {
            int value = array[i];
            int position = counts[value - minVal] - 1;
            sorted[position] = value;
            counts[value - minVal]--;
        }

        return sorted;
    }

    /**
     * Sorts an array of scores in descending order (highest score first).
     *
     * @param scores array of scores
     * @return newly sorted array in descending order
     */
    public static int[] sortDescending(int[] scores) {
        int[] sortedAsc = sort(scores, DEFAULT_MIN_SCORE, DEFAULT_MAX_SCORE);
        int[] sortedDesc = new int[sortedAsc.length];
        for (int i = 0; i < sortedAsc.length; i++) {
            sortedDesc[i] = sortedAsc[sortedAsc.length - 1 - i];
        }
        return sortedDesc;
    }

    /**
     * Stably sorts a list of arbitrary objects by an integer score function using Counting Sort.
     * <p>Time Complexity: $O(n + k)$</p>
     *
     * @param items          list of domain objects (e.g. Repository)
     * @param scoreExtractor function extracting integer score in range $[0, 100]$
     * @param ascending      true for ascending, false for descending
     * @param <T>            domain object type
     * @return stable sorted list of items
     */
    public static <T> List<T> sortByScore(List<T> items, ToIntFunction<T> scoreExtractor, boolean ascending) {
        if (items == null || items.size() <= 1) {
            return items == null ? Collections.emptyList() : new ArrayList<>(items);
        }

        int range = DEFAULT_MAX_SCORE - DEFAULT_MIN_SCORE + 1;
        int[] counts = new int[range];

        // 1. Count frequencies
        for (T item : items) {
            int score = scoreExtractor.applyAsInt(item);
            if (score < DEFAULT_MIN_SCORE || score > DEFAULT_MAX_SCORE) {
                throw new IllegalArgumentException("Score out of range [0, 100]: " + score);
            }
            counts[score]++;
        }

        // 2. Cumulative prefix sums
        if (ascending) {
            for (int i = 1; i < range; i++) {
                counts[i] += counts[i - 1];
            }
        } else {
            // For descending order, accumulate from highest score (range - 1) down to 0
            for (int i = range - 2; i >= 0; i--) {
                counts[i] += counts[i + 1];
            }
        }

        // 3. Stable placement into output array traversing backward
        @SuppressWarnings("unchecked")
        T[] output = (T[]) new Object[items.size()];
        for (int i = items.size() - 1; i >= 0; i--) {
            T item = items.get(i);
            int score = scoreExtractor.applyAsInt(item);
            int position = counts[score] - 1;
            output[position] = item;
            counts[score]--;
        }

        List<T> result = new ArrayList<>(items.size());
        Collections.addAll(result, output);
        return result;
    }
}
