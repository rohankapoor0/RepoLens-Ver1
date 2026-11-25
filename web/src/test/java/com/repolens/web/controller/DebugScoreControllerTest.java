package com.repolens.web.controller;

import com.repolens.github.GithubClient;
import com.repolens.github.model.GithubRepo;
import com.repolens.github.model.GithubTree;
import com.repolens.github.model.GithubTreeEntry;
import com.repolens.github.model.RateLimitInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DebugScoreController.class)
class DebugScoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GithubClient githubClient;

    @MockBean
    private com.repolens.core.cache.BloomFilterRepoScoreCache bloomFilterRepoScoreCache;

    @Test
    @DisplayName("GET /api/debug/score/{owner}/{repo} computes and returns deterministic RepoScore with summary and breakdowns")
    void shouldReturnDebugScore() throws Exception {
        com.repolens.core.cache.CacheMetrics metrics = new com.repolens.core.cache.CacheMetrics();
        given(bloomFilterRepoScoreCache.getMetrics()).willReturn(metrics);
        given(bloomFilterRepoScoreCache.get(anyString(), anyString(), anyString())).willReturn(Optional.empty());
        GithubRepo repo = new GithubRepo(
                "sample-app", "testuser/sample-app", "testuser",
                "A clean sample application",
                List.of("java"), "MIT", "Java",
                10, 2, 0,
                Instant.now(), Instant.now().minusSeconds(100000), Instant.now(),
                "main", 1024, false, false
        );

        GithubTree tree = new GithubTree("sha123", false, List.of(
                new GithubTreeEntry("README.md", "100644", "blob", 1200L, "sha1"),
                new GithubTreeEntry("LICENSE", "100644", "blob", 1100L, "sha2"),
                new GithubTreeEntry(".gitignore", "100644", "blob", 200L, "sha3"),
                new GithubTreeEntry("pom.xml", "100644", "blob", 3000L, "sha4"),
                new GithubTreeEntry("src/test/java/AppTest.java", "100644", "blob", 1500L, "sha5")
        ));

        String readme = "# Sample App\n\n![Demo](demo.png)\n\n## Overview\nDocumentation here.\n\n## Installation\nBuild it.\n\n## Usage\nRun it.\n";

        given(githubClient.getRepo(eq("testuser"), eq("sample-app"))).willReturn(repo);
        given(githubClient.getTree(eq("testuser"), eq("sample-app"), anyString())).willReturn(tree);
        given(githubClient.getReadme(eq("testuser"), eq("sample-app"))).willReturn(Optional.of(readme));
        given(githubClient.getRecentCommitDates(eq("testuser"), eq("sample-app"))).willReturn(List.of(Instant.now()));
        given(githubClient.getLastRateLimitInfo()).willReturn(RateLimitInfo.UNKNOWN);

        mockMvc.perform(get("/api/debug/score/testuser/sample-app").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repository").value("testuser/sample-app"))
                .andExpect(jsonPath("$.totalScore").isNumber())
                .andExpect(jsonPath("$.categories.DOCUMENTATION").exists())
                .andExpect(jsonPath("$.categories.SAFETY").exists())
                .andExpect(jsonPath("$.deductions").isArray())
                .andExpect(jsonPath("$.summary").exists())
                .andExpect(jsonPath("$.summary.score").isNumber())
                .andExpect(jsonPath("$.summary.gradeBand").isString())
                .andExpect(jsonPath("$.summary.explanation").isString())
                .andExpect(jsonPath("$.categoryBreakdowns.DOCUMENTATION").exists());
    }
}
