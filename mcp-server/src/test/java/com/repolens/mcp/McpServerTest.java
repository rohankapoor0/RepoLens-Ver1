package com.repolens.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repolens.mcp.cache.McpCacheConfig;
import com.repolens.mcp.client.RepoLensBackendClient;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerTest {

    private InputStream originalSystemIn;
    private PrintStream originalSystemOut;

    private PipedOutputStream testInWriter;
    private ByteArrayOutputStream capturedStdout;
    private McpSyncServer server;
    private HttpServer mockHttpServer;

    @BeforeEach
    void setUp() throws Exception {
        originalSystemIn = System.in;
        originalSystemOut = System.out;

        testInWriter = new PipedOutputStream();
        PipedInputStream serverIn = new PipedInputStream(testInWriter);
        System.setIn(serverIn);

        capturedStdout = new ByteArrayOutputStream();
        PrintStream testOut = new PrintStream(capturedStdout, true, StandardCharsets.UTF_8);
        System.setOut(testOut);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            try {
                server.closeGracefully();
            } catch (Exception ignored) {
            }
        }
        if (mockHttpServer != null) {
            mockHttpServer.stop(0);
        }

        System.setIn(originalSystemIn);
        System.setOut(originalSystemOut);

        try {
            testInWriter.close();
        } catch (Exception ignored) {
        }
    }

    @Test
    void testAllSevenToolsRegisteredAndDiscoverable(@TempDir Path tempDir) throws Exception {
        server = McpServerApplication.createServer(tempDir);
        ObjectMapper mapper = new ObjectMapper();

        // 1. Initialize
        sendRequest("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test-client\",\"version\":\"1.0.0\"}}}\n");
        String initResponse = waitForResponseLine(1, 5, TimeUnit.SECONDS);
        assertThat(initResponse).isNotNull();
        JsonNode initNode = mapper.readTree(initResponse);
        assertThat(initNode.path("result").path("serverInfo").path("name").asText()).isEqualTo("repolens-mcp");

        // 2. Initialized notification
        sendRequest("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n");

        // 3. tools/list
        sendRequest("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}\n");
        String listResponse = waitForResponseLine(2, 5, TimeUnit.SECONDS);
        assertThat(listResponse).isNotNull();
        JsonNode listNode = mapper.readTree(listResponse);

        JsonNode tools = listNode.path("result").path("tools");
        assertThat(tools.isArray()).isTrue();
        assertThat(tools.size()).isEqualTo(7);

        Set<String> toolNames = new HashSet<>();
        for (JsonNode t : tools) {
            toolNames.add(t.path("name").asText());
        }

        assertThat(toolNames).containsExactlyInAnyOrder(
                "analyze_profile",
                "score_repo",
                "compare_repos",
                "fix_plan",
                "explain_deduction",
                "find_duplicates",
                "get_status"
        );

        // 4. Test get_status tool call
        sendRequest("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"get_status\",\"arguments\":{}}}\n");
        String statusResponse = waitForResponseLine(3, 5, TimeUnit.SECONDS);
        assertThat(statusResponse).isNotNull();
        JsonNode statusCall = mapper.readTree(statusResponse);
        assertThat(statusCall.path("result").path("isError").asBoolean()).isFalse();

        JsonNode statusPayload = mapper.readTree(statusCall.path("result").path("content").get(0).path("text").asText());
        assertThat(statusPayload.path("service").asText()).isEqualTo("RepoLens MCP Server");
        assertThat(statusPayload.path("server_version").asText()).isEqualTo("1.0.0");
        assertThat(statusPayload.path("github_token_detected").isBoolean()).isTrue();
        assertThat(statusPayload.has("cache_path")).isTrue();
        assertThat(statusPayload.has("rate_limit")).isTrue();

        // 5. Test validation error handling on tools with missing arguments
        sendRequest("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"analyze_profile\",\"arguments\":{}}}\n");
        String errResponse = waitForResponseLine(4, 5, TimeUnit.SECONDS);
        assertThat(errResponse).isNotNull();
        JsonNode errCall = mapper.readTree(errResponse);
        assertThat(errCall.path("result").path("isError").asBoolean()).isTrue();

        // 6. Assert stdout purity: every captured line must be valid JSON-RPC 2.0
        assertStdoutPurity(mapper);
    }

    @Test
    void testAllToolsWithMockBackend(@TempDir Path tempDir) throws Exception {
        // Start local lightweight HTTP server mocking the RepoLens web endpoints
        mockHttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int mockPort = mockHttpServer.getAddress().getPort();

        // Mock /api/analyze/testuser
        mockHttpServer.createContext("/api/analyze/testuser", exchange -> {
            String json = """
                    {
                      "username": "testuser",
                      "analyzedAt": "2026-11-25T12:00:00Z",
                      "stats": { "averageScore": 82.5, "totalPublicRepos": 3 },
                      "rankedRepos": [
                        { "name": "repo-alpha", "score": 90 },
                        { "name": "repo-beta", "score": 75 }
                      ],
                      "fixPlan": {
                        "totalEstimatedGain": 12,
                        "coveredDeductionsCount": 3,
                        "items": [
                          { "ruleId": "DOCS_SHORT_README", "action": "Expand README", "gain": 5 }
                        ]
                      },
                      "duplicates": {
                        "totalDuplicatePairs": 0,
                        "duplicateRatio": 0.0,
                        "duplicateGroups": []
                      }
                    }
                    """;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        // Mock /api/repo/testowner/repo-alpha
        mockHttpServer.createContext("/api/repo/testowner/repo-alpha", exchange -> {
            String json = """
                    {
                      "repository": "testowner/repo-alpha",
                      "owner": "testowner",
                      "repo": "repo-alpha",
                      "totalScore": 90,
                      "categories": {
                        "SAFETY": { "score": 30, "maxPoints": 30, "categoryName": "Safety & Secret Exposure" },
                        "DOCUMENTATION": { "score": 15, "maxPoints": 20, "categoryName": "Documentation & Readme Quality" }
                      },
                      "deductionsCount": 1,
                      "deductions": [
                        {
                          "category": "DOCUMENTATION",
                          "ruleId": "DOCS_SHORT_README",
                          "pointsOff": 5,
                          "reason": "Short README length",
                          "recommendation": "Add more detailed documentation"
                        }
                      ],
                      "intelligence": { "primaryLanguage": "Java" }
                    }
                    """;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        // Mock /api/repo/testowner/repo-beta
        mockHttpServer.createContext("/api/repo/testowner/repo-beta", exchange -> {
            String json = """
                    {
                      "repository": "testowner/repo-beta",
                      "owner": "testowner",
                      "repo": "repo-beta",
                      "totalScore": 75,
                      "categories": {
                        "SAFETY": { "score": 30, "maxPoints": 30, "categoryName": "Safety & Secret Exposure" },
                        "DOCUMENTATION": { "score": 10, "maxPoints": 20, "categoryName": "Documentation & Readme Quality" }
                      },
                      "deductionsCount": 2,
                      "deductions": [],
                      "intelligence": { "primaryLanguage": "Python" }
                    }
                    """;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        // Mock /api/status
        mockHttpServer.createContext("/api/status", exchange -> {
            String json = """
                    {
                      "service": "RepoLens",
                      "status": "UP",
                      "rateLimit": { "limit": 5000, "remaining": 4990, "resource": "core" }
                    }
                    """;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        mockHttpServer.start();

        // Initialize MCP server with custom client pointing to mock backend
        RepoLensBackendClient backendClient = new RepoLensBackendClient(
                "http://127.0.0.1:" + mockPort,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                new ObjectMapper()
        );

        server = McpServerApplication.createServer(tempDir, McpServerApplication.createTransport(), backendClient);
        ObjectMapper mapper = new ObjectMapper();

        // Handshake
        sendRequest("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test-runner\",\"version\":\"1.0\"}}}\n");
        waitForResponseLine(1, 5, TimeUnit.SECONDS);
        sendRequest("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n");

        // 1. Test analyze_profile
        sendRequest("{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/call\",\"params\":{\"name\":\"analyze_profile\",\"arguments\":{\"username\":\"testuser\"}}}\n");
        String analyzeResp = waitForResponseLine(10, 5, TimeUnit.SECONDS);
        JsonNode analyzeNode = mapper.readTree(analyzeResp);
        assertThat(analyzeNode.path("result").path("isError").asBoolean()).isFalse();
        JsonNode profileData = mapper.readTree(analyzeNode.path("result").path("content").get(0).path("text").asText());
        assertThat(profileData.path("username").asText()).isEqualTo("testuser");
        assertThat(profileData.path("status").asText()).isEqualTo("SUCCESS");

        // 2. Test score_repo
        sendRequest("{\"jsonrpc\":\"2.0\",\"id\":20,\"method\":\"tools/call\",\"params\":{\"name\":\"score_repo\",\"arguments\":{\"owner\":\"testowner\",\"repository\":\"repo-alpha\"}}}\n");
        String scoreResp = waitForResponseLine(20, 5, TimeUnit.SECONDS);
        JsonNode scoreNode = mapper.readTree(scoreResp);
        assertThat(scoreNode.path("result").path("isError").asBoolean()).isFalse();
        JsonNode repoData = mapper.readTree(scoreNode.path("result").path("content").get(0).path("text").asText());
        assertThat(repoData.path("repository").asText()).isEqualTo("repo-alpha");
        assertThat(repoData.path("scoreReport").path("totalScore").asInt()).isEqualTo(90);

        // 3. Test compare_repos
        sendRequest("{\"jsonrpc\":\"2.0\",\"id\":30,\"method\":\"tools/call\",\"params\":{\"name\":\"compare_repos\",\"arguments\":{\"repositories\":[\"testowner/repo-alpha\",\"testowner/repo-beta\"]}}}\n");
        String compareResp = waitForResponseLine(30, 5, TimeUnit.SECONDS);
        JsonNode compareNode = mapper.readTree(compareResp);
        assertThat(compareNode.path("result").path("isError").asBoolean()).isFalse();
        JsonNode compareData = mapper.readTree(compareNode.path("result").path("content").get(0).path("text").asText());
        assertThat(compareData.path("repositoriesEvaluated").asInt()).isEqualTo(2);
        assertThat(compareData.path("highlights").path("topRepository").asText()).isEqualTo("testowner/repo-alpha");

        // 4. Test fix_plan
        sendRequest("{\"jsonrpc\":\"2.0\",\"id\":40,\"method\":\"tools/call\",\"params\":{\"name\":\"fix_plan\",\"arguments\":{\"username\":\"testuser\"}}}\n");
        String fixResp = waitForResponseLine(40, 5, TimeUnit.SECONDS);
        JsonNode fixNode = mapper.readTree(fixResp);
        assertThat(fixNode.path("result").path("isError").asBoolean()).isFalse();
        JsonNode fixData = mapper.readTree(fixNode.path("result").path("content").get(0).path("text").asText());
        assertThat(fixData.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(fixData.path("fixPlan").path("totalEstimatedGain").asInt()).isEqualTo(12);

        // 5. Test explain_deduction
        sendRequest("{\"jsonrpc\":\"2.0\",\"id\":50,\"method\":\"tools/call\",\"params\":{\"name\":\"explain_deduction\",\"arguments\":{\"owner\":\"testowner\",\"repository\":\"repo-alpha\",\"category\":\"DOCUMENTATION\"}}}\n");
        String explainResp = waitForResponseLine(50, 5, TimeUnit.SECONDS);
        JsonNode explainNode = mapper.readTree(explainResp);
        assertThat(explainNode.path("result").path("isError").asBoolean()).isFalse();
        JsonNode explainData = mapper.readTree(explainNode.path("result").path("content").get(0).path("text").asText());
        assertThat(explainData.path("category").asText()).isEqualTo("DOCUMENTATION");
        assertThat(explainData.path("deductionsCount").asInt()).isEqualTo(1);
        assertThat(explainData.path("totalPointsOff").asInt()).isEqualTo(5);

        // 6. Test find_duplicates
        sendRequest("{\"jsonrpc\":\"2.0\",\"id\":60,\"method\":\"tools/call\",\"params\":{\"name\":\"find_duplicates\",\"arguments\":{\"username\":\"testuser\"}}}\n");
        String dupResp = waitForResponseLine(60, 5, TimeUnit.SECONDS);
        JsonNode dupNode = mapper.readTree(dupResp);
        assertThat(dupNode.path("result").path("isError").asBoolean()).isFalse();
        JsonNode dupData = mapper.readTree(dupNode.path("result").path("content").get(0).path("text").asText());
        assertThat(dupData.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(dupData.path("duplicatesReport").has("duplicateGroups")).isTrue();

        // 7. Test get_status with connected backend
        sendRequest("{\"jsonrpc\":\"2.0\",\"id\":70,\"method\":\"tools/call\",\"params\":{\"name\":\"get_status\",\"arguments\":{}}}\n");
        String statResp = waitForResponseLine(70, 5, TimeUnit.SECONDS);
        JsonNode statNode = mapper.readTree(statResp);
        assertThat(statNode.path("result").path("isError").asBoolean()).isFalse();
        JsonNode statData = mapper.readTree(statNode.path("result").path("content").get(0).path("text").asText());
        assertThat(statData.path("backend").path("status").asText()).isEqualTo("CONNECTED");

        assertStdoutPurity(mapper);
    }

    @Test
    void testBackendConnectionFailureGracefulHandling(@TempDir Path tempDir) throws Exception {
        // Points to an unopened port on localhost
        RepoLensBackendClient deadBackend = new RepoLensBackendClient(
                "http://127.0.0.1:59999",
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(200)).build(),
                new ObjectMapper()
        );

        server = McpServerApplication.createServer(tempDir, McpServerApplication.createTransport(), deadBackend);
        ObjectMapper mapper = new ObjectMapper();

        sendRequest("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test-runner\",\"version\":\"1.0\"}}}\n");
        waitForResponseLine(1, 5, TimeUnit.SECONDS);
        sendRequest("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n");

        sendRequest("{\"jsonrpc\":\"2.0\",\"id\":99,\"method\":\"tools/call\",\"params\":{\"name\":\"analyze_profile\",\"arguments\":{\"username\":\"torvalds\"}}}\n");
        String resp = waitForResponseLine(99, 5, TimeUnit.SECONDS);
        assertThat(resp).isNotNull();
        JsonNode callNode = mapper.readTree(resp);
        assertThat(callNode.path("result").path("isError").asBoolean()).isTrue();

        JsonNode errorText = mapper.readTree(callNode.path("result").path("content").get(0).path("text").asText());
        assertThat(errorText.path("error").asText()).isEqualTo("BackendError");
        assertThat(errorText.path("details").path("errorMessage").asText()).contains("Could not connect to RepoLens backend");

        assertStdoutPurity(mapper);
    }

    private void sendRequest(String jsonLine) throws Exception {
        testInWriter.write(jsonLine.getBytes(StandardCharsets.UTF_8));
        testInWriter.flush();
    }

    private void assertStdoutPurity(ObjectMapper mapper) throws Exception {
        List<String> lines = getCapturedLines();
        assertThat(lines).isNotEmpty();
        for (String line : lines) {
            assertThat(line.trim()).isNotEmpty();
            JsonNode message = mapper.readTree(line);
            assertThat(message.has("jsonrpc")).as("Every line must be valid JSON-RPC: " + line).isTrue();
            assertThat(message.path("jsonrpc").asText()).isEqualTo("2.0");

            String rawToken = System.getenv("GITHUB_TOKEN");
            if (rawToken != null && !rawToken.isBlank()) {
                assertThat(line).doesNotContain(rawToken);
            }
        }
    }

    private List<String> getCapturedLines() {
        String output = capturedStdout.toString(StandardCharsets.UTF_8);
        List<String> list = new ArrayList<>();
        for (String line : output.split("\r?\n")) {
            if (!line.trim().isEmpty()) {
                list.add(line.trim());
            }
        }
        return list;
    }

    private String waitForResponseLine(int expectedId, long timeout, TimeUnit unit) throws Exception {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        ObjectMapper mapper = new ObjectMapper();

        while (System.currentTimeMillis() < deadline) {
            List<String> lines = getCapturedLines();
            for (String line : lines) {
                try {
                    JsonNode node = mapper.readTree(line);
                    if (node.has("id") && node.get("id").asInt() == expectedId) {
                        return line;
                    }
                } catch (Exception ignored) {
                }
            }
            Thread.sleep(50);
        }
        return null;
    }
}
