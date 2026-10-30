package com.example.github;

public class GithubApiException extends RuntimeException {

    private final int upstreamStatus;

    public GithubApiException(String message, int upstreamStatus, Throwable cause) {
        super(message, cause);
        this.upstreamStatus = upstreamStatus;
    }

    public int getUpstreamStatus() {
        return upstreamStatus;
    }
}
