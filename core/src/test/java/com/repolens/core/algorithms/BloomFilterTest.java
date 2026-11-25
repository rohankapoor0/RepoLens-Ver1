package com.repolens.core.algorithms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BloomFilterTest {

    @Test
    @DisplayName("Negative guarantee: an un-inserted element returns false (definitely new)")
    void shouldGuaranteeNoFalseNegatives() {
        BloomFilter<String> filter = new BloomFilter<>(100, 0.01);

        filter.add("octocat/Hello-World");
        filter.add("torvalds/linux");
        filter.add("spring-projects/spring-boot");

        // Negative check: items never added must guarantee false
        assertThat(filter.mightContain("unknown/repo-alpha")).isFalse();
        assertThat(filter.mightContain("unknown/repo-beta")).isFalse();
        assertThat(filter.mightContain("unknown/repo-gamma")).isFalse();
    }

    @Test
    @DisplayName("Inserted elements return true for mightContain")
    void shouldFindInsertedElements() {
        BloomFilter<String> filter = new BloomFilter<>(50, 0.01);

        String repo = "google/guava";
        filter.add(repo);

        // Positive check: mightContain returns true
        assertThat(filter.mightContain(repo)).isTrue();
        assertThat(filter.getElementCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Bloom filter parameter calculation produces valid bits and hash functions")
    void shouldCalculateOptimalParameters() {
        BloomFilter<String> filter = new BloomFilter<>(1000, 0.01);

        // For n=1000, p=0.01:
        // m ~ 9585 bits, k ~ 7
        assertThat(filter.getBitSize()).isGreaterThan(9000);
        assertThat(filter.getNumHashFunctions()).isBetween(6, 8);
    }
}
