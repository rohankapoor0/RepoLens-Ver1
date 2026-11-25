package com.repolens.web.controller;

import com.repolens.core.cache.BloomFilterRepoScoreCache;
import com.repolens.core.cache.CachedRepoScoreRecord;
import com.repolens.github.GithubClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BadgeController.class)
class BadgeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GithubClient githubClient;

    @MockBean
    private BloomFilterRepoScoreCache repoCache;

    @Test
    @DisplayName("GET /badge/{owner}/{repo}.svg returns dynamic SVG badge with correct mime-type")
    void shouldReturnSvgBadge() throws Exception {
        String scoreJson = """
                {"totalScore": 85, "categoryScores": {}, "deductions": [], "note": "Clean"}
                """;
        CachedRepoScoreRecord record = new CachedRepoScoreRecord(
                "octocat/hello-world@LATEST", "octocat", "hello-world", "LATEST",
                "{}", scoreJson, Instant.now()
        );
        given(repoCache.get(anyString(), anyString(), anyString())).willReturn(Optional.of(record));

        mockMvc.perform(get("/badge/octocat/hello-world.svg"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/svg+xml"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<svg")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("85/100")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("#4c1"))); // Green color for 85
    }
}
