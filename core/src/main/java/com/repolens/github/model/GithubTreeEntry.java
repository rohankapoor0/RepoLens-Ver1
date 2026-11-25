package com.repolens.github.model;

/**
 * Entry inside a recursive Git tree.
 */
public record GithubTreeEntry(
        String path,
        String mode,
        String type, // "blob" or "tree"
        Long size,
        String sha
) {
    public boolean isFile() {
        return "blob".equalsIgnoreCase(type);
    }

    public boolean isDirectory() {
        return "tree".equalsIgnoreCase(type);
    }
}
