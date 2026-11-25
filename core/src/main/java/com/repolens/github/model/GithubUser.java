package com.repolens.github.model;

/**
 * Public GitHub user profile representation retrieved from GET /users/{username}.
 */
public record GithubUser(
        String login,
        long id,
        String avatarUrl,
        String htmlUrl,
        String name,
        String company,
        String blog,
        String location,
        String bio,
        int publicRepos,
        int publicGists,
        int followers,
        int following
) {}
