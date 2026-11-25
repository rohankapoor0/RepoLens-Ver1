package com.repolens.core.algorithms;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Hand-crafted Quickselect (Hoare's Selection Algorithm).
 * <p>
 * Finds the $k$-th smallest element in an unordered collection without sorting the entire dataset.
 * Includes median-of-three pivot selection to prevent worst-case $O(n^2)$ behavior on already sorted inputs.
 * <p>
 * Used in RepoLens for:
 * <ul>
 *   <li>Computing statistical medians of repository hygiene scores across a user profile.</li>
 *   <li>Determining 25th and 75th percentile score thresholds for ranking brackets in $O(n)$ average time.</li>
 * </ul>
 *
 * <h3>Complexity Analysis (Viva Reference):</h3>
 * <ul>
 *   <li><b>Average Time Complexity:</b> $O(n)$ linear time. At each step, partitioning discards half of the array ($n + n/2 + n/4 + \dots \le 2n$).</li>
 *   <li><b>Worst-case Time Complexity:</b> $O(n^2)$ (mitigated to near impossibility via median-of-three pivot selection).</li>
 *   <li><b>Space Complexity:</b> $O(1)$ auxiliary space when implemented iteratively (in-place partitioning).</li>
 * </ul>
 */
public class Quickselect {

    /**
     * Finds the $k$-th smallest element (0-indexed) in an integer array.
     * Note: This method modifies the ordering of elements in {@code arr} in-place.
     * <p>Time Complexity: $O(n)$ average</p>
     *
     * @param arr the input array
     * @param k   0-based index of the target rank ($0 \le k < \text{arr.length}$)
     * @return the $k$-th smallest element
     */
    public static int select(int[] arr, int k) {
        if (arr == null || arr.length == 0) {
            throw new IllegalArgumentException("Array cannot be null or empty");
        }
        if (k < 0 || k >= arr.length) {
            throw new IndexOutOfBoundsException("k must be between 0 and " + (arr.length - 1) + ", got: " + k);
        }

        int left = 0;
        int right = arr.length - 1;

        while (left <= right) {
            if (left == right) {
                return arr[left];
            }

            int pivotIndex = partition(arr, left, right);

            if (k == pivotIndex) {
                return arr[k];
            } else if (k < pivotIndex) {
                right = pivotIndex - 1;
            } else {
                left = pivotIndex + 1;
            }
        }
        return arr[k];
    }

    /**
     * Computes the exact median of an integer array in $O(n)$ average time without full sorting.
     *
     * @param arr input array (cloned internally to avoid mutating caller's data)
     * @return median score
     */
    public static double median(int[] arr) {
        if (arr == null || arr.length == 0) {
            throw new IllegalArgumentException("Array cannot be null or empty");
        }
        int[] copy = Arrays.copyOf(arr, arr.length);
        int n = copy.length;

        if (n % 2 == 1) {
            return select(copy, n / 2);
        } else {
            int mid1 = select(copy, (n / 2) - 1);
            int mid2 = select(copy, n / 2);
            return (mid1 + mid2) / 2.0;
        }
    }

    /**
     * Computes the element at the specified percentile ($0.0 \le p \le 100.0$) in $O(n)$ average time.
     *
     * @param arr        input array
     * @param percentile percentile value (e.g. 25.0, 75.0)
     * @return value at that percentile rank
     */
    public static double percentile(int[] arr, double percentile) {
        if (arr == null || arr.length == 0) {
            throw new IllegalArgumentException("Array cannot be null or empty");
        }
        if (percentile < 0.0 || percentile > 100.0) {
            throw new IllegalArgumentException("Percentile must be between 0 and 100, got: " + percentile);
        }

        int[] copy = Arrays.copyOf(arr, arr.length);
        int rank = (int) Math.round((percentile / 100.0) * (copy.length - 1));
        return select(copy, rank);
    }

    /**
     * Generic Quickselect for objects with a custom comparator.
     *
     * @param arr        array of elements
     * @param k          0-based target rank
     * @param comparator comparator defining order
     * @param <T>        element type
     * @return the $k$-th smallest element
     */
    public static <T> T select(T[] arr, int k, Comparator<? super T> comparator) {
        if (arr == null || arr.length == 0) {
            throw new IllegalArgumentException("Array cannot be null or empty");
        }
        if (k < 0 || k >= arr.length) {
            throw new IndexOutOfBoundsException("k out of bounds: " + k);
        }
        if (comparator == null) {
            throw new IllegalArgumentException("Comparator cannot be null");
        }

        int left = 0;
        int right = arr.length - 1;

        while (left <= right) {
            if (left == right) {
                return arr[left];
            }
            int pivotIndex = partitionGeneric(arr, left, right, comparator);
            if (k == pivotIndex) {
                return arr[k];
            } else if (k < pivotIndex) {
                right = pivotIndex - 1;
            } else {
                left = pivotIndex + 1;
            }
        }
        return arr[k];
    }

    /* ---------------- Internal Partitioning with Median-of-Three ---------------- */

    private static int partition(int[] arr, int left, int right) {
        // Median-of-three: left, mid, right
        int mid = left + (right - left) / 2;
        if (arr[left] > arr[mid]) swap(arr, left, mid);
        if (arr[left] > arr[right]) swap(arr, left, right);
        if (arr[mid] > arr[right]) swap(arr, mid, right);

        // Place median at right - 1
        swap(arr, mid, right);
        int pivot = arr[right];

        int i = left;
        for (int j = left; j < right; j++) {
            if (arr[j] <= pivot) {
                swap(arr, i, j);
                i++;
            }
        }
        swap(arr, i, right);
        return i;
    }

    private static <T> int partitionGeneric(T[] arr, int left, int right, Comparator<? super T> comp) {
        int mid = left + (right - left) / 2;
        if (comp.compare(arr[left], arr[mid]) > 0) swapGeneric(arr, left, mid);
        if (comp.compare(arr[left], arr[right]) > 0) swapGeneric(arr, left, right);
        if (comp.compare(arr[mid], arr[right]) > 0) swapGeneric(arr, mid, right);

        swapGeneric(arr, mid, right);
        T pivot = arr[right];

        int i = left;
        for (int j = left; j < right; j++) {
            if (comp.compare(arr[j], pivot) <= 0) {
                swapGeneric(arr, i, j);
                i++;
            }
        }
        swapGeneric(arr, i, right);
        return i;
    }

    private static void swap(int[] arr, int i, int j) {
        int temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }

    private static <T> void swapGeneric(T[] arr, int i, int j) {
        T temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }
}
