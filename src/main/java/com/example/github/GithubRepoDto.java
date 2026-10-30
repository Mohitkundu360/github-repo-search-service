package com.example.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubRepoDto(
        long id,
        String name,
        @JsonProperty("full_name") String fullName,
        Owner owner,
        String description,
        @JsonProperty("html_url") String htmlUrl,
        String language,
        @JsonProperty("stargazers_count") int stars,
        @JsonProperty("forks_count") int forks,
        @JsonProperty("updated_at") Instant updatedAt) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Owner(String login) {
    }
}
