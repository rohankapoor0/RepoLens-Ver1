package com.repolens.web.controller;

import com.repolens.core.cache.CacheMetrics;
import com.repolens.github.GithubClient;
import com.repolens.github.model.RateLimitInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatusController.class)
class StatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GithubClient githubClient;

    @MockBean
    private CacheMetrics cacheMetrics;

    @Test
    @DisplayName("GET /api/status returns 200 with rate limit and cache stats")
    void shouldReturnStatus() throws Exception {
        RateLimitInfo rateLimit = new RateLimitInfo(5000, 4980, Instant.now().plusSeconds(3600), 20, "core");
        given(githubClient.getLastRateLimitInfo()).willReturn(rateLimit);

        given(cacheMetrics.getSavedApiRequests()).willReturn(15L);
        given(cacheMetrics.getCacheHits()).willReturn(10L);
        given(cacheMetrics.getCacheMisses()).willReturn(5L);
        given(cacheMetrics.getBloomChecks()).willReturn(15L);
        given(cacheMetrics.getBloomConfirmedHits()).willReturn(10L);
        given(cacheMetrics.getBloomNegativeDefiniteMisses()).willReturn(5L);
        given(cacheMetrics.getBloomFalsePositives()).willReturn(0L);

        mockMvc.perform(get("/api/status").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("RepoLens"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.rateLimit.limit").value(5000))
                .andExpect(jsonPath("$.rateLimit.remaining").value(4980))
                .andExpect(jsonPath("$.cacheMetrics.savedApiRequests").value(15));
    }
}
