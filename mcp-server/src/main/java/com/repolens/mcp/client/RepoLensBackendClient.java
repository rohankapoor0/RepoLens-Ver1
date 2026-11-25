package com.repolens.mcp.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP Client connecting the RepoLens MCP Server to the RepoLens backend REST API.
 * <p>
 * Reuses existing backend business logic and endpoints without reimplementing algorithms or scoring.
 * Gracefully handles connection failures, timeouts, rate limits, and missing resources.
 */
public class RepoLensBackendClient {

    public static final String DEFAULT_BASE_URL = "http://localhost:8080";
    public static final String BASE_URL_ENV = "REPOLENS_API_URL";

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RepoLensBackendClient() {
        this(resolveBaseUrl(), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), new ObjectMapper());
    }

    public RepoLensBackendClient(String baseUrl, HttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.replaceAll("/+$", "") : DEFAULT_BASE_URL;
        this.httpClient = httpClient != null ? httpClient : HttpClient.newHttpClient();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public static String resolveBaseUrl() {
        String env = System.getenv(BASE_URL_ENV);
        if (env == null || env.isBlank()) {
            env = System.getenv("REPOLENS_BACKEND_URL");
        }
        return (env != null && !env.isBlank()) ? env.trim() : DEFAULT_BASE_URL;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Calls GET /api/analyze/{username} to analyze a user profile.
     */
    public BackendResponse analyzeUser(String username) {
        String cleanUser = sanitizeUsername(username);
        String url = baseUrl + "/api/analyze/" + cleanUser;
        return executeGet(url, Duration.ofSeconds(60));
    }

    /**
     * Calls GET /api/repo/{owner}/{repo} to score and evaluate a single repository.
     */
    public BackendResponse getRepoDetail(String owner, String repo) {
        String cleanOwner = sanitizeUsername(owner);
        String cleanRepo = (repo != null) ? repo.trim() : "";
        String url = baseUrl + "/api/repo/" + cleanOwner + "/" + cleanRepo;
        return executeGet(url, Duration.ofSeconds(30));
    }

    /**
     * Calls GET /api/status to fetch live service and quota status.
     */
    public BackendResponse getStatus() {
        String url = baseUrl + "/api/status";
        return executeGet(url, Duration.ofSeconds(10));
    }

    private BackendResponse executeGet(String url, Duration timeout) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent", "RepoLens-MCP-Client/1.0")
                .timeout(timeout)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode >= 200 && statusCode < 300) {
                Map<String, Object> data = objectMapper.readValue(responseBody, new TypeReference<>() {});
                return new BackendResponse(true, statusCode, data, null);
            }

            // Error response from backend (e.g. 400, 404, 429, 500, 503)
            Map<String, Object> errorDetails = new LinkedHashMap<>();
            try {
                errorDetails = objectMapper.readValue(responseBody, new TypeReference<>() {});
            } catch (Exception ignored) {
                errorDetails.put("rawResponse", responseBody);
            }

            String errorMessage = extractErrorMessage(statusCode, errorDetails);
            return new BackendResponse(false, statusCode, errorDetails, errorMessage);

        } catch (ConnectException | HttpConnectTimeoutException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "BackendConnectionFailure");
            err.put("targetUrl", url);
            err.put("baseUrl", baseUrl);
            String message = "Could not connect to RepoLens backend at " + baseUrl +
                    ". Please ensure the RepoLens web application is running (e.g. `java -jar web/target/web-1.0.0-SNAPSHOT.jar`).";
            return new BackendResponse(false, 503, err, message);

        } catch (IOException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "IOFailure");
            err.put("details", e.getMessage());
            return new BackendResponse(false, 500, err, "I/O communication error with RepoLens backend: " + e.getMessage());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "Interrupted");
            return new BackendResponse(false, 500, err, "Request interrupted while awaiting backend response.");
        }
    }

    private String extractErrorMessage(int statusCode, Map<String, Object> errorDetails) {
        if (errorDetails != null) {
            if (errorDetails.containsKey("message")) {
                return String.valueOf(errorDetails.get("message"));
            }
            if (errorDetails.containsKey("error")) {
                return String.valueOf(errorDetails.get("error"));
            }
        }
        return switch (statusCode) {
            case 400 -> "Bad Request: Invalid parameters provided to RepoLens backend.";
            case 404 -> "Resource not found on RepoLens backend or GitHub.";
            case 429 -> "GitHub API rate limit exceeded.";
            case 503 -> "GitHub API service is currently unavailable.";
            default -> "RepoLens backend returned HTTP status " + statusCode;
        };
    }

    private String sanitizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return "";
        }
        String clean = username.trim();
        return clean.startsWith("@") ? clean.substring(1).trim() : clean;
    }

    /**
     * Encapsulates the response from the RepoLens backend.
     */
    public record BackendResponse(
            boolean isSuccess,
            int statusCode,
            Map<String, Object> data,
            String errorMessage
    ) {
    }
}
