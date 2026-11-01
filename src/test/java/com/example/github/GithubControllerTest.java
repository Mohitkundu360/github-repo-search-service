package com.example.github;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GithubController.class)
class GithubControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GithubService githubService;

    private static RepoView view() {
        return new RepoView(1L, "demo", "octo/demo", "octo", "A demo", "https://github.com/octo/demo",
                "Java", 123, 4, Instant.parse("2026-01-01T00:00:00Z"));
    }

    // ---------- GET /api/github/repositories ----------

    @Test
    void listUsesDefaultsAndReturnsAPageObject() throws Exception {
        when(githubService.list(null, null, null, null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(view()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/github/repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("demo"))
                .andExpect(jsonPath("$.content[0].stars").value(123))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void listPassesEveryQueryParameterToTheService() throws Exception {
        when(githubService.list("spring", "java", 100, "stars", 2, 5))
                .thenReturn(new PageResponse<>(List.of(), 2, 5, 0, 0));

        mockMvc.perform(get("/api/github/repositories")
                        .param("nameFilter", "spring")
                        .param("language", "java")
                        .param("minStars", "100")
                        .param("sortBy", "stars")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(githubService).list("spring", "java", 100, "stars", 2, 5);
    }

    @Test
    void listMapsServiceValidationErrorsToProblemDetail400() throws Exception {
        when(githubService.list(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new IllegalArgumentException("size must be between 1 and 100."));

        mockMvc.perform(get("/api/github/repositories").param("size", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("size must be between 1 and 100."));
    }

    @Test
    void listRejectsANonNumericPageBeforeCallingTheService() throws Exception {
        mockMvc.perform(get("/api/github/repositories").param("page", "abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(githubService);
    }

    // ---------- POST /api/github/search ----------

    @Test
    void searchReturnsTheStoredRepositories() throws Exception {
        when(githubService.searchAndStore("java")).thenReturn(List.of(view()));

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"java\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].githubId").value(1))
                .andExpect(jsonPath("$[0].fullName").value("octo/demo"));
    }

    @Test
    void searchMapsARateLimitTo429WithRetryAfter() throws Exception {
        when(githubService.searchAndStore("java")).thenThrow(new GithubRateLimitException(42));

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"java\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(jsonPath("$.title").value("GitHub rate limit"));
    }

    @Test
    void searchMapsAnUpstreamTimeoutTo504() throws Exception {
        when(githubService.searchAndStore("java"))
                .thenThrow(new GithubApiException("GitHub API timed out", 0, true, new RuntimeException()));

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"java\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.title").value("GitHub API error"));
    }

    @Test
    void searchMapsOtherUpstreamFailuresTo502() throws Exception {
        when(githubService.searchAndStore("java"))
                .thenThrow(new GithubApiException("GitHub API returned HTTP 500", 500, new RuntimeException()));

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"java\"}"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void searchRejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(githubService);
    }
}
