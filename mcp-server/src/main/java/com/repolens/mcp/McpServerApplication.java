package com.repolens.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.repolens.mcp.cache.McpCacheConfig;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * RepoLens MCP Server Application.
 * <p>
 * Standard I/O (stdio) Model Context Protocol server exposing repository analysis,
 * scoring, and diagnostic tools to AI coding agents and MCP clients.
 * <p>
 * CRITICAL RULE: System.out carries ONLY MCP JSON-RPC protocol messages.
 * Any logging, diagnostics, or banners MUST go to System.err.
 */
public class McpServerApplication {

    public static final String SERVER_NAME = "repolens-mcp";
    public static final String SERVER_VERSION = "1.0.0";

    static {
        // Guarantee early SLF4J binding to System.err before any logger or transport initialization
        System.setProperty("org.slf4j.simpleLogger.logFile", "System.err");
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");
    }

    public static void main(String[] args) {
        // Informational banner strictly on System.err
        System.err.println("[repolens-mcp] Starting RepoLens Model Context Protocol Server v" + SERVER_VERSION);
        System.err.println("[repolens-mcp] Transport: stdio (stdin/stdout reserved for JSON-RPC)");

        Path cacheDir = McpCacheConfig.resolveCacheDir();
        System.err.println("[repolens-mcp] Cache directory: " + cacheDir);

        StdioServerTransport transport = createTransport();
        McpSyncServer server = createServer(cacheDir, transport);

        CountDownLatch shutdownLatch = new CountDownLatch(1);

        // Monitor transport for EOF on stdin so server exits cleanly when client disconnects
        Thread eofMonitor = new Thread(() -> {
            try {
                java.lang.reflect.Field isClosingField = StdioServerTransport.class.getDeclaredField("isClosing");
                isClosingField.setAccessible(true);
                while (!isClosingField.getBoolean(transport)) {
                    Thread.sleep(200);
                }
            } catch (Exception ignored) {
            }
            shutdownLatch.countDown();
        });
        eofMonitor.setDaemon(true);
        eofMonitor.setName("mcp-eof-monitor");
        eofMonitor.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("[repolens-mcp] Shutting down stdio MCP server...");
            try {
                server.closeGracefully();
            } catch (Exception ignored) {
            }
            shutdownLatch.countDown();
        }));

        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[repolens-mcp] Server interrupted");
        }
    }

    /**
     * Creates and initializes the synchronous MCP server over a new StdioServerTransport.
     *
     * @param cacheDir path to the isolated MCP cache directory
     * @return configured McpSyncServer instance
     */
    public static McpSyncServer createServer(Path cacheDir) {
        return createServer(cacheDir, createTransport());
    }

    /**
     * Creates a new StdioServerTransport configured with JSR310 Jackson module.
     */
    public static StdioServerTransport createTransport() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new StdioServerTransport(objectMapper);
    }

    /**
     * Creates and initializes the synchronous MCP server using the given transport.
     *
     * @param cacheDir  path to the isolated MCP cache directory
     * @param transport transport to bind to
     * @return configured McpSyncServer instance
     */
    public static McpSyncServer createServer(Path cacheDir, StdioServerTransport transport) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tool(
                        new McpSchema.Tool(
                                "get_status",
                                "Returns RepoLens MCP status, token detection, rate limits, and cache directory (read-only).",
                                "{\"type\":\"object\",\"properties\":{}}"
                        ),
                        arguments -> handleGetStatus(cacheDir, objectMapper)
                )
                .build();
    }

    /**
     * Tool handler for 'get_status'.
     * Returns:
     * - github_token_detected: boolean only, never returns the raw token
     * - rate_limit: latest GitHub rate-limit info if available
     * - cache_path: isolated cache path
     * - server_version: RepoLens MCP server version
     */
    public static McpSchema.CallToolResult handleGetStatus(Path cacheDir, ObjectMapper objectMapper) {
        Map<String, Object> statusMap = new LinkedHashMap<>();
        statusMap.put("service", "RepoLens MCP Server");
        statusMap.put("status", "UP");
        statusMap.put("server_version", SERVER_VERSION);

        String tokenEnv = System.getenv("GITHUB_TOKEN");
        boolean tokenDetected = tokenEnv != null && !tokenEnv.isBlank();
        statusMap.put("github_token_detected", tokenDetected);

        statusMap.put("cache_path", cacheDir != null ? cacheDir.toString() : "unknown");

        Map<String, Object> rateLimitMap = fetchRateLimitInfo(tokenEnv);
        if (rateLimitMap != null) {
            statusMap.put("rate_limit", rateLimitMap);
        } else {
            statusMap.put("rate_limit", "unavailable");
        }

        try {
            String jsonText = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(statusMap);
            return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(jsonText)), false);
        } catch (Exception e) {
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("{\"error\":\"Failed to serialize status: " + e.getMessage() + "\"}")),
                    true
            );
        }
    }

    private static Map<String, Object> fetchRateLimitInfo(String tokenEnv) {
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
            // Graceful fallback for offline, network issues, or timeouts
        }
        return null;
    }
}
