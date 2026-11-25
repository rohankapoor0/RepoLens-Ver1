package com.repolens.github;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.repolens.github.etag.CachedResponse;
import com.repolens.github.etag.ETagStorage;
import com.repolens.github.etag.InMemoryETagStorage;
import com.repolens.github.exception.EmptyRepositoryException;
import com.repolens.github.exception.GithubUnavailableException;
import com.repolens.github.exception.RateLimitException;
import com.repolens.github.exception.UserNotFoundException;
import com.repolens.github.model.GithubRepo;
import com.repolens.github.model.GithubTree;
import com.repolens.github.model.GithubTreeEntry;
import com.repolens.github.model.GithubUser;
import com.repolens.github.model.RateLimitInfo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Official GitHub REST API Client implemented with {@link java.net.http.HttpClient} and Jackson.
 * <p>
 * Key Features:
 * <ul>
 *   <li>ETag caching: sends {@code If-None-Match} and handles HTTP 304 as local cache hit.</li>
 *   <li>Rate limit tracking: parses {@code x-ratelimit-*} headers on every response.</li>
 *   <li>Strict Token Privacy: reads optional token from {@code GITHUB_TOKEN}. Never prints, logs, or returns it.</li>
 *   <li>Public data only: read-only operations.</li>
 * </ul>
 */
public class GithubClient {

    public static final String DEFAULT_BASE_URL = "https://api.github.com";
    private static final String API_VERSION = "2022-11-28";
    private static final String USER_AGENT = "RepoLens-Quality-Dashboard/1.0";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String token; // Optional. NEVER logged, printed, or returned.
    private final ETagStorage etagStorage;
    private final AtomicReference<RateLimitInfo> lastRateLimitInfo;

    public GithubClient() {
        this(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                DEFAULT_BASE_URL,
                System.getenv("GITHUB_TOKEN"),
                new InMemoryETagStorage()
        );
    }

    public GithubClient(HttpClient httpClient, String baseUrl, String token, ETagStorage etagStorage) {
        this.httpClient = httpClient != null ? httpClient : HttpClient.newHttpClient();
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : DEFAULT_BASE_URL;
        this.token = (token != null && !token.isBlank()) ? token.trim() : null;
        this.etagStorage = etagStorage != null ? etagStorage : new InMemoryETagStorage();
        this.lastRateLimitInfo = new AtomicReference<>(RateLimitInfo.UNKNOWN);

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Retrieves the public profile for an arbitrary GitHub user.
     * Endpoint: GET /users/{username}
     *
     * @param username target GitHub username or @username
     * @return GitHub user profile
     * @throws UserNotFoundException if user does not exist
     */
    public GithubUser getUser(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
        String cleanUser = username.trim().startsWith("@") ? username.trim().substring(1).trim() : username.trim();
        String url = baseUrl + "/users/" + cleanUser;
        String jsonBody = executeRequest(url, cleanUser);
        try {
            JsonNode node = objectMapper.readTree(jsonBody);
            return new GithubUser(
                    node.path("login").asText(cleanUser),
                    node.path("id").asLong(0),
                    node.path("avatar_url").asText(""),
                    node.path("html_url").asText(""),
                    node.path("name").asText(node.path("login").asText(cleanUser)),
                    node.path("company").asText(""),
                    node.path("blog").asText(""),
                    node.path("location").asText(""),
                    node.path("bio").asText(""),
                    node.path("public_repos").asInt(0),
                    node.path("public_gists").asInt(0),
                    node.path("followers").asInt(0),
                    node.path("following").asInt(0)
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse user profile JSON from GitHub", e);
        }
    }

    /**
     * Lists public repositories for the given GitHub user, paginated in batches of 100.
     * Skips forks by default.
     *
     * @param username target GitHub username
     * @return list of repositories
     */
    public List<GithubRepo> listPublicRepos(String username) {
        return listPublicRepos(username, true);
    }

    /**
     * Lists public repositories for the given GitHub user, paginated in batches of 100.
     *
     * @param username  target GitHub username
     * @param skipForks whether to filter out repository forks
     * @return list of repositories
     */
    public List<GithubRepo> listPublicRepos(String username, boolean skipForks) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }

        String cleanUser = username.trim().startsWith("@") ? username.trim().substring(1).trim() : username.trim();
        List<GithubRepo> allRepos = new ArrayList<>();
        int page = 1;
        final int perPage = 100;

        while (true) {
            String url = baseUrl + "/users/" + cleanUser + "/repos?type=public&per_page=" + perPage + "&page=" + page;
            String jsonBody = executeRequest(url, cleanUser);

            try {
                JsonNode root = objectMapper.readTree(jsonBody);
                if (!root.isArray() || root.isEmpty()) {
                    break;
                }

                for (JsonNode repoNode : root) {
                    boolean isFork = repoNode.path("fork").asBoolean(false);
                    if (skipForks && isFork) {
                        continue;
                    }
                    allRepos.add(parseRepo(repoNode));
                }

                if (root.size() < perPage) {
                    break; // Reached last page
                }
                page++;
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse repository list JSON from GitHub", e);
            }
        }

        return allRepos;
    }

