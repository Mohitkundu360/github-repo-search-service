package com.example.github;

public class GithubRateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public GithubRateLimitException(long retryAfterSeconds) {
        super("GitHub API rate limit reached. Retry in " + retryAfterSeconds + " seconds.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
