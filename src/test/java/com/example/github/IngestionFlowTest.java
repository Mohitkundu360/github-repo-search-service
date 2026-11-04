package com.example.github;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class IngestionFlowTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GithubRepositoryEntityRepository repository;

    @MockitoBean
    private GithubClient githubClient;

    @BeforeEach
    void cleanTable() {
        repository.deleteAll();
    }

    private static GithubRepoDto dto(long id, String name, int stars) {
        return new GithubRepoDto(id, name, "octo/" + name, new GithubRepoDto.Owner("octo"), "desc",
                "https://github.com/octo/" + name, "Java", stars, 1, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private void search(String query) throws Exception {
        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"" + query + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void searchingTwiceUpdatesRowsInsteadOfDuplicatingThem() throws Exception {
        when(githubClient.searchRepositories("java")).thenReturn(List.of(dto(1, "a", 10), dto(2, "b", 20)));
        search("java");
        assertThat(repository.count()).isEqualTo(2L);

        when(githubClient.searchRepositories("java")).thenReturn(List.of(dto(1, "a", 15)));
        search("java");

        assertThat(repository.count()).isEqualTo(2L);
        assertThat(repository.findByGithubIdIn(List.of(1L))).hasSize(1)
                .first().satisfies(entity -> assertThat(entity.getStars()).isEqualTo(15));
    }

    @Test
    void aRateLimitedSearchReturns429AndStoresNothing() throws Exception {
        when(githubClient.searchRepositories("java")).thenThrow(new GithubRateLimitException(30));

        mockMvc.perform(post("/api/github/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"java\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"));

        assertThat(repository.count()).isZero();
    }

    @Test
    void listEndpointPagesAndSortsStoredRows() throws Exception {
        when(githubClient.searchRepositories("java"))
                .thenReturn(List.of(dto(1, "a", 10), dto(2, "b", 30), dto(3, "c", 20)));
        search("java");

        mockMvc.perform(get("/api/github/repositories").param("size", "2").param("sortBy", "stars"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].stars").value(30))
                .andExpect(jsonPath("$.content[1].stars").value(20))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }
}
