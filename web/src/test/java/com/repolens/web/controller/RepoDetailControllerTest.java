package com.repolens.web.controller;

import com.repolens.core.cache.BloomFilterRepoScoreCache;
import com.repolens.github.GithubClient;
import com.repolens.github.model.GithubRepo;
import com.repolens.github.model.GithubTree;
import com.repolens.github.model.GithubTreeEntry;
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

@WebMvcTest(RepoDetailController.class)
class RepoDetailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GithubClient githubClient;

    @MockBean
    private BloomFilterRepoScoreCache repoCache;

    @Test
    @DisplayName("GET /api/repo/{owner}/{repo} returns full score breakdown and deductions")
    void shouldReturnRepoDetailBreakdown() throws Exception {
        given(repoCache.get(anyString(), anyString(), anyString())).willReturn(Optional.empty());

        GithubRepo repo = new GithubRepo(
                "my-project", "octocat/my-project", "octocat",
                "A neat project",
                List.of("java"), "MIT", "Java",
                50, 10, 2,
                Instant.now(), Instant.now().minusSeconds(50000), Instant.now(),
                "main", 2048, false, false
        );

        GithubTree tree = new GithubTree("sha999", false, List.of(
                new GithubTreeEntry("README.md", "100644", "blob", 1500L, "s1"),
                new GithubTreeEntry("LICENSE", "100644", "blob", 1000L, "s2"),
                new GithubTreeEntry(".gitignore", "100644", "blob", 200L, "s3"),
                new GithubTreeEntry("pom.xml", "100644", "blob", 4000L, "s4"),
                new GithubTreeEntry("src/test/java/AppTest.java", "100644", "blob", 1200L, "s5")
        ));

        String readme = "# My Project\n\n![Arch](arch.png)\n\n## Overview\nHello.\n\n## Installation\nmvn install\n\n## Usage\nRun it.\n";

        given(githubClient.getRepo(eq("octocat"), eq("my-project"))).willReturn(repo);
        given(githubClient.getTree(eq("octocat"), eq("my-project"), anyString())).willReturn(tree);
        given(githubClient.getReadme(eq("octocat"), eq("my-project"))).willReturn(Optional.of(readme));
        given(githubClient.getRecentCommitDates(eq("octocat"), eq("my-project"))).willReturn(List.of(Instant.now()));

        mockMvc.perform(get("/api/repo/octocat/my-project").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repository").value("octocat/my-project"))
                .andExpect(jsonPath("$.owner").value("octocat"))
                .andExpect(jsonPath("$.repo").value("my-project"))
                .andExpect(jsonPath("$.cached").value(false))
                .andExpect(jsonPath("$.totalScore").isNumber())
                .andExpect(jsonPath("$.categories.DOCUMENTATION").exists())
                .andExpect(jsonPath("$.categories.ENGINEERING_HYGIENE").exists())
                .andExpect(jsonPath("$.categories.SAFETY").exists())
                .andExpect(jsonPath("$.deductions").isArray())
                .andExpect(jsonPath("$.snapshot.filesScanned").value(5));
    }
}