    /**
     * Retrieves detailed metadata for a single repository.
     *
     * @param owner repository owner
     * @param repo  repository name
     * @return repository metadata
     */
    public GithubRepo getRepo(String owner, String repo) {
        String url = baseUrl + "/repos/" + owner + "/" + repo;
        String jsonBody = executeRequest(url, owner);
        try {
            JsonNode node = objectMapper.readTree(jsonBody);
            return parseRepo(node);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse repository details JSON", e);
        }
    }

    /**
     * Retrieves the recursive Git tree for a repository default branch.
     *
     * @param owner  repository owner
     * @param repo   repository name
     * @param branch branch name or tree SHA
     * @return tree metadata and path entries
     */
    public GithubTree getTree(String owner, String repo, String branch) {
        String targetBranch = (branch != null && !branch.isBlank()) ? branch : "main";
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/git/trees/" + targetBranch + "?recursive=1";

        try {
            String jsonBody = executeRequest(url, owner);
            JsonNode root = objectMapper.readTree(jsonBody);
            String sha = root.path("sha").asText("");
            boolean truncated = root.path("truncated").asBoolean(false);
            List<GithubTreeEntry> entries = new ArrayList<>();

            JsonNode treeArray = root.path("tree");
            if (treeArray.isArray()) {
                for (JsonNode entry : treeArray) {
                    entries.add(new GithubTreeEntry(
                            entry.path("path").asText(""),
                            entry.path("mode").asText(""),
                            entry.path("type").asText(""),
                            entry.hasNonNull("size") ? entry.path("size").asLong() : null,
                            entry.path("sha").asText("")
                    ));
                }
            }
            return new GithubTree(sha, truncated, entries);
        } catch (EmptyRepositoryException | UserNotFoundException e) {
            // Empty / bare repository with no commits/tree
            return new GithubTree("", false, Collections.emptyList());
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse git tree JSON", e);
        }
    }

    /**
     * Retrieves and decodes the README text for a repository.
     *
     * @param owner repository owner
     * @param repo  repository name
     * @return decoded README markdown string, or empty if no README exists
     */
    public Optional<String> getReadme(String owner, String repo) {
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/readme";
        try {
            String jsonBody = executeRequest(url, owner);
            JsonNode root = objectMapper.readTree(jsonBody);
            String content = root.path("content").asText("");
            if (content.isBlank()) {
                return Optional.empty();
            }
            // GitHub wraps base64 content with newlines
            String cleanBase64 = content.replaceAll("\\s+", "");
            byte[] decodedBytes = Base64.getDecoder().decode(cleanBase64);
            return Optional.of(new String(decodedBytes, StandardCharsets.UTF_8));
        } catch (EmptyRepositoryException | UserNotFoundException e) {
            // GitHub returns 404/409 if repository does not have a README or is empty
            return Optional.empty();
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse README JSON", e);
        }
    }

    /**
     * Retrieves commit timestamps for roughly the last 30 commits of a repository.
     *
     * @param owner repository owner
     * @param repo  repository name
     * @return list of commit Instants (newest first)
     */
    public List<Instant> getRecentCommitDates(String owner, String repo) {
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/commits?per_page=30";
        try {
            String jsonBody = executeRequest(url, owner);
            JsonNode root = objectMapper.readTree(jsonBody);
            if (!root.isArray()) {
                return Collections.emptyList();
            }

            List<Instant> commitDates = new ArrayList<>();
            for (JsonNode commitNode : root) {
                JsonNode commitDetails = commitNode.path("commit");
                String dateStr = commitDetails.path("committer").path("date").asText("");
                if (dateStr.isBlank()) {
                    dateStr = commitDetails.path("author").path("date").asText("");
                }
                if (!dateStr.isBlank()) {
                    try {
                        commitDates.add(Instant.parse(dateStr));
                    } catch (Exception ignored) {
                        // skip malformed date strings
                    }
                }
            }
            return commitDates;
        } catch (EmptyRepositoryException | UserNotFoundException e) {
            // Empty / bare repository with no commits returns 409 or 404
            return Collections.emptyList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse commit dates JSON", e);
        }
    }

    /**
     * Returns the latest rate-limit info snapshot observed from GitHub API response headers.
     */
    public RateLimitInfo getLastRateLimitInfo() {
        return lastRateLimitInfo.get();
    }

    /* ---------------- Internal Request Execution & ETag Handling ---------------- */

