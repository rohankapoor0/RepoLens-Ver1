package com.repolens.web.validation;

import com.repolens.web.exception.InvalidUsernameException;

import java.util.regex.Pattern;

/**
 * Validates and normalizes usernames against official GitHub username constraints.
 * <p>
 * Supported inputs:
 * <ul>
 *   <li>{@code username}</li>
 *   <li>{@code @username}</li>
 * </ul>
 * Rules:
 * <ul>
 *   <li>1 to 39 alphanumeric characters and single hyphens.</li>
 *   <li>Cannot start or end with a hyphen.</li>
 *   <li>Cannot have consecutive hyphens.</li>
 * </ul>
 */
public class GithubUsernameValidator {

    private static final Pattern GITHUB_USERNAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38}$");

    /**
     * Normalizes a username by trimming whitespace and stripping any leading '@' prefix,
     * then validates against GitHub constraints.
     *
     * @param username input username or @username
     * @return clean normalized username
     * @throws InvalidUsernameException if input is invalid
     */
    public static String normalize(String username) {
        if (username == null || username.isBlank()) {
            throw new InvalidUsernameException(username, "Username cannot be empty or blank.");
        }

        String trimmed = username.trim();
        if (trimmed.startsWith("@")) {
            trimmed = trimmed.substring(1).trim();
        }

        validate(trimmed);
        return trimmed;
    }

    /**
     * Validates that the given username (or @username) complies with GitHub rules.
     *
     * @param username input username or @username
     * @throws InvalidUsernameException if input is invalid
     */
    public static void validate(String username) {
        if (username == null || username.isBlank()) {
            throw new InvalidUsernameException(username, "Username cannot be empty or blank.");
        }

        String trimmed = username.trim();
        if (trimmed.startsWith("@")) {
            trimmed = trimmed.substring(1).trim();
        }

        if (trimmed.isEmpty()) {
            throw new InvalidUsernameException(username, "Username cannot be empty or blank.");
        }

        if (trimmed.length() > 39) {
            throw new InvalidUsernameException(trimmed, "GitHub username exceeds the maximum allowed length of 39 characters.");
        }

        if (!GITHUB_USERNAME_PATTERN.matcher(trimmed).matches()) {
            throw new InvalidUsernameException(trimmed,
                    "Invalid GitHub username '" + trimmed + "'. GitHub usernames must be 1-39 alphanumeric characters, " +
                            "cannot begin or end with a hyphen, and cannot contain consecutive hyphens.");
        }
    }
}
