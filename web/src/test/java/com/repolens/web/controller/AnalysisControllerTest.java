package com.repolens.web.controller;

import com.repolens.github.exception.UserNotFoundException;
import com.repolens.web.exception.InvalidUsernameException;
import com.repolens.web.exception.VisitorThrottleException;
import com.repolens.web.service.AnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisController.class)
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalysisService analysisService;

    @Test
    @DisplayName("GET /api/analyze/{username} returns 200 with analysis results")
    void shouldReturnAnalysisResultsGet() throws Exception {
        Map<String, Object> mockResult = Map.of(
                "username", "octocat",
                "totalPublicRepos", 2,
                "reposAnalyzed", 2,
                "medianScore", 85.0,
                "rankedRepos", List.of(
                        Map.of("repo", "repo-1", "score", 90),
                        Map.of("repo", "repo-2", "score", 80)
                ),
                "staleRepos", List.of(),
                "fixPlan", Map.of(
                        "headline", "1 fix improves 2 of your 2 repos",
                        "distinctReposImproved", 2,
                        "totalPointsRecoverable", 10,
                        "orderedFixes", List.of(
                                Map.of("checkKey", "ADD_LICENSE", "title", "Add Open Source LICENSE", "effortCost", 1.0)
                        )
                ),
                "duplicates", Map.of(
                        "duplicateGroupsCount", 0,
                        "duplicateReposCount", 0,
                        "groups", List.of()
                )
        );

        given(analysisService.analyzeUser(anyString(), anyString())).willReturn(mockResult);

        mockMvc.perform(get("/api/analyze/octocat").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("octocat"))
                .andExpect(jsonPath("$.reposAnalyzed").value(2))
                .andExpect(jsonPath("$.rankedRepos").isArray())
                .andExpect(jsonPath("$.medianScore").value(85.0))
                .andExpect(jsonPath("$.fixPlan.headline").value("1 fix improves 2 of your 2 repos"))
                .andExpect(jsonPath("$.duplicates.duplicateGroupsCount").value(0));
    }

    @Test
    @DisplayName("POST /api/analyze/{username} also works and returns 200")
    void shouldReturnAnalysisResultsPost() throws Exception {
        Map<String, Object> mockResult = Map.of(
                "username", "octocat",
                "totalPublicRepos", 1,
                "reposAnalyzed", 1
        );

        given(analysisService.analyzeUser(anyString(), anyString())).willReturn(mockResult);

        mockMvc.perform(post("/api/analyze/octocat").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("octocat"));
    }

    @Test
    @DisplayName("GET /api/analyze/{username} with invalid username returns 400")
    void shouldReturn400OnInvalidUsername() throws Exception {
        given(analysisService.analyzeUser(anyString(), anyString()))
                .willThrow(new InvalidUsernameException("-invalid-", "Cannot start with a hyphen"));

        mockMvc.perform(get("/api/analyze/-invalid-").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Username"))
                .andExpect(jsonPath("$.username").value("-invalid-"));
    }

    @Test
    @DisplayName("GET /api/analyze/{username} returns 404 for unknown user")
    void shouldReturn404OnUnknownUser() throws Exception {
        given(analysisService.analyzeUser(anyString(), anyString()))
                .willThrow(new UserNotFoundException("nonexistent-user-xyz"));

        mockMvc.perform(get("/api/analyze/nonexistent-user-xyz").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User Not Found"));
    }

    @Test
    @DisplayName("GET /api/analyze/{username} returns 429 when visitor is throttled")
    void shouldReturn429OnVisitorThrottling() throws Exception {
        given(analysisService.analyzeUser(anyString(), anyString()))
                .willThrow(new VisitorThrottleException(10, 60, "Rate limit exceeded. You may analyze at most 10 distinct usernames per hour."));

        mockMvc.perform(get("/api/analyze/octocat").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Visitor Rate Limit Exceeded"))
                .andExpect(jsonPath("$.limit").value(10));
    }
}
