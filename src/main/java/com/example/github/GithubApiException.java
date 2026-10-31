package com.example.github;

public class GithubApiException extends RuntimeException {

    private final int upstreamStatus;
    private final boolean timeout;

    public GithubApiException(String message, int upstreamStatus, Throwable cause) {
        this(message, upstreamStatus, false, cause);
    }

    public GithubApiException(String message, int upstreamStatus, boolean timeout, Throwable cause) {
        super(message, cause);
        this.upstreamStatus = upstreamStatus;
        this.timeout = timeout;
    }

    public int getUpstreamStatus() {
        return upstreamStatus;
    }

    public boolean isTimeout() {
        return timeout;
    }
}
