package com.repolens.core.duplicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateFinderTest {

    private final DuplicateFinder duplicateFinder = new DuplicateFinder();

    @Test
    @DisplayName("Groups repositories sharing identical README content hash")
    void shouldGroupByIdenticalReadmeHash() {
        String readme = "# Microservice Application\n\nThis application processes transactions and records audit logs across regions.";

        RepoProfile repo1 = new RepoProfile("transactor-v1", "Payment service", List.of("src/App.java"), readme, "Java");
        RepoProfile repo2 = new RepoProfile("payment-backend", "Another service", List.of("pkg/main.go"), readme, "Go");
        RepoProfile repo3 = new RepoProfile("unrelated-docs", "Documentation", List.of("docs/index.md"), "# Docs\nDifferent text here.", "Markdown");

        DuplicateReport report = duplicateFinder.findDuplicates(List.of(repo1, repo2, repo3));

        assertThat(report.duplicateGroupsCount()).isEqualTo(1);
        assertThat(report.duplicateReposCount()).isEqualTo(2);

        DuplicateGroup group = report.groups().get(0);
        assertThat(group.repos()).containsExactly("payment-backend", "transactor-v1");
        assertThat(group.primaryReason()).contains("Identical README hash");
    }

    @Test
    @DisplayName("Groups repositories with high file-tree overlap (Jaccard >= 70%)")
    void shouldGroupByFileTreeOverlap() {
        List<String> treeA = List.of(
                "pom.xml", "README.md", "src/main/java/App.java", "src/test/java/AppTest.java", ".gitignore"
        );
        List<String> treeB = List.of(
                "pom.xml", "README.md", "src/main/java/App.java", "src/test/java/AppTest.java", "LICENSE"
        );
        // Intersection = 4 (pom, readme, App.java, AppTest.java). Union = 6 (pom, readme, App.java, AppTest.java, .gitignore, LICENSE).
        // Jaccard = 4 / 6 = 0.666... Wait! Let's ensure Jaccard >= 0.70:
        // If intersection = 5, union = 6 -> Jaccard = 5/6 = 0.833!
        List<String> treeHighOverlap = List.of(
                "pom.xml", "README.md", "src/main/java/App.java", "src/test/java/AppTest.java", ".gitignore", "build.sh"
        );
        List<String> treeTarget = List.of(
                "pom.xml", "README.md", "src/main/java/App.java", "src/test/java/AppTest.java", ".gitignore", "run.sh"
        );
        // Intersection = 5, union = 7 -> 5/7 = 0.714 >= 0.70!

        RepoProfile repoA = new RepoProfile("project-alpha", "Desc A", treeHighOverlap, "# Alpha readme", "Java");
        RepoProfile repoB = new RepoProfile("project-fork", "Desc B", treeTarget, "# Different readme", "Java");

        DuplicateReport report = duplicateFinder.findDuplicates(List.of(repoA, repoB));

        assertThat(report.duplicateGroupsCount()).isEqualTo(1);
        assertThat(report.groups().get(0).primaryReason()).contains("High file-tree overlap");
        assertThat(report.groups().get(0).repos()).containsExactly("project-alpha", "project-fork");
    }

    @Test
    @DisplayName("Groups repositories with similar names and shared language")
    void shouldGroupBySimilarNames() {
        RepoProfile repoA = new RepoProfile("shopAI", "E-commerce platform", List.of("index.js"), "", "JavaScript");
        RepoProfile repoB = new RepoProfile("shop-ai", "E-commerce platform", List.of("main.js"), "", "JavaScript");

        DuplicateReport report = duplicateFinder.findDuplicates(List.of(repoA, repoB));

        assertThat(report.duplicateGroupsCount()).isEqualTo(1);
        assertThat(report.groups().get(0).primaryReason()).contains("Similar repository names");
    }

    @Test
    @DisplayName("Transitive union: DisjointSetUnion merges multi-way similarities into a single connected component")
    void shouldMergeTransitivelyUsingDSU() {
        String sharedReadme = "# Analytics Engine\n\nHigh-throughput streaming processing for real-time telemetry.";
        List<String> sharedFiles = List.of("pom.xml", "src/A.java", "src/B.java", "src/C.java", "src/D.java");

        // Repo 1 and Repo 2 share README hash
        RepoProfile r1 = new RepoProfile("engine-v1", "Engine v1", List.of("file1.txt"), sharedReadme, "Java");
        RepoProfile r2 = new RepoProfile("engine-core", "Engine core", sharedFiles, sharedReadme, "Java");

        // Repo 2 and Repo 3 share file tree
        RepoProfile r3 = new RepoProfile("engine-runner", "Engine runner", sharedFiles, "# Other text", "Java");

        DuplicateReport report = duplicateFinder.findDuplicates(List.of(r1, r2, r3));

        // All 3 should be merged into 1 single group via DSU
        assertThat(report.duplicateGroupsCount()).isEqualTo(1);
        assertThat(report.duplicateReposCount()).isEqualTo(3);
        assertThat(report.groups().get(0).repos()).containsExactly("engine-core", "engine-runner", "engine-v1");
    }

    @Test
    @DisplayName("Completely distinct repositories produce zero duplicate groups")
    void shouldReturnEmptyWhenNoDuplicates() {
        RepoProfile r1 = new RepoProfile("ios-music-player", "Swift audio player", List.of("Music/Player.swift"), "Swift audio", "Swift");
        RepoProfile r2 = new RepoProfile("db-crawler", "Go SQL crawler", List.of("crawler/main.go"), "Go crawler", "Go");

        DuplicateReport report = duplicateFinder.findDuplicates(List.of(r1, r2));

        assertThat(report.duplicateGroupsCount()).isEqualTo(0);
        assertThat(report.duplicateReposCount()).isEqualTo(0);
        assertThat(report.groups()).isEmpty();
    }
}
