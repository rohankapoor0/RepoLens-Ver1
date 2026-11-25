package com.repolens.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.repolens.mcp.client.RepoLensBackendClient;
import com.repolens.mcp.client.RepoLensBackendClient.BackendResponse;
import io.modelcontextprotocol.spec.McpSchema;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Handlers for all seven RepoLens Model Context Protocol (MCP) tools.
 * <p>
 * Implements:
 * 1. analyze_profile(username)
 * 2. score_repo(owner, repository)
 * 3. compare_repos(repositories)
 * 4. fix_plan(username)
 * 5. explain_deduction(owner, repository, category)
 * 6. find_duplicates(username)
 * 7. get_status()
 * <p>
 * Strictly delegates to the RepoLens backend REST API and core models without reimplementing
 * algorithms or scoring rules. Stdout is reserved exclusively for JSON-RPC; diagnostic
 * logs go to System.err. Tokens are never exposed.
 */
public class RepoLensToolHandlers {

    public static final String SERVER_NAME = "repolens-mcp";
    public static final String SERVER_VERSION = "1.0.0";

    private final RepoLensBackendClient backendClient;
    private final ObjectMapper objectMapper;

    public RepoLensToolHandlers(RepoLensBackendClient backendClient, ObjectMapper objectMapper) {
        this.backendClient = backendClient != null ? backendClient : new RepoLensBackendClient();
        this.objectMapper = objectMapper != null ? objectMapper : createDefaultObjectMapper();
    }

