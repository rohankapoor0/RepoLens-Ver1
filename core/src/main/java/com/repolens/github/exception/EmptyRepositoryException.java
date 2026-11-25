package com.repolens.github.exception;

/**
 * Thrown when GitHub returns HTTP 409 Conflict, indicating that a repository
 * is completely empty (no commits, no tree, no default branch).
 * <p>
 * This is a standard GitHub Git Data API response, distinct from authentication issues.
 */
public class EmptyRepositoryException extends RuntimeException {

    private final String repoOrUser;

    public EmptyRepositoryException(String repoOrUser, String message) {
        super(message);
        this.repoOrUser = repoOrUser;
    }

    public String getRepoOrUser() {
        return repoOrUser;
    }
}
