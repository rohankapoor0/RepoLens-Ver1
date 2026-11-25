package com.repolens.core.algorithms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuickselectTest {

    @Test
    @DisplayName("Finds the k-th smallest element in unordered array in linear O(n) average time")
    void shouldSelectKthElement() {
        int[] scores = {85, 30, 95, 60, 45, 100, 10, 75};
        // Sorted: [10, 30, 45, 60, 75, 85, 95, 100]

        assertThat(Quickselect.select(scores.clone(), 0)).isEqualTo(10); // Minimum
        assertThat(Quickselect.select(scores.clone(), 1)).isEqualTo(30);
        assertThat(Quickselect.select(scores.clone(), 3)).isEqualTo(60);
        assertThat(Quickselect.select(scores.clone(), 7)).isEqualTo(100); // Maximum
    }

    @Test
    @DisplayName("Calculates median accurately for odd and even size arrays")
    void shouldCalculateMedian() {
        int[] odd = {10, 50, 20, 40, 30}; // sorted: 10, 20, 30, 40, 50 -> median = 30
        assertThat(Quickselect.median(odd)).isEqualTo(30.0);

        int[] even = {10, 20, 30, 40}; // median = (20 + 30)/2 = 25.0
        assertThat(Quickselect.median(even)).isEqualTo(25.0);
    }

    @Test
    @DisplayName("Computes percentiles for hygiene brackets")
    void shouldComputePercentiles() {
        int[] scores = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
        // 25th percentile ~ index 2 (value 30)
        assertThat(Quickselect.percentile(scores, 25.0)).isEqualTo(30.0);
        // 75th percentile ~ index 7 (value 80)
        assertThat(Quickselect.percentile(scores, 75.0)).isEqualTo(80.0);
    }
}
