package com.repolens.core.algorithms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CountingSortTest {

    record DummyRepo(String name, int score) {}

    @Test
    @DisplayName("Sorts hygiene scores in range [0, 100] in linear O(n) time")
    void shouldSortScoresInLinearTime() {
        int[] scores = {75, 20, 100, 0, 45, 90, 75, 30, 85};
        int[] sorted = CountingSort.sort(scores);

        assertThat(sorted).containsExactly(0, 20, 30, 45, 75, 75, 85, 90, 100);
    }

    @Test
    @DisplayName("Sorts scores in descending order (best to worst)")
    void shouldSortDescending() {
        int[] scores = {50, 95, 10, 80, 100};
        int[] desc = CountingSort.sortDescending(scores);

        assertThat(desc).containsExactly(100, 95, 80, 50, 10);
    }

    @Test
    @DisplayName("Maintains stability when sorting domain objects by score")
    void shouldSortDomainObjectsStably() {
        List<DummyRepo> repos = Arrays.asList(
                new DummyRepo("repo-A", 80),
                new DummyRepo("repo-B", 60),
                new DummyRepo("repo-C", 80),
                new DummyRepo("repo-D", 100)
        );

        List<DummyRepo> sorted = CountingSort.sortByScore(repos, DummyRepo::score, false); // descending

        assertThat(sorted.get(0).name()).isEqualTo("repo-D"); // 100
        // Stable: repo-A must precede repo-C because both have 80 and repo-A appeared first
        assertThat(sorted.get(1).name()).isEqualTo("repo-A"); // 80
        assertThat(sorted.get(2).name()).isEqualTo("repo-C"); // 80
        assertThat(sorted.get(3).name()).isEqualTo("repo-B"); // 60
    }
}