    public static ObjectMapper createDefaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    /**
     * 1. analyze_profile(username)
     * Maps to GET /api/analyze/{username}
     */
    public McpSchema.CallToolResult handleAnalyzeProfile(Map<String, Object> arguments) {
        String username = extractString(arguments, "username");
        if (username.isBlank()) {
            return createErrorResult("IllegalArgument", "The 'username' parameter is required and cannot be blank.");
        }

        BackendResponse response = backendClient.analyzeUser(username);
        if (!response.isSuccess()) {
            return createBackendErrorResult("analyze_profile", response);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", "analyze_profile");
        result.put("username", username);
        result.put("status", "SUCCESS");
        result.put("analysis", response.data());

        return createSuccessResult(result);
    }

    /**
     * 2. score_repo(owner, repository)
     * Maps to GET /api/repo/{owner}/{repository}
     */
    public McpSchema.CallToolResult handleScoreRepo(Map<String, Object> arguments) {
        String owner = extractString(arguments, "owner");
        String repository = extractString(arguments, "repository");
        if (repository.isBlank()) {
            repository = extractString(arguments, "repo");
        }

        // Support passing "owner/repo" in repository parameter if owner is omitted
        if (owner.isBlank() && repository.contains("/")) {
            String[] parts = repository.split("/", 2);
            owner = parts[0];
            repository = parts[1];
        }

        if (owner.isBlank() || repository.isBlank()) {
            return createErrorResult("IllegalArgument", "Both 'owner' and 'repository' parameters are required.");
        }

        BackendResponse response = backendClient.getRepoDetail(owner, repository);
        if (!response.isSuccess()) {
            return createBackendErrorResult("score_repo", response);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", "score_repo");
        result.put("owner", owner);
        result.put("repository", repository);
        result.put("status", "SUCCESS");
        result.put("scoreReport", response.data());

        return createSuccessResult(result);
    }

    /**
     * 3. compare_repos(repositories)
     * Reuses GET /api/repo/{owner}/{repo} to compare multiple repositories side-by-side.
     */
    @SuppressWarnings("unchecked")
    public McpSchema.CallToolResult handleCompareRepos(Map<String, Object> arguments) {
        List<String> repoList = new ArrayList<>();
        Object reposObj = arguments.get("repositories");

        if (reposObj instanceof List<?>) {
            for (Object item : (List<?>) reposObj) {
                if (item != null && !item.toString().isBlank()) {
                    repoList.add(item.toString().trim());
                }
            }
        } else if (reposObj instanceof String) {
            String[] split = ((String) reposObj).split(",");
            for (String s : split) {
                if (!s.trim().isBlank()) {
                    repoList.add(s.trim());
                }
            }
        }

        if (repoList.size() < 2) {
            return createErrorResult("IllegalArgument",
                    "The 'repositories' parameter must contain at least 2 repositories in 'owner/repo' format.");
        }

        List<Map<String, Object>> evaluated = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();

        for (String repoIdentifier : repoList) {
            String[] parts = repoIdentifier.split("/", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                errors.add(Map.of("repository", repoIdentifier, "error", "Invalid format, expected 'owner/repo'"));
                continue;
            }

            String owner = parts[0].trim();
            String repo = parts[1].trim();
            BackendResponse resp = backendClient.getRepoDetail(owner, repo);

            if (resp.isSuccess() && resp.data() != null) {
                Map<String, Object> data = resp.data();
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("repository", data.getOrDefault("repository", repoIdentifier));
                summary.put("owner", owner);
                summary.put("repo", repo);
                summary.put("score", data.getOrDefault("totalScore", 0));
                summary.put("deductionsCount", data.getOrDefault("deductionsCount", 0));
                summary.put("categories", data.get("categories"));
                summary.put("intelligence", data.get("intelligence"));
                summary.put("summary", data.get("summary"));
                evaluated.add(summary);
            } else {
                errors.add(Map.of(
                        "repository", repoIdentifier,
                        "statusCode", resp.statusCode(),
                        "error", resp.errorMessage() != null ? resp.errorMessage() : "Failed to score repository"
                ));
            }
        }

        if (evaluated.isEmpty()) {
            Map<String, Object> failResult = new LinkedHashMap<>();
            failResult.put("error", "ComparisonFailed");
            failResult.put("message", "None of the specified repositories could be evaluated.");
            failResult.put("errors", errors);
            return createErrorResult("ComparisonFailed", failResult);
        }

        // Rank repositories by total score descending
        evaluated.sort((a, b) -> {
            int scoreA = a.get("score") instanceof Number ? ((Number) a.get("score")).intValue() : 0;
            int scoreB = b.get("score") instanceof Number ? ((Number) b.get("score")).intValue() : 0;
            return Integer.compare(scoreB, scoreA);
        });

        int rank = 1;
        for (Map<String, Object> r : evaluated) {
            r.put("rank", rank++);
        }

        Map<String, Object> comparisonResult = new LinkedHashMap<>();
        comparisonResult.put("tool", "compare_repos");
        comparisonResult.put("repositoriesRequested", repoList.size());
        comparisonResult.put("repositoriesEvaluated", evaluated.size());

        Map<String, Object> highlights = new LinkedHashMap<>();
        highlights.put("topRepository", evaluated.get(0).get("repository"));
        highlights.put("topScore", evaluated.get(0).get("score"));
        highlights.put("lowestRepository", evaluated.get(evaluated.size() - 1).get("repository"));
        highlights.put("lowestScore", evaluated.get(evaluated.size() - 1).get("score"));

        double avgScore = evaluated.stream()
                .mapToInt(e -> e.get("score") instanceof Number ? ((Number) e.get("score")).intValue() : 0)
                .average().orElse(0.0);
        highlights.put("averageScore", Math.round(avgScore * 10.0) / 10.0);

        comparisonResult.put("highlights", highlights);
        comparisonResult.put("rankings", evaluated);

        if (!errors.isEmpty()) {
            comparisonResult.put("failedRepositories", errors);
        }

        return createSuccessResult(comparisonResult);
    }

    /**
     * 4. fix_plan(username)
     * Maps to GET /api/analyze/{username} -> extracts fixPlan
     */
    public McpSchema.CallToolResult handleFixPlan(Map<String, Object> arguments) {
        String username = extractString(arguments, "username");
        if (username.isBlank()) {
            return createErrorResult("IllegalArgument", "The 'username' parameter is required.");
        }

        BackendResponse response = backendClient.analyzeUser(username);
        if (!response.isSuccess()) {
            return createBackendErrorResult("fix_plan", response);
        }

        Map<String, Object> data = response.data();
        Object fixPlanObj = data != null ? data.get("fixPlan") : null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", "fix_plan");
        result.put("username", username);

        if (fixPlanObj != null) {
            result.put("status", "SUCCESS");
            result.put("fixPlan", fixPlanObj);
            result.put("algorithm", "Greedy Weighted Set Cover (Chvátal approximation factor)");
        } else {
            result.put("status", "NO_PLAN_AVAILABLE");
            result.put("message", "No fix plan deficiencies detected for " + username + ".");
        }

        return createSuccessResult(result);
    }

    /**
     * 5. explain_deduction(owner, repository, category)
     * Maps to GET /api/repo/{owner}/{repository} -> filters deductions by category
     */
    @SuppressWarnings("unchecked")
    public McpSchema.CallToolResult handleExplainDeduction(Map<String, Object> arguments) {
        String owner = extractString(arguments, "owner");
        String repository = extractString(arguments, "repository");
        if (repository.isBlank()) {
            repository = extractString(arguments, "repo");
        }
        String categoryInput = extractString(arguments, "category");

        if (owner.isBlank() || repository.isBlank() || categoryInput.isBlank()) {
            return createErrorResult("IllegalArgument",
                    "Parameters 'owner', 'repository', and 'category' are all required.");
        }

        BackendResponse response = backendClient.getRepoDetail(owner, repository);
        if (!response.isSuccess()) {
            return createBackendErrorResult("explain_deduction", response);
        }

        Map<String, Object> data = response.data();
        String normalizedCategory = normalizeCategory(categoryInput);

        // Find category scores and max points
        Map<String, Object> categoriesMap = (data.get("categories") instanceof Map<?, ?>)
                ? (Map<String, Object>) data.get("categories")
                : Collections.emptyMap();

        Map<String, Object> targetCategoryInfo = null;
        for (Map.Entry<String, Object> entry : categoriesMap.entrySet()) {
            if (matchesCategory(entry.getKey(), normalizedCategory)) {
                if (entry.getValue() instanceof Map<?, ?>) {
                    targetCategoryInfo = (Map<String, Object>) entry.getValue();
                }
                break;
            }
        }

        // Filter deductions
        List<Map<String, Object>> deductions = (data.get("deductions") instanceof List<?>)
                ? (List<Map<String, Object>>) data.get("deductions")
                : Collections.emptyList();

        List<Map<String, Object>> matchedDeductions = new ArrayList<>();
        int totalPointsDeducted = 0;

        for (Map<String, Object> d : deductions) {
            String cat = String.valueOf(d.getOrDefault("category", ""));
            if (matchesCategory(cat, normalizedCategory)) {
                matchedDeductions.add(d);
                if (d.get("pointsOff") instanceof Number) {
                    totalPointsDeducted += ((Number) d.get("pointsOff")).intValue();
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", "explain_deduction");
        result.put("repository", owner + "/" + repository);
        result.put("category", normalizedCategory);

        if (targetCategoryInfo != null) {
            result.put("categoryScore", targetCategoryInfo.get("score"));
            result.put("maxPoints", targetCategoryInfo.get("maxPoints"));
            result.put("categoryDisplayName", targetCategoryInfo.get("categoryName"));
        }

        result.put("deductionsCount", matchedDeductions.size());
        result.put("totalPointsOff", totalPointsDeducted);
        result.put("deductions", matchedDeductions);

        if (matchedDeductions.isEmpty()) {
            result.put("explanation", "No deductions found for category '" + normalizedCategory +
                    "'. Full points awarded!");
        } else {
            result.put("explanation", matchedDeductions.size() + " deduction(s) found in category '" +
                    normalizedCategory + "' totaling -" + totalPointsDeducted + " points.");
        }

        return createSuccessResult(result);
    }

    /**
     * 6. find_duplicates(username)
     * Maps to GET /api/analyze/{username} -> extracts duplicates
     */
    public McpSchema.CallToolResult handleFindDuplicates(Map<String, Object> arguments) {
        String username = extractString(arguments, "username");
        if (username.isBlank()) {
            return createErrorResult("IllegalArgument", "The 'username' parameter is required.");
        }

        BackendResponse response = backendClient.analyzeUser(username);
        if (!response.isSuccess()) {
            return createBackendErrorResult("find_duplicates", response);
        }

        Map<String, Object> data = response.data();
        Object duplicatesObj = data != null ? data.get("duplicates") : null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", "find_duplicates");
        result.put("username", username);

        if (duplicatesObj != null) {
            result.put("status", "SUCCESS");
            result.put("duplicatesReport", duplicatesObj);
            result.put("algorithm", "Disjoint Set Union (DSU with path compression and union-by-rank)");
        } else {
            result.put("status", "NO_DUPLICATES_DETECTED");
            result.put("message", "No duplicate or clone clusters detected for " + username + ".");
        }

        return createSuccessResult(result);
    }

    /**
     * 7. get_status()
     * Preserves existing implementation: checks token detection boolean, cache directory,
     * server version, and current rate limit info.
     */
    public McpSchema.CallToolResult handleGetStatus(Path cacheDir) {
        Map<String, Object> statusMap = new LinkedHashMap<>();
        statusMap.put("service", "RepoLens MCP Server");
        statusMap.put("status", "UP");
        statusMap.put("server_version", SERVER_VERSION);

        String tokenEnv = System.getenv("GITHUB_TOKEN");
        boolean tokenDetected = tokenEnv != null && !tokenEnv.isBlank();
        statusMap.put("github_token_detected", tokenDetected);

        statusMap.put("cache_path", cacheDir != null ? cacheDir.toString() : "unknown");

        // Backend health check
        BackendResponse backendStatus = backendClient.getStatus();
        if (backendStatus.isSuccess() && backendStatus.data() != null) {
            statusMap.put("backend", Map.of(
                    "status", "CONNECTED",
                    "url", backendClient.getBaseUrl()
            ));
            Object rateLimitObj = backendStatus.data().get("rateLimit");
            if (rateLimitObj != null) {
                statusMap.put("rate_limit", rateLimitObj);
            }
        } else {
            statusMap.put("backend", Map.of(
                    "status", "DISCONNECTED",
                    "url", backendClient.getBaseUrl(),
                    "note", "RepoLens backend web server not reachable at " + backendClient.getBaseUrl()
            ));
            // Fallback directly to GitHub rate limit check if backend is offline
            Map<String, Object> directRateLimit = fetchDirectGitHubRateLimit(tokenEnv);
            statusMap.put("rate_limit", directRateLimit != null ? directRateLimit : "unavailable");
        }

        return createSuccessResult(statusMap);
    }

    private static String extractString(Map<String, Object> arguments, String key) {
        if (arguments == null || !arguments.containsKey(key) || arguments.get(key) == null) {
            return "";
        }
        return arguments.get(key).toString().trim();
    }

    private static String normalizeCategory(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String clean = input.trim().toUpperCase()
                .replaceAll("[\\s&-]+", "_");
        return switch (clean) {
            case "SAFETY", "SAFETY_SECRET_EXPOSURE", "SECURITY" -> "SAFETY";
            case "DOCUMENTATION", "DOCS", "README", "DOCUMENTATION_README_QUALITY" -> "DOCUMENTATION";
            case "ENGINEERING_HYGIENE", "HYGIENE", "CI", "CICD", "ENGINEERING" -> "ENGINEERING_HYGIENE";
            case "STRUCTURE", "MODULARITY", "STRUCTURE_MODULARITY" -> "STRUCTURE";
            case "LICENSING_COMMUNITY", "LICENSING", "LICENSE", "COMMUNITY" -> "LICENSING_COMMUNITY";
            case "MAINTENANCE", "ACTIVITY", "RECENCY", "MAINTENANCE_ACTIVITY_RECENCY" -> "MAINTENANCE";
            default -> clean;
        };
    }

    private static boolean matchesCategory(String categoryKey, String normalizedTarget) {
        if (categoryKey == null || normalizedTarget == null) return false;
        String normalizedKey = normalizeCategory(categoryKey);
        return normalizedKey.equalsIgnoreCase(normalizedTarget) ||
                categoryKey.equalsIgnoreCase(normalizedTarget);
    }

    private McpSchema.CallToolResult createSuccessResult(Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(json)), false);
        } catch (Exception e) {
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("{\"error\":\"SerializationError\",\"message\":\"" + e.getMessage() + "\"}")),
                    true
            );
        }
    }

