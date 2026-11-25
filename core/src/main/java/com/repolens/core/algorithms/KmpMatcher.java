package com.repolens.core.algorithms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hand-crafted Knuth-Morris-Pratt (KMP) string matching algorithm.
 * <p>
 * Used in RepoLens for:
 * <ul>
 *   <li>Scanning repository README contents for secret-looking strings (e.g. {@code AWS_SECRET_ACCESS_KEY}, {@code ghp_}, {@code BEGIN RSA PRIVATE KEY}).</li>
 *   <li>Searching file paths and descriptions for specific junk patterns without backtracking.</li>
 * </ul>
 *
 * <h3>Complexity Analysis (Viva Reference):</h3>
 * <ul>
 *   <li><b>Preprocessing ($\pi$-table / LPS array):</b> $O(m)$ time and $O(m)$ space where $m$ is the pattern length. Computes the Longest Proper Prefix which is also a Suffix.</li>
 *   <li><b>Searching:</b> $O(n)$ time where $n$ is the text length. Avoids re-examining previously matched characters by sliding pattern using the LPS array.</li>
 *   <li><b>Total Time Complexity:</b> $O(n + m)$, strictly optimal and immune to worst-case $O(n \cdot m)$ degenerations of naive search.</li>
 *   <li><b>Space Complexity:</b> $O(m)$ auxiliary space for the LPS array.</li>
 * </ul>
 */
public class KmpMatcher {

    /**
     * Precomputes the Longest Proper Prefix which is also Suffix (LPS) array for the given pattern.
     * <p>Time Complexity: $O(m)$</p>
     *
     * @param pattern the search pattern
     * @return array where lps[i] is the length of the longest proper prefix of pattern[0..i] that is also a suffix of pattern[0..i]
     */
    public static int[] computeLpsTable(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return new int[0];
        }
        int m = pattern.length();
        int[] lps = new int[m];
        int len = 0; // length of the previous longest prefix suffix
        int i = 1;

        lps[0] = 0; // lps[0] is always 0

        while (i < m) {
            if (pattern.charAt(i) == pattern.charAt(len)) {
                len++;
                lps[i] = len;
                i++;
            } else {
                if (len != 0) {
                    len = lps[len - 1]; // fallback without incrementing i
                } else {
                    lps[i] = 0;
                    i++;
                }
            }
        }
        return lps;
    }

    /**
     * Searches for the first occurrence of pattern in text.
     * <p>Time Complexity: $O(n + m)$</p>
     *
     * @param text    the source text to scan
     * @param pattern the search pattern
     * @return zero-based index of the first match, or -1 if not found
     */
    public static int indexOf(String text, String pattern) {
        return indexOf(text, pattern, false);
    }

    /**
     * Searches for the first occurrence of pattern in text with optional case-insensitivity.
     * <p>Time Complexity: $O(n + m)$</p>
     *
     * @param text            the source text
     * @param pattern         the pattern to search
     * @param caseInsensitive whether to ignore case
     * @return zero-based start index of first occurrence, or -1 if not found
     */
    public static int indexOf(String text, String pattern, boolean caseInsensitive) {
        if (text == null || pattern == null || pattern.isEmpty() || text.length() < pattern.length()) {
            return -1;
        }

        String searchPattern = caseInsensitive ? pattern.toLowerCase() : pattern;
        String searchText = caseInsensitive ? text.toLowerCase() : text;

        int[] lps = computeLpsTable(searchPattern);
        int n = searchText.length();
        int m = searchPattern.length();

        int i = 0; // index for text
        int j = 0; // index for pattern

        while (i < n) {
            if (searchText.charAt(i) == searchPattern.charAt(j)) {
                i++;
                j++;
            }

            if (j == m) {
                return i - j; // match found
            } else if (i < n && searchText.charAt(i) != searchPattern.charAt(j)) {
                if (j != 0) {
                    j = lps[j - 1];
                } else {
                    i++;
                }
            }
        }
        return -1;
    }

    /**
     * Checks if text contains pattern (case-sensitive).
     *
     * @param text    the source text
     * @param pattern the pattern
     * @return true if pattern is found in text
     */
    public static boolean contains(String text, String pattern) {
        return indexOf(text, pattern, false) != -1;
    }

    /**
     * Checks if text contains pattern (case-insensitive).
     *
     * @param text    the source text
     * @param pattern the pattern
     * @return true if pattern is found in text ignoring case
     */
    public static boolean containsIgnoreCase(String text, String pattern) {
        return indexOf(text, pattern, true) != -1;
    }

    /**
     * Finds all starting indices of pattern in text.
     * <p>Time Complexity: $O(n + m)$</p>
     *
     * @param text    the source text
     * @param pattern the pattern
     * @return list of 0-based indices where pattern starts
     */
    public static List<Integer> findAllOccurrences(String text, String pattern) {
        if (text == null || pattern == null || pattern.isEmpty() || text.length() < pattern.length()) {
            return Collections.emptyList();
        }

        List<Integer> occurrences = new ArrayList<>();
        int[] lps = computeLpsTable(pattern);
        int n = text.length();
        int m = pattern.length();

        int i = 0;
        int j = 0;

        while (i < n) {
            if (text.charAt(i) == pattern.charAt(j)) {
                i++;
                j++;
            }

            if (j == m) {
                occurrences.add(i - j);
                j = lps[j - 1]; // continue searching for next occurrence
            } else if (i < n && text.charAt(i) != pattern.charAt(j)) {
                if (j != 0) {
                    j = lps[j - 1];
                } else {
                    i++;
                }
            }
        }
        return occurrences;
    }
}
