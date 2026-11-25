package com.repolens.web.controller;

import com.repolens.github.GithubClient;
import com.repolens.github.exception.GithubUnavailableException;
import com.repolens.github.exception.RateLimitException;
import com.repolens.github.exception.UserNotFoundException;
import com.repolens.github.model.GithubRepo;
import com.repolens.github.model.RateLimitInfo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Debug endpoint allowing real data verification against GitHub REST API.
 */
@RestController
@RequestMapping("/api/debug")
public class DebugRepoController {

    private final GithubClient githubClient;

    public DebugRepoController(GithubClient githubClient) {
        this.githubClient = githubClient;
    }

    @GetMapping("/repos/{username}")
    public ResponseEntity<Map<String, Object>> getDebugRepos(@PathVariable("username") String username) {
        List<GithubRepo> repos = githubClient.listPublicRepos(username);
        RateLimitInfo rateLimit = githubClient.getLastRateLimitInfo();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("username", username);
        response.put("repoCount", repos.size());

        Map<String, Object> rateLimitMap = new LinkedHashMap<>();
        rateLimitMap.put("limit", rateLimit.limit());
        rateLimitMap.put("remaining", rateLimit.remaining());
        rateLimitMap.put("resetTime", rateLimit.resetTime() != null ? rateLimit.resetTime().toString() : null);
        rateLimitMap.put("secondsUntilReset", rateLimit.secondsUntilReset());
        rateLimitMap.put("resource", rateLimit.resource());
        response.put("rateLimit", rateLimitMap);

        response.put("repos", repos);

        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "User Not Found",
                "message", ex.getMessage(),
                "username", ex.getUsername()
        ));
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "error", "Rate Limit Exceeded",
                "message", ex.getMessage(),
                "resetTime", ex.getResetTime().toString(),
                "remaining", ex.getRemainingLimit()
        ));
    }

    @ExceptionHandler(GithubUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleGithubUnavailable(GithubUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "GitHub Unavailable",
                "message", ex.getMessage(),
                "statusCode", ex.getStatusCode()
        ));
    }
}