    private McpSchema.CallToolResult createErrorResult(String errorType, Object details) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", errorType);
        err.put("details", details);
        try {
            String json = objectMapper.writeValueAsString(err);
            return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(json)), true);
        } catch (Exception e) {
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("{\"error\":\"" + errorType + "\"}")),
                    true
            );
        }
    }

    private McpSchema.CallToolResult createBackendErrorResult(String toolName, BackendResponse response) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("tool", toolName);
        err.put("statusCode", response.statusCode());
        err.put("errorMessage", response.errorMessage());
        if (response.data() != null && !response.data().isEmpty()) {
            err.put("backendResponse", response.data());
        }
        return createErrorResult("BackendError", err);
    }

    private Map<String, Object> fetchDirectGitHubRateLimit(String tokenEnv) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/rate_limit"))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "RepoLens-MCP-Server/" + SERVER_VERSION)
                    .timeout(Duration.ofSeconds(2))
                    .GET();

            if (tokenEnv != null && !tokenEnv.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + tokenEnv.trim());
            }

            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 || response.statusCode() == 304 || response.statusCode() == 403) {
                Map<String, Object> rateLimit = new LinkedHashMap<>();
                response.headers().firstValue("x-ratelimit-limit").map(Integer::parseInt).ifPresent(v -> rateLimit.put("limit", v));
                response.headers().firstValue("x-ratelimit-remaining").map(Integer::parseInt).ifPresent(v -> rateLimit.put("remaining", v));
                response.headers().firstValue("x-ratelimit-used").map(Integer::parseInt).ifPresent(v -> rateLimit.put("used", v));
                response.headers().firstValue("x-ratelimit-reset").map(Long::parseLong).ifPresent(v -> {
                    rateLimit.put("resetTime", Instant.ofEpochSecond(v).toString());
                    long secondsUntil = Math.max(0, v - Instant.now().getEpochSecond());
                    rateLimit.put("secondsUntilReset", secondsUntil);
                });
                response.headers().firstValue("x-ratelimit-resource").ifPresent(v -> rateLimit.put("resource", v));
                if (!rateLimit.isEmpty()) {
                    return rateLimit;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
