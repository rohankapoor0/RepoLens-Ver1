package com.repolens.web.controller;

import com.repolens.github.exception.GithubUnavailableException;
import com.repolens.github.exception.RateLimitException;
import com.repolens.github.exception.UserNotFoundException;
import com.repolens.web.exception.InvalidUsernameException;
import com.repolens.web.exception.NoPublicReposException;
import com.repolens.web.exception.VisitorThrottleException;
import com.repolens.web.service.AnalysisService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Primary REST API controller for repository hygiene analysis and ranking.
 */
@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/analyze/{username}")
    public ResponseEntity<Map<String, Object>> analyzeUserGet(
            @PathVariable("username") String username,
            HttpServletRequest request
    ) {
        String clientIp = extractClientIp(request);
        return ResponseEntity.ok(analysisService.analyzeUser(username, clientIp));
    }

    @PostMapping("/analyze/{username}")
    public ResponseEntity<Map<String, Object>> analyzeUserPost(
            @PathVariable("username") String username,
            HttpServletRequest request
    ) {
        String clientIp = extractClientIp(request);
        return ResponseEntity.ok(analysisService.analyzeUser(username, clientIp));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }

    @ExceptionHandler(InvalidUsernameException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidUsername(InvalidUsernameException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Invalid Username",
                "message", ex.getMessage(),
                "username", String.valueOf(ex.getUsername())
        ));
    }

    @ExceptionHandler(NoPublicReposException.class)
    public ResponseEntity<Map<String, Object>> handleNoPublicRepos(NoPublicReposException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "No Public Repositories",
                "message", ex.getMessage(),
                "username", ex.getUsername()
        ));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "User Not Found",
                "message", ex.getMessage(),
                "username", ex.getUsername()
        ));
    }

    @ExceptionHandler(VisitorThrottleException.class)
    public ResponseEntity<Map<String, Object>> handleVisitorThrottle(VisitorThrottleException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "error", "Visitor Rate Limit Exceeded",
                "message", ex.getMessage(),
                "limit", ex.getLimit(),
                "retryAfterMinutes", ex.getRetryAfterMinutes()
        ));
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "error", "GitHub Rate Limit Exceeded",
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
