package com.example.github;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GithubClientTest {

    private static final String BASE_URL = "https://api.github.test";

    private static final String ONE_REPO = """
            {"total_count":1,"items":[{"id":42,"name":"demo","full_name":"octo/demo",
            "owner":{"login":"octo","id":7},"description":"A demo","html_url":"https://github.com/octo/demo",
            "language":"Java","stargazers_count":123,"forks_count":4,"updated_at":"2026-10-30T04:27:15Z",
            "unknown_field":true}]}
            """;

    private static final String EMPTY_RESULT = "{\"items\":[]}";

    private MockRestServiceServer server;
    private GithubClient client;

    @BeforeEach
    void setUp() {
        init("");
    }

    private void init(String token) {
        RestClient.Builder builder =
                GithubClient.newBuilder(BASE_URL, token, Duration.ofSeconds(1), Duration.ofSeconds(1));
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GithubClient(builder.build());
    }

    // ---------- mapping ----------

    @Test
    void mapsSearchResponseToDtos() {
        server.expect(method(HttpMethod.GET))
                .andExpect(request -> assertThat(request.getURI().getPath()).isEqualTo("/search/repositories"))
                .andRespond(withSuccess(ONE_REPO, MediaType.APPLICATION_JSON));

        List<GithubRepoDto> result = client.searchRepositories("spring");

        assertThat(result).hasSize(1);
        GithubRepoDto dto = result.get(0);
        assertThat(dto.id()).isEqualTo(42L);
        assertThat(dto.name()).isEqualTo("demo");
        assertThat(dto.fullName()).isEqualTo("octo/demo");
        assertThat(dto.owner().login()).isEqualTo("octo");
        assertThat(dto.language()).isEqualTo("Java");
        assertThat(dto.stars()).isEqualTo(123);
        assertThat(dto.forks()).isEqualTo(4);
        assertThat(dto.updatedAt()).isEqualTo(Instant.parse("2026-10-30T04:27:15Z"));
        server.verify();
    }

    @Test
    void returnsEmptyListWhenItemsAreMissing() {
        server.expect(anything()).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.searchRepositories("spring")).isEmpty();
    }

    // ---------- request building ----------

    @ParameterizedTest
    @CsvSource({
            "spring boot,q=spring%20boot&per_page=30",
            "spring&per_page=100,q=spring%26per_page%3D100&per_page=30"
    })
    void encodesTheQueryValue_soItCannotInjectParameters(String query, String expectedRawQuery) {
        server.expect(request -> assertThat(request.getURI().getRawQuery()).isEqualTo(expectedRawQuery))
                .andRespond(withSuccess(EMPTY_RESULT, MediaType.APPLICATION_JSON));

        client.searchRepositories(query);

        server.verify();
    }

    @Test
    void sendsBearerTokenWhenConfigured() {
        init("tkn");
        server.expect(header(HttpHeaders.AUTHORIZATION, "Bearer tkn"))
                .andRespond(withSuccess(EMPTY_RESULT, MediaType.APPLICATION_JSON));

        client.searchRepositories("spring");

        server.verify();
    }

    @Test
    void sendsNoAuthorizationHeaderWithoutToken() {
        server.expect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess(EMPTY_RESULT, MediaType.APPLICATION_JSON));

        client.searchRepositories("spring");

        server.verify();
    }

    // ---------- error mapping ----------

    @Test
    void maps429ToRateLimitWithRetryAfter() {
        server.expect(anything())
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "42"));

        assertThatThrownBy(() -> client.searchRepositories("x"))
                .isInstanceOfSatisfying(GithubRateLimitException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(42L));
    }

    @Test
    void maps403WithExhaustedQuotaToRateLimit() {
        long reset = Instant.now().getEpochSecond() + 30;
        server.expect(anything())
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .header("X-RateLimit-Remaining", "0")
                        .header("X-RateLimit-Reset", String.valueOf(reset)));

        assertThatThrownBy(() -> client.searchRepositories("x"))
                .isInstanceOfSatisfying(GithubRateLimitException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isBetween(1L, 30L));
    }

    @Test
    void maps403WithoutRateLimitHeadersToApiException() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.searchRepositories("x"))
                .isInstanceOfSatisfying(GithubApiException.class, e -> {
                    assertThat(e.getUpstreamStatus()).isEqualTo(403);
                    assertThat(e.isTimeout()).isFalse();
                });
    }

    @Test
    void maps500ToApiExceptionWithUpstreamStatus() {
        server.expect(anything()).andRespond(withServerError());

        assertThatThrownBy(() -> client.searchRepositories("x"))
                .isInstanceOfSatisfying(GithubApiException.class, e -> {
                    assertThat(e.getUpstreamStatus()).isEqualTo(500);
                    assertThat(e.isTimeout()).isFalse();
                });
    }

    @Test
    void mapsReadTimeoutToTimeoutException() {
        server.expect(anything()).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });

        assertThatThrownBy(() -> client.searchRepositories("x"))
                .isInstanceOfSatisfying(GithubApiException.class, e -> assertThat(e.isTimeout()).isTrue());
    }

    @Test
    void mapsOtherIoFailuresToNonTimeoutException() {
        server.expect(anything()).andRespond(request -> {
            throw new ConnectException("Connection refused");
        });

        assertThatThrownBy(() -> client.searchRepositories("x"))
                .isInstanceOfSatisfying(GithubApiException.class, e -> assertThat(e.isTimeout()).isFalse());
    }

    // ---------- helpers ----------

    @Test
    void retryAfterUsesTheHeaderSeconds() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, "42");

        assertThat(GithubClient.retryAfterSeconds(headers, Instant.now())).isEqualTo(42L);
    }

    @Test
    void retryAfterFallsBackToTheResetEpoch() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-RateLimit-Reset", String.valueOf(now.getEpochSecond() + 30));

        assertThat(GithubClient.retryAfterSeconds(headers, now)).isEqualTo(30L);
    }

    @Test
    void retryAfterDefaultsTo60WhenMissingOrInvalid() {
        HttpHeaders garbage = new HttpHeaders();
        garbage.add(HttpHeaders.RETRY_AFTER, "soon");

        assertThat(GithubClient.retryAfterSeconds(null, Instant.now())).isEqualTo(60L);
        assertThat(GithubClient.retryAfterSeconds(new HttpHeaders(), Instant.now())).isEqualTo(60L);
        assertThat(GithubClient.retryAfterSeconds(garbage, Instant.now())).isEqualTo(60L);
    }

    @ParameterizedTest
    @CsvSource({"0,1", "-5,1", "42,42", "999999,3600"})
    void retryAfterIsClampedBetween1And3600(String raw, long expected) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, raw);

        assertThat(GithubClient.retryAfterSeconds(headers, Instant.now())).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "429,,,true",
            "403,0,,true",
            "403,,30,true",
            "403,,,false",
            "403,5,,false",
            "500,0,,false",
            "404,,,false"
    })
    void decidesWhichResponsesAreRateLimits(int status, String remaining, String retryAfter, boolean expected) {
        HttpHeaders headers = new HttpHeaders();
        if (remaining != null) {
            headers.add("X-RateLimit-Remaining", remaining);
        }
        if (retryAfter != null) {
            headers.add(HttpHeaders.RETRY_AFTER, retryAfter);
        }

        assertThat(GithubClient.isRateLimited(status, headers)).isEqualTo(expected);
    }
}
