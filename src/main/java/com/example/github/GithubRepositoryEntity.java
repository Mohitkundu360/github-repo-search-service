package com.example.github;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "github_repositories",
        uniqueConstraints = @UniqueConstraint(name = "uk_github_repositories_github_id", columnNames = "github_id"))
public class GithubRepositoryEntity {

    private static final int MAX_DESCRIPTION = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "github_id", nullable = false)
    private Long githubId;

    @Column(nullable = false)
    private String name;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "owner_login")
    private String ownerLogin;

    @Column(length = MAX_DESCRIPTION)
    private String description;

    @Column(name = "html_url", nullable = false, length = 500)
    private String htmlUrl;

    @Column(length = 100)
    private String language;

    @Column(nullable = false)
    private int stars;

    @Column(nullable = false)
    private int forks;

    @Column(name = "github_updated_at")
    private Instant githubUpdatedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected GithubRepositoryEntity() {
        // required by JPA
    }

    public static GithubRepositoryEntity from(GithubRepoDto dto, Instant fetchedAt) {
        GithubRepositoryEntity entity = new GithubRepositoryEntity();
        entity.updateFrom(dto, fetchedAt);
        return entity;
    }

    public void updateFrom(GithubRepoDto dto, Instant fetchedAt) {
        this.githubId = dto.id();
        this.name = dto.name();
        this.fullName = dto.fullName();
        this.ownerLogin = dto.owner() == null ? null : dto.owner().login();
        this.description = truncate(dto.description(), MAX_DESCRIPTION);
        this.htmlUrl = dto.htmlUrl();
        this.language = dto.language();
        this.stars = dto.stars();
        this.forks = dto.forks();
        this.githubUpdatedAt = dto.updatedAt();
        this.fetchedAt = fetchedAt;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public Long getGithubId() {
        return githubId;
    }

    public String getName() {
        return name;
    }

    public String getFullName() {
        return fullName;
    }

    public String getOwnerLogin() {
        return ownerLogin;
    }

    public String getDescription() {
        return description;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public String getLanguage() {
        return language;
    }

    public int getStars() {
        return stars;
    }

    public int getForks() {
        return forks;
    }

    public Instant getGithubUpdatedAt() {
        return githubUpdatedAt;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
