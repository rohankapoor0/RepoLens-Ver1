package com.repolens.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repolens.mcp.cache.McpCacheConfig;
import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerTest {

    private InputStream originalSystemIn;
    private PrintStream originalSystemOut;

    private PipedOutputStream testInWriter;
    private ByteArrayOutputStream capturedStdout;
    private McpSyncServer server;

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

        System.setIn(originalSystemIn);
        System.setOut(originalSystemOut);

        try {
            testInWriter.close();
        } catch (Exception ignored) {
        }
    }

    @Test
    void testInitializeToolsListAndGetStatusTool(@TempDir Path tempDir) throws Exception {
        // Start server in-process connected to our redirected System.in and System.out
        server = McpServerApplication.createServer(tempDir);

        ObjectMapper mapper = new ObjectMapper();

        // 1. Send initialize request
        String initRequest = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test-client\",\"version\":\"1.0.0\"}}}\n";
        testInWriter.write(initRequest.getBytes(StandardCharsets.UTF_8));
        testInWriter.flush();

        // Wait for initialize response
        String initResponseLine = waitForResponseLine(1, 5, TimeUnit.SECONDS);
        assertThat(initResponseLine).isNotNull();
        JsonNode initNode = mapper.readTree(initResponseLine);
        assertThat(initNode.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(initNode.path("id").asInt()).isEqualTo(1);
        assertThat(initNode.path("result").path("serverInfo").path("name").asText()).isEqualTo("repolens-mcp");
        assertThat(initNode.path("result").path("serverInfo").path("version").asText()).isEqualTo("1.0.0");
        assertThat(initNode.path("result").path("capabilities").path("tools").isObject()).isTrue();

        // 2. Send initialized notification
        String initializedNotif = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n";
        testInWriter.write(initializedNotif.getBytes(StandardCharsets.UTF_8));
        testInWriter.flush();

        // 3. Send tools/list request
        String listToolsRequest = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}\n";
        testInWriter.write(listToolsRequest.getBytes(StandardCharsets.UTF_8));
        testInWriter.flush();

        // Wait for tools/list response
        String listResponseLine = waitForResponseLine(2, 5, TimeUnit.SECONDS);
        assertThat(listResponseLine).isNotNull();
        JsonNode listNode = mapper.readTree(listResponseLine);
        assertThat(listNode.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(listNode.path("id").asInt()).isEqualTo(2);

        JsonNode toolsArray = listNode.path("result").path("tools");
        assertThat(toolsArray.isArray()).isTrue();
        assertThat(toolsArray.size()).isGreaterThanOrEqualTo(1);

        JsonNode getStatusTool = null;
        for (JsonNode t : toolsArray) {
            if ("get_status".equals(t.path("name").asText())) {
                getStatusTool = t;
                break;
            }
        }
        assertThat(getStatusTool).as("get_status tool should be registered").isNotNull();
        assertThat(getStatusTool.path("description").asText()).contains("read-only");

        // 4. Send tools/call for get_status
        String callStatusRequest = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"get_status\",\"arguments\":{}}}\n";
        testInWriter.write(callStatusRequest.getBytes(StandardCharsets.UTF_8));
        testInWriter.flush();

        // Wait for tools/call response
        String callResponseLine = waitForResponseLine(3, 5, TimeUnit.SECONDS);
        assertThat(callResponseLine).isNotNull();
        JsonNode callNode = mapper.readTree(callResponseLine);
        assertThat(callNode.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(callNode.path("id").asInt()).isEqualTo(3);
        assertThat(callNode.path("result").path("isError").asBoolean()).isFalse();

        String toolText = callNode.path("result").path("content").get(0).path("text").asText();
        JsonNode statusPayload = mapper.readTree(toolText);
        assertThat(statusPayload.path("service").asText()).isEqualTo("RepoLens MCP Server");
        assertThat(statusPayload.path("status").asText()).isEqualTo("UP");
        assertThat(statusPayload.path("server_version").asText()).isEqualTo("1.0.0");
        assertThat(statusPayload.has("github_token_detected")).isTrue();
        assertThat(statusPayload.path("github_token_detected").isBoolean()).isTrue();
        assertThat(statusPayload.path("cache_path").asText()).isEqualTo(tempDir.toString());
        assertThat(statusPayload.has("rate_limit")).isTrue();

        // 5. CRITICAL: Validate stdout purity - ONLY valid JSON-RPC messages, zero stray lines or banners
        List<String> lines = getCapturedLines();
        assertThat(lines).as("Should have captured response lines").isNotEmpty();
        for (String line : lines) {
            assertThat(line.trim()).isNotEmpty();
            JsonNode message = mapper.readTree(line);
            assertThat(message.has("jsonrpc")).as("Line must have jsonrpc: " + line).isTrue();
            assertThat(message.path("jsonrpc").asText()).isEqualTo("2.0");

            // Absolute rule: Never leak raw token even if GITHUB_TOKEN was present
            String rawToken = System.getenv("GITHUB_TOKEN");
            if (rawToken != null && !rawToken.isBlank()) {
                assertThat(line).doesNotContain(rawToken);
            }
        }
    }

    @Test
    void testCacheConfigDefaultAndEnvResolution(@TempDir Path tempDir) {
        Path resolved = McpCacheConfig.resolveCacheDir();
        assertThat(resolved).isNotNull();
        assertThat(resolved.isAbsolute()).isTrue();
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
