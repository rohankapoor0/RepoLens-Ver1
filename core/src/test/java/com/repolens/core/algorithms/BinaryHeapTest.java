package com.repolens.core.algorithms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BinaryHeapTest {

    @Test
    @DisplayName("Min-heap extracts elements in ascending natural order")
    void shouldExtractInAscendingOrder() {
        BinaryHeap<Integer> minHeap = new BinaryHeap<>();
        minHeap.insert(42);
        minHeap.insert(15);
        minHeap.insert(88);
        minHeap.insert(3);
        minHeap.insert(27);

        assertThat(minHeap.size()).isEqualTo(5);
        assertThat(minHeap.peek()).isEqualTo(3);

        assertThat(minHeap.extractTop()).isEqualTo(3);
        assertThat(minHeap.extractTop()).isEqualTo(15);
        assertThat(minHeap.extractTop()).isEqualTo(27);
        assertThat(minHeap.extractTop()).isEqualTo(42);
        assertThat(minHeap.extractTop()).isEqualTo(88);
        assertThat(minHeap.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Max-heap extracts elements in descending order with custom comparator")
    void shouldWorkAsMaxHeap() {
        BinaryHeap<Integer> maxHeap = new BinaryHeap<>(Comparator.reverseOrder());
        maxHeap.insert(10);
        maxHeap.insert(50);
        maxHeap.insert(30);

        assertThat(maxHeap.peek()).isEqualTo(50);
        assertThat(maxHeap.extractTop()).isEqualTo(50);
        assertThat(maxHeap.extractTop()).isEqualTo(30);
        assertThat(maxHeap.extractTop()).isEqualTo(10);
    }

    @Test
    @DisplayName("Bulk construction via buildHeap runs in linear O(n) time")
    void shouldBuildHeapFromList() {
        List<Integer> list = Arrays.asList(9, 4, 7, 1, -2, 6, 5);
        BinaryHeap<Integer> heap = new BinaryHeap<>(list, Comparator.naturalOrder());

        assertThat(heap.size()).isEqualTo(7);
        assertThat(heap.toSortedList()).containsExactly(-2, 1, 4, 5, 6, 7, 9);
    }

    @Test
    @DisplayName("Top-K extraction selects the highest-scoring items efficiently")
    void shouldComputeTopK() {
        List<Integer> scores = Arrays.asList(45, 80, 20, 95, 60, 100, 15, 75);
        // We want top 3 highest scores
        List<Integer> top3 = BinaryHeap.topK(scores, 3, Comparator.naturalOrder());

        assertThat(top3).containsExactly(100, 95, 80);
    }

    @Test
    @DisplayName("Throws exception on empty extract or peek")
    void shouldThrowOnEmpty() {
        BinaryHeap<String> empty = new BinaryHeap<>();
        assertThatThrownBy(empty::peek).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(empty::extractTop).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> empty.insert(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
