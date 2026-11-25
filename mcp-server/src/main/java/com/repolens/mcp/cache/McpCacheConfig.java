package com.repolens.mcp.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Cache configuration for the RepoLens MCP Server.
 * <p>
 * Uses an isolated cache location so it never conflicts with the web application's H2 database.
 * Controlled by the {@code REPOLENS_CACHE_DIR} environment variable, defaulting to {@code ~/.repolens/mcp-cache}.
 */
public final class McpCacheConfig {

    public static final String CACHE_DIR_ENV = "REPOLENS_CACHE_DIR";
    public static final String DEFAULT_CACHE_SUBDIR = ".repolens/mcp-cache";

    private McpCacheConfig() {
    }

    /**
     * Resolves the MCP cache directory path and creates it if it does not already exist.
     * All diagnostic output is routed to {@code System.err} to protect {@code System.out}.
     *
     * @return normalized absolute Path to the MCP cache directory
     */
    public static Path resolveCacheDir() {
        String envPath = System.getenv(CACHE_DIR_ENV);
        Path dir;
        if (envPath != null && !envPath.isBlank()) {
            dir = Paths.get(envPath.trim()).toAbsolutePath().normalize();
        } else {
            String userHome = System.getProperty("user.home", ".");
            dir = Paths.get(userHome, ".repolens", "mcp-cache").toAbsolutePath().normalize();
        }

        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            System.err.println("[WARN] Failed to create MCP cache directory: " + dir + " (" + e.getMessage() + ")");
        }

        return dir;
    }
}
