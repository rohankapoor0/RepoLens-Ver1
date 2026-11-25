package com.repolens.web.controller;

import com.repolens.github.GithubClient;
import com.repolens.github.model.GithubRepo;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DebugRepoController.class)
class DebugRepoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GithubClient githubClient;

    @Test
    @DisplayName("GET /api/debug/repos/{username} returns repos list and rate limit info without token")
    void shouldReturnDebugReposAndRateLimit() throws Exception {
        GithubRepo sample = new GithubRepo(
                "my-repo", "testuser/my-repo", "testuser", "A test repository",
                List.of("java"), "MIT", "Java", 10, 2, 1,
                Instant.parse("2026-06-01T12:00:00Z"),
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2026-06-01T12:00:00Z"),
                "main", 1024, false, false
        );

        RateLimitInfo rateLimit = new RateLimitInfo(60, 58, Instant.now().plusSeconds(3600), 2, "core");

        given(githubClient.listPublicRepos(eq("testuser"))).willReturn(List.of(sample));
        given(githubClient.getLastRateLimitInfo()).willReturn(rateLimit);

        mockMvc.perform(get("/api/debug/repos/testuser").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.repoCount").value(1))
                .andExpect(jsonPath("$.rateLimit.limit").value(60))
                .andExpect(jsonPath("$.rateLimit.remaining").value(58))
                .andExpect(jsonPath("$.repos[0].name").value("my-repo"))
                .andExpect(jsonPath("$.repos[0].license").value("MIT"))
                // Ensure no token field exists in the response
                .andExpect(jsonPath("$.token").doesNotExist());
    }
}
