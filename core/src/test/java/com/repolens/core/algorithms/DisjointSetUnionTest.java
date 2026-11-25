package com.repolens.core.algorithms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DisjointSetUnionTest {

    @Test
    @DisplayName("Union and Find correctly manage connected components")
    void shouldManageComponents() {
        DisjointSetUnion<String> dsu = new DisjointSetUnion<>();

        dsu.makeSet("repo-backend");
        dsu.makeSet("repo-frontend");
        dsu.makeSet("repo-backend-fork");
        dsu.makeSet("repo-docs");

        assertThat(dsu.getComponentCount()).isEqualTo(4);

        // Union duplicates/forks
        dsu.union("repo-backend", "repo-backend-fork");
        assertThat(dsu.connected("repo-backend", "repo-backend-fork")).isTrue();
        assertThat(dsu.connected("repo-backend", "repo-frontend")).isFalse();
        assertThat(dsu.getComponentCount()).isEqualTo(3);
        assertThat(dsu.getComponentSize("repo-backend")).isEqualTo(2);

        // Union more items
        dsu.union("repo-backend-fork", "repo-docs");
        assertThat(dsu.connected("repo-backend", "repo-docs")).isTrue();
        assertThat(dsu.getComponentCount()).isEqualTo(2);
        assertThat(dsu.getComponentSize("repo-backend")).isEqualTo(3);
    }

    @Test
    @DisplayName("getComponents groups elements into connected duplicate clusters")
    void shouldGroupComponents() {
        DisjointSetUnion<String> dsu = new DisjointSetUnion<>();
        dsu.union("alpha", "alpha-v2");
        dsu.union("alpha-v2", "alpha-legacy");

        dsu.union("beta", "beta-clone");

        dsu.makeSet("gamma-standalone");

        Map<String, List<String>> components = dsu.getComponents();
        assertThat(components).hasSize(3);

        // One cluster with 3 items, one with 2, one with 1
        List<Integer> sizes = components.values().stream().map(List::size).sorted().toList();
        assertThat(sizes).containsExactly(1, 2, 3);
    }
}
