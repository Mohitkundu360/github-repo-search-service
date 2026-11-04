package com.example.github;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class GithubClient {

    static final int PER_PAGE = 30;
    private static final long DEFAULT_RETRY_AFTER_SECONDS = 60;
    private static final long MAX_RETRY_AFTER_SECONDS = 3600;

    private final RestClient restClient;

    @Autowired
    public GithubClient(
            @Value("${github.api.base-url:https://api.github.com}") String baseUrl,
            @Value("${github.token:}") String token,
            @Value("${github.api.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${github.api.read-timeout-ms:10000}") long readTimeoutMs) {
        this(newBuilder(baseUrl, token, Duration.ofMillis(connectTimeoutMs), Duration.ofMillis(readTimeoutMs)).build());
    }

    GithubClient(RestClient restClient) {
        this.restClient = restClient;
    }

    static RestClient.Builder newBuilder(String baseUrl, String token, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(HttpHeaders.USER_AGENT, "github-repo-search-service");
        if (token != null && !token.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim());
        }
        return builder;
    }

    public List<GithubRepoDto> searchRepositories(String query) {
        try {
            GithubSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search/repositories")
                            .queryParam("q", "{q}")
                            .queryParam("per_page", PER_PAGE)
                            .build(query))
                    .retrieve()
                    .body(GithubSearchResponse.class);
            return (response == null || response.items() == null) ? List.of() : response.items();
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            boolean timeout = hasCause(e, SocketTimeoutException.class);
            throw new GithubApiException(
                    timeout ? "GitHub API timed out" : "Could not reach the GitHub API", 0, timeout, e);
        } catch (RestClientException e) {
            throw new GithubApiException("Unexpected error calling the GitHub API", 0, false, e);
        }
    }

    private RuntimeException translate(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        HttpHeaders headers = e.getResponseHeaders();
        if (isRateLimited(status, headers)) {
            return new GithubRateLimitException(retryAfterSeconds(headers, Instant.now()));
        }
        return new GithubApiException("GitHub API returned HTTP " + status, status, false, e);
    }

    static boolean isRateLimited(int status, HttpHeaders headers) {
        if (status == 429) {
            return true;
        }
        if (status != 403 || headers == null) {
            return false;
        }
        return "0".equals(headers.getFirst("X-RateLimit-Remaining"))
                || headers.getFirst(HttpHeaders.RETRY_AFTER) != null;
    }

    static long retryAfterSeconds(HttpHeaders headers, Instant now) {
        long seconds = DEFAULT_RETRY_AFTER_SECONDS;
        if (headers != null) {
            String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
            String reset = headers.getFirst("X-RateLimit-Reset");
            try {
                if (retryAfter != null) {
                    seconds = Long.parseLong(retryAfter.trim());
                } else if (reset != null) {
                    seconds = Long.parseLong(reset.trim()) - now.getEpochSecond();
                }
            } catch (NumberFormatException ignored) {
                // keep the default
            }
        }
        return Math.max(1, Math.min(seconds, MAX_RETRY_AFTER_SECONDS));
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}
