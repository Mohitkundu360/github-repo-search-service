package com.example.github;

import java.time.Instant;

public record RepoView(
        Long githubId,
        String name,
        String fullName,
        String owner,
        String description,
        String htmlUrl,
        String language,
        int stars,
        int forks,
        Instant updatedAt) {

    public static RepoView from(GithubRepositoryEntity e) {
        return new RepoView(
                e.getGithubId(),
                e.getName(),
                e.getFullName(),
                e.getOwnerLogin(),
                e.getDescription(),
                e.getHtmlUrl(),
                e.getLanguage(),
                e.getStars(),
                e.getForks(),
                e.getGithubUpdatedAt());
    }
}