    private String executeRequest(String url, String contextUser) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", API_VERSION)
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(15))
                .GET();

        // Safe token injection (never printed or returned)
        if (token != null) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }

        // ETag conditional check
        Optional<CachedResponse> cachedOpt = etagStorage.get(url);
        cachedOpt.ifPresent(cached -> requestBuilder.header("If-None-Match", cached.etag()));

        HttpResponse<String> response;
        try {
            response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new GithubUnavailableException(503, "Network error communicating with GitHub: " + e.getMessage());
        }

        // Parse rate-limit response headers
        RateLimitInfo rateLimit = extractRateLimit(response);
        lastRateLimitInfo.set(rateLimit);

        int status = response.statusCode();

        // 304 Not Modified: Cache hit
        if (status == 304) {
            if (cachedOpt.isPresent()) {
                return cachedOpt.get().body();
            }
            throw new GithubUnavailableException(500, "Received 304 Not Modified but cached body was missing for: " + url);
        }

        // 200 OK: Cache response with ETag if present
        if (status == 200) {
            String body = response.body();
            Optional<String> etagHeader = response.headers().firstValue("ETag");
            etagHeader.ifPresent(etag -> etagStorage.put(url, etag, body, rateLimit));
            return body;
        }

        // 404 Not Found
        if (status == 404) {
            throw new UserNotFoundException(contextUser);
        }

        // 409 Conflict: Empty repository with no git objects/commits
        if (status == 409) {
            throw new EmptyRepositoryException(contextUser, "Git Repository is empty (HTTP 409 Conflict).");
        }

        // 403 / 429 Rate Limit
        if (status == 403 || status == 429) {
            String body = response.body();
            boolean isRateLimit = rateLimit.isExhausted() ||
                    (body != null && (body.contains("API rate limit exceeded") || body.contains("secondary rate limit")));

            if (isRateLimit) {
                throw new RateLimitException(
                        rateLimit.resetTime(),
                        rateLimit.remaining(),
                        "GitHub API rate limit reached. Limit resets at " + rateLimit.resetTime() +
                                " (in " + rateLimit.secondsUntilReset() + " seconds). Configure GITHUB_TOKEN to raise limits."
                );
            }
            throw new GithubUnavailableException(status, "GitHub access forbidden (HTTP " + status + "). " + body);
        }

        // 5xx Server Errors
        if (status >= 500) {
            throw new GithubUnavailableException(status, "GitHub server returned error status HTTP " + status);
        }

        throw new GithubUnavailableException(status, "Unexpected response from GitHub: HTTP " + status);
    }

    private RateLimitInfo extractRateLimit(HttpResponse<?> response) {
        int limit = response.headers().firstValue("x-ratelimit-limit")
                .map(Integer::parseInt)
                .orElse(lastRateLimitInfo.get().limit());

        int remaining = response.headers().firstValue("x-ratelimit-remaining")
                .map(Integer::parseInt)
                .orElse(lastRateLimitInfo.get().remaining());

        Instant resetTime = response.headers().firstValue("x-ratelimit-reset")
                .map(Long::parseLong)
                .map(Instant::ofEpochSecond)
                .orElse(lastRateLimitInfo.get().resetTime());

        int used = response.headers().firstValue("x-ratelimit-used")
                .map(Integer::parseInt)
                .orElse(0);

        String resource = response.headers().firstValue("x-ratelimit-resource")
                .orElse("core");

        return new RateLimitInfo(limit, remaining, resetTime, used, resource);
    }

    private GithubRepo parseRepo(JsonNode node) {
        String name = node.path("name").asText("");
        String fullName = node.path("full_name").asText("");
        String owner = node.path("owner").path("login").asText("");
        String description = node.hasNonNull("description") ? node.path("description").asText("") : "";

        List<String> topics = new ArrayList<>();
        JsonNode topicsNode = node.path("topics");
        if (topicsNode.isArray()) {
            for (JsonNode t : topicsNode) {
                topics.add(t.asText());
            }
        }

        String license = null;
        JsonNode licenseNode = node.path("license");
        if (!licenseNode.isMissingNode() && !licenseNode.isNull()) {
            license = licenseNode.path("spdx_id").asText(licenseNode.path("name").asText(""));
            if ("NOASSERTION".equalsIgnoreCase(license)) {
                license = licenseNode.path("name").asText("Custom");
            }
        }

        String language = node.hasNonNull("language") ? node.path("language").asText("") : "";
        int stars = node.path("stargazers_count").asInt(0);
        int forks = node.path("forks_count").asInt(0);
        int openIssues = node.path("open_issues_count").asInt(0);

        Instant pushedAt = parseInstant(node.path("pushed_at").asText(null));
        Instant createdAt = parseInstant(node.path("created_at").asText(null));
        Instant updatedAt = parseInstant(node.path("updated_at").asText(null));

        String defaultBranch = node.path("default_branch").asText("main");
        int size = node.path("size").asInt(0);
        boolean archived = node.path("archived").asBoolean(false);
        boolean fork = node.path("fork").asBoolean(false);

        return new GithubRepo(
                name, fullName, owner, description, topics, license, language,
                stars, forks, openIssues, pushedAt, createdAt, updatedAt,
                defaultBranch, size, archived, fork
        );
    }

    private Instant parseInstant(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception e) {
            return null;
        }
    }
}
