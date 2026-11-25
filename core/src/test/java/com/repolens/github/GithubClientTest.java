package com.repolens.github;

import com.repolens.github.etag.InMemoryETagStorage;
import com.repolens.github.exception.GithubUnavailableException;
import com.repolens.github.exception.RateLimitException;
import com.repolens.github.exception.UserNotFoundException;
import com.repolens.github.model.GithubRepo;
import com.repolens.github.model.GithubTree;
import com.repolens.github.model.RateLimitInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GithubClientTest {

    private String loadFixture(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("Fixture not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed reading fixture: " + path, e);
        }
    }

    @Test
    @DisplayName("listPublicRepos parses repos, skips forks by default, and flags archived repos")
    void shouldListPublicReposAndFilterForks() {
        String reposJson = loadFixture("/fixtures/repos.json");

        MockHttpClient mockHttp = new MockHttpClient((req) -> {
            Map<String, List<String>> headers = Map.of(
                    "x-ratelimit-limit", List.of("60"),
                    "x-ratelimit-remaining", List.of("55"),
                    "x-ratelimit-reset", List.of(String.valueOf(Instant.now().getEpochSecond() + 3600))
            );
            return new MockHttpResponse<>(200, headers, reposJson);
        });

        GithubClient client = new GithubClient(mockHttp, "https://mock.github.api", null, new InMemoryETagStorage());

        // Default: skipForks = true
        List<GithubRepo> repos = client.listPublicRepos("testuser");

        // The fixture contains 3 repos: 1 standard, 1 fork, 1 archived.
        // With skipForks=true, we expect 2 repos: "sample-repo" and "archived-legacy"
        assertThat(repos).hasSize(2);
        assertThat(repos.get(0).name()).isEqualTo("sample-repo");
        assertThat(repos.get(0).fork()).isFalse();
        assertThat(repos.get(0).archived()).isFalse();

        assertThat(repos.get(1).name()).isEqualTo("archived-legacy");
        assertThat(repos.get(1).archived()).isTrue();

        // With skipForks = false, all 3 are returned
        List<GithubRepo> allRepos = client.listPublicRepos("testuser", false);
        assertThat(allRepos).hasSize(3);
        assertThat(allRepos.stream().anyMatch(GithubRepo::fork)).isTrue();
    }

    @Test
    @DisplayName("getRepo correctly parses full details including topics, license, open issues, size")
    void shouldGetRepoDetails() {
        String repoJson = loadFixture("/fixtures/repo.json");

        MockHttpClient mockHttp = new MockHttpClient((req) ->
                new MockHttpResponse<>(200, Map.of(), repoJson)
        );

        GithubClient client = new GithubClient(mockHttp, "https://mock.github.api", null, new InMemoryETagStorage());
        GithubRepo repo = client.getRepo("testuser", "sample-repo");

        assertThat(repo.name()).isEqualTo("sample-repo");
        assertThat(repo.owner()).isEqualTo("testuser");
        assertThat(repo.description()).isEqualTo("Detailed repository for unit testing");
        assertThat(repo.topics()).containsExactly("testing", "clean-code");
        assertThat(repo.license()).isEqualTo("MIT");
        assertThat(repo.language()).isEqualTo("Java");
        assertThat(repo.stargazersCount()).isEqualTo(128);
        assertThat(repo.forksCount()).isEqualTo(14);
        assertThat(repo.openIssuesCount()).isEqualTo(3);
        assertThat(repo.defaultBranch()).isEqualTo("main");
        assertThat(repo.size()).isEqualTo(2048);
        assertThat(repo.archived()).isFalse();
    }

    @Test
    @DisplayName("getTree parses recursive file tree and truncated flag")
    void shouldGetRecursiveGitTree() {
        String treeJson = loadFixture("/fixtures/tree.json");

        MockHttpClient mockHttp = new MockHttpClient((req) ->
                new MockHttpResponse<>(200, Map.of(), treeJson)
        );

        GithubClient client = new GithubClient(mockHttp, "https://mock.github.api", null, new InMemoryETagStorage());
        GithubTree tree = client.getTree("testuser", "sample-repo", "main");

        assertThat(tree.truncated()).isFalse();
        assertThat(tree.entries()).hasSize(5);
        assertThat(tree.entries().stream().map(e -> e.path())).contains(
                ".gitignore", "README.md", ".github/workflows/ci.yml", ".env", "pom.xml"
        );
    }

    @Test
    @DisplayName("getReadme decodes base64 content and returns empty Optional on 404")
    void shouldGetAndDecodeReadme() {
        String readmeJson = loadFixture("/fixtures/readme.json");

        MockHttpClient mockHttp = new MockHttpClient((req) -> {
            if (req.uri().getPath().contains("empty-repo")) {
                return new MockHttpResponse<>(404, Map.of(), "{\"message\": \"Not Found\"}");
            }
            return new MockHttpResponse<>(200, Map.of(), readmeJson);
        });

        GithubClient client = new GithubClient(mockHttp, "https://mock.github.api", null, new InMemoryETagStorage());

        Optional<String> readme = client.getReadme("testuser", "sample-repo");
        assertThat(readme).isPresent();
        assertThat(readme.get()).contains("# RepoLens");
        assertThat(readme.get()).contains("Deterministic repo quality tool");

        // 404 yields Optional.empty()
        Optional<String> emptyReadme = client.getReadme("testuser", "empty-repo");
        assertThat(emptyReadme).isEmpty();
    }

    @Test
    @DisplayName("getRecentCommitDates parses commit timestamps")
    void shouldGetRecentCommitDates() {
        String commitsJson = loadFixture("/fixtures/commits.json");

        MockHttpClient mockHttp = new MockHttpClient((req) ->
                new MockHttpResponse<>(200, Map.of(), commitsJson)
        );

        GithubClient client = new GithubClient(mockHttp, "https://mock.github.api", null, new InMemoryETagStorage());
        List<Instant> commitDates = client.getRecentCommitDates("testuser", "sample-repo");

        assertThat(commitDates).hasSize(2);
        assertThat(commitDates.get(0)).isEqualTo(Instant.parse("2026-06-01T14:00:00Z"));
        assertThat(commitDates.get(1)).isEqualTo(Instant.parse("2026-05-20T10:00:00Z"));
    }

    @Test
    @DisplayName("ETag 304 response serves cached body without re-fetching")
    void shouldHandleETag304CacheHit() {
        String repoJson = loadFixture("/fixtures/repo.json");
        AtomicInteger requestCount = new AtomicInteger(0);
        AtomicReference<String> sentIfNoneMatch = new AtomicReference<>();

        MockHttpClient mockHttp = new MockHttpClient((req) -> {
            requestCount.incrementAndGet();
            req.headers().firstValue("If-None-Match").ifPresent(sentIfNoneMatch::set);

            if (requestCount.get() == 1) {
                // First request: returns 200 with ETag header
                return new MockHttpResponse<>(200, Map.of("ETag", List.of("\"etag-abc-123\"")), repoJson);
            } else {
                // Second request: returns 304 Not Modified
                return new MockHttpResponse<>(304, Map.of("ETag", List.of("\"etag-abc-123\"")), "");
            }
        });

        InMemoryETagStorage etagStorage = new InMemoryETagStorage();
        GithubClient client = new GithubClient(mockHttp, "https://mock.github.api", null, etagStorage);

        // First call: populates cache
        GithubRepo repo1 = client.getRepo("testuser", "sample-repo");
        assertThat(repo1.name()).isEqualTo("sample-repo");

        // Second call: sends ETag, gets 304, returns cached payload
        GithubRepo repo2 = client.getRepo("testuser", "sample-repo");
        assertThat(repo2.name()).isEqualTo("sample-repo");
        assertThat(sentIfNoneMatch.get()).isEqualTo("\"etag-abc-123\"");
        assertThat(requestCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("RateLimitException thrown on HTTP 403/429 with reset time and message")
    void shouldThrowRateLimitExceptionWhenExhausted() {
        long resetEpoch = Instant.now().getEpochSecond() + 1800;
        MockHttpClient mockHttp = new MockHttpClient((req) ->
                new MockHttpResponse<>(403, Map.of(
                        "x-ratelimit-limit", List.of("60"),
                        "x-ratelimit-remaining", List.of("0"),
                        "x-ratelimit-reset", List.of(String.valueOf(resetEpoch))
                ), "{\"message\": \"API rate limit exceeded\"}")
        );

        GithubClient client = new GithubClient(mockHttp, "https://mock.github.api", null, new InMemoryETagStorage());

        assertThatThrownBy(() -> client.listPublicRepos("testuser"))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("rate limit reached")
                .satisfies(ex -> {
                    RateLimitException rle = (RateLimitException) ex;
                    assertThat(rle.getRemainingLimit()).isEqualTo(0);
                    assertThat(rle.getResetTime().getEpochSecond()).isEqualTo(resetEpoch);
                });
    }

    @Test
    @DisplayName("UserNotFoundException thrown on HTTP 404 for missing user")
    void shouldThrowUserNotFoundException() {
        MockHttpClient mockHttp = new MockHttpClient((req) ->
                new MockHttpResponse<>(404, Map.of(), "{\"message\": \"Not Found\"}")
        );

        GithubClient client = new GithubClient(mockHttp, "https://mock.github.api", null, new InMemoryETagStorage());

        assertThatThrownBy(() -> client.listPublicRepos("nonexistent_user_404"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("nonexistent_user_404");
    }

    @Test
    @DisplayName("GithubUnavailableException thrown on HTTP 500 / 503")
    void shouldThrowGithubUnavailableException() {
        MockHttpClient mockHttp = new MockHttpClient((req) ->
                new MockHttpResponse<>(503, Map.of(), "Service Unavailable")
        );

        GithubClient client = new GithubClient(mockHttp, "https://mock.github.api", null, new InMemoryETagStorage());

        assertThatThrownBy(() -> client.getRepo("owner", "repo"))
                .isInstanceOf(GithubUnavailableException.class)
                .satisfies(ex -> assertThat(((GithubUnavailableException) ex).getStatusCode()).isEqualTo(503));
    }

    @Test
    @DisplayName("getUser retrieves user profile for arbitrary public user and supports @username prefix")
    void shouldGetUserProfileAndSupportAtPrefix() {
        String userJson = """
                {
                  "login": "octocat",
                  "id": 583231,
                  "avatar_url": "https://avatars.githubusercontent.com/u/583231?v=4",
                  "html_url": "https://github.com/octocat",
                  "name": "The Octocat",
                  "company": "@github",
                  "blog": "https://github.blog",
                  "location": "San Francisco",
                  "bio": "GitHub mascot",
                  "public_repos": 8,
                  "public_gists": 8,
                  "followers": 15000,
                  "following": 9
                }
                """;

        AtomicReference<String> requestedUri = new AtomicReference<>();
        MockHttpClient mockHttp = new MockHttpClient((req) -> {
            requestedUri.set(req.uri().toString());
            return new MockHttpResponse<>(200, Map.of(), userJson);
        });

        GithubClient client = new GithubClient(mockHttp, "https://mock.github.api", null, new InMemoryETagStorage());

        // Test standard username
        com.repolens.github.model.GithubUser user1 = client.getUser("octocat");
        assertThat(user1.login()).isEqualTo("octocat");
        assertThat(user1.name()).isEqualTo("The Octocat");
        assertThat(user1.publicRepos()).isEqualTo(8);
        assertThat(requestedUri.get()).isEqualTo("https://mock.github.api/users/octocat");

        // Test @username prefix
        com.repolens.github.model.GithubUser user2 = client.getUser("@octocat");
        assertThat(user2.login()).isEqualTo("octocat");
        assertThat(requestedUri.get()).isEqualTo("https://mock.github.api/users/octocat");
    }

    @Test
    @DisplayName("HTTP 409 Conflict on empty repository returns empty tree, empty readme and empty commits")
    void shouldHandleHttp409OnEmptyRepositoryGracefully() {
        String conflictJson = """
                {
                  "message": "Git Repository is empty.",
                  "documentation_url": "https://docs.github.com/rest/git/trees"
                }
                """;

        MockHttpClient mockHttp = new MockHttpClient((req) ->
                new MockHttpResponse<>(409, Map.of(), conflictJson)
        );

        GithubClient client = new GithubClient(mockHttp, "https://mock.github.api", null, new InMemoryETagStorage());

        // Tree returns empty tree instead of throwing
        GithubTree tree = client.getTree("owner", "empty-repo", "main");
        assertThat(tree.entries()).isEmpty();

        // Readme returns empty
        Optional<String> readme = client.getReadme("owner", "empty-repo");
        assertThat(readme).isEmpty();

        // Commits returns empty list
        List<Instant> commits = client.getRecentCommitDates("owner", "empty-repo");
        assertThat(commits).isEmpty();
    }

    @Test
    @DisplayName("Token privacy: token is injected in Authorization header but never exposed in exceptions or toString")
    void shouldKeepTokenStrictlyPrivate() {
        final String secretToken = "ghp_SECRET_TOKEN_DO_NOT_LEAK";
        AtomicBoolean tokenHeaderObserved = new AtomicBoolean(false);

        MockHttpClient mockHttp = new MockHttpClient((req) -> {
            Optional<String> auth = req.headers().firstValue("Authorization");
            if (auth.isPresent() && auth.get().equals("Bearer " + secretToken)) {
                tokenHeaderObserved.set(true);
            }
            return new MockHttpResponse<>(500, Map.of(), "Internal Server Error");
        });

        GithubClient client = new GithubClient(mockHttp, "https://mock.github.api", secretToken, new InMemoryETagStorage());

        assertThatThrownBy(() -> client.listPublicRepos("anyuser"))
                .isInstanceOf(GithubUnavailableException.class)
                .hasMessageNotContaining(secretToken);

        assertThat(tokenHeaderObserved.get()).isTrue();
        assertThat(client.toString()).doesNotContain(secretToken);
    }

    /* ---------------- Mock HTTP Infrastructure ---------------- */

    @FunctionalInterface
    interface MockRequestHandler {
        HttpResponse<String> handle(HttpRequest request);
    }

    static class MockHttpClient extends HttpClient {
        private final MockRequestHandler handler;

        MockHttpClient(MockRequestHandler handler) {
            this.handler = handler;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override
        public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override
        public Redirect followRedirects() { return Redirect.NEVER; }
        @Override
        public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override
        public SSLContext sslContext() { try { return SSLContext.getDefault(); } catch (Exception e) { throw new RuntimeException(e); } }
        @Override
        public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override
        public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override
        public Version version() { return Version.HTTP_2; }
        @Override
        public Optional<Executor> executor() { return Optional.empty(); }

        @SuppressWarnings("unchecked")
        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return (HttpResponse<T>) handler.handle(request);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return CompletableFuture.completedFuture(send(request, responseBodyHandler));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return CompletableFuture.completedFuture(send(request, responseBodyHandler));
        }
    }

    static class MockHttpResponse<T> implements HttpResponse<T> {
        private final int statusCode;
        private final HttpHeaders headers;
        private final T body;

        MockHttpResponse(int statusCode, Map<String, List<String>> headersMap, T body) {
            this.statusCode = statusCode;
            this.headers = HttpHeaders.of(headersMap, (k, v) -> true);
            this.body = body;
        }

        @Override public int statusCode() { return statusCode; }
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return headers; }
        @Override public T body() { return body; }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return URI.create("https://mock.github.api"); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_2; }
    }
}
