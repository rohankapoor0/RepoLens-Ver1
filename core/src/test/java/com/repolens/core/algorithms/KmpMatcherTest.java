package com.repolens.core.algorithms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KmpMatcherTest {

    @Test
    @DisplayName("LPS table is computed correctly according to prefix/suffix rules")
    void shouldComputeCorrectLpsTable() {
        // "AABAACAABAA"
        // indices: 0 1 2 3 4 5 6 7 8 9 10
        // expected: 0 1 0 1 2 0 1 2 3 4 5
        int[] lps = KmpMatcher.computeLpsTable("AABAACAABAA");
        assertThat(lps).containsExactly(0, 1, 0, 1, 2, 0, 1, 2, 3, 4, 5);

        // "ABCDABD"
        int[] lps2 = KmpMatcher.computeLpsTable("ABCDABD");
        assertThat(lps2).containsExactly(0, 0, 0, 0, 1, 2, 0);
    }

    @Test
    @DisplayName("Finds first occurrence of pattern in text without backtracking")
    void shouldFindIndexOf() {
        String text = "Check repository hygiene and security practices in RepoLens";
        assertThat(KmpMatcher.indexOf(text, "hygiene")).isEqualTo(17);
        assertThat(KmpMatcher.indexOf(text, "security")).isEqualTo(29);
        assertThat(KmpMatcher.indexOf(text, "nonexistent")).isEqualTo(-1);
    }

    @Test
    @DisplayName("Case-insensitive matching works as expected")
    void shouldMatchCaseInsensitive() {
        String readme = "# README\n\nTodo: remove hardcoded AWS_SECRET_ACCESS_KEY before commit";
        assertThat(KmpMatcher.containsIgnoreCase(readme, "todo")).isTrue();
        assertThat(KmpMatcher.containsIgnoreCase(readme, "aws_secret_access_key")).isTrue();
        assertThat(KmpMatcher.contains(readme, "TODO")).isFalse(); // case-sensitive is false
    }

    @Test
    @DisplayName("Finds all occurrences of repeating patterns")
    void shouldFindAllOccurrences() {
        String text = "AABAACAADAABAABA";
        String pattern = "AABA";
        List<Integer> occurrences = KmpMatcher.findAllOccurrences(text, pattern);
        assertThat(occurrences).containsExactly(0, 9, 12);
    }
}
