package com.example.github;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubServiceTest {

    @Mock
    private GithubRepositoryEntityRepository repository;

    @Mock
    private GithubClient githubClient;

    @InjectMocks
    private GithubService service;

    @Captor
    private ArgumentCaptor<List<GithubRepositoryEntity>> saved;

    @Captor
    private ArgumentCaptor<Pageable> pageable;

    private static GithubRepoDto dto(long id, String name, int stars) {
        return new GithubRepoDto(id, name, "octo/" + name, new GithubRepoDto.Owner("octo"), "desc",
                "https://github.com/octo/" + name, "Java", stars, 1, Instant.parse("2026-01-01T00:00:00Z"));
    }

    // ---------- searchAndStore ----------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsBlankQuery(String query) {
        assertThatThrownBy(() -> service.searchAndStore(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Query must not be empty.");

        verifyNoInteractions(githubClient, repository);
    }

    @Test
    void rejectsTooLongQuery() {
        String tooLong = "x".repeat(257);

        assertThatThrownBy(() -> service.searchAndStore(tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 256");

        verifyNoInteractions(githubClient, repository);
    }

    @Test
    void trimsTheQueryAndSkipsTheDatabaseWhenNothingIsFound() {
        when(githubClient.searchRepositories("spring boot")).thenReturn(List.of());

        List<RepoView> result = service.searchAndStore("  spring boot  ");

        assertThat(result).isEmpty();
        verify(githubClient).searchRepositories("spring boot");
        verifyNoInteractions(repository);
    }

    @Test
    void insertsRepositoriesThatAreNotStoredYet() {
        when(githubClient.searchRepositories("java")).thenReturn(List.of(dto(1, "a", 10), dto(2, "b", 20)));
        when(repository.findByGithubIdIn(anyCollection())).thenReturn(List.of());
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<RepoView> views = service.searchAndStore("java");

        assertThat(views).extracting(RepoView::githubId).containsExactly(1L, 2L);
        assertThat(views).extracting(RepoView::stars).containsExactly(10, 20);
    }

    @Test
    void updatesAnExistingRepositoryInsteadOfInsertingAnotherRow() {
        Instant firstFetch = Instant.parse("2026-01-01T00:00:00Z");
        GithubRepositoryEntity existing = GithubRepositoryEntity.from(dto(1, "a", 10), firstFetch);
        when(githubClient.searchRepositories("java")).thenReturn(List.of(dto(1, "a-renamed", 99)));
        when(repository.findByGithubIdIn(anyCollection())).thenReturn(List.of(existing));
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        service.searchAndStore("java");

        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).containsExactly(existing);
        assertThat(existing.getStars()).isEqualTo(99);
        assertThat(existing.getName()).isEqualTo("a-renamed");
        assertThat(existing.getFetchedAt()).isAfter(firstFetch);
    }

    @Test
    void keepsOnlyTheFirstOccurrenceOfARepeatedGithubId() {
        when(githubClient.searchRepositories("java"))
                .thenReturn(List.of(dto(1, "first", 1), dto(1, "second", 2), dto(2, "x", 3)));
        when(repository.findByGithubIdIn(anyCollection())).thenReturn(List.of());
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        service.searchAndStore("java");

        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(GithubRepositoryEntity::getName).containsExactly("first", "x");
    }

    // ---------- list ----------

    @ParameterizedTest
    @CsvSource({"-1,10,", "0,0,", "0,101,", "0,10,-1"})
    void listRejectsInvalidPagingAndFilters(int page, int size, Integer minStars) {
        assertThatThrownBy(() -> service.list(null, null, minStars, null, page, size))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void listRejectsAnUnknownSortKeyAndNamesTheAllowedOnes() {
        assertThatThrownBy(() -> service.list(null, null, null, "bogus", 0, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported sortBy 'bogus'")
                .hasMessageContaining("Allowed:");

        verifyNoInteractions(repository);
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @CsvSource({
            "stars,stars,DESC",
            "forks,forks,DESC",
            "updated,githubUpdatedAt,DESC",
            "name,name,ASC",
            "language,language,ASC"
    })
    void listMapsSortKeysToEntityFieldsWithAnIdTieBreaker(String key, String property, Sort.Direction direction) {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.list(null, null, null, key, 2, 10);

        verify(repository).findAll(any(Specification.class), pageable.capture());
        Pageable used = pageable.getValue();
        assertThat(used.getPageNumber()).isEqualTo(2);
        assertThat(used.getPageSize()).isEqualTo(10);
        Sort.Order order = used.getSort().getOrderFor(property);
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(direction);
        assertThat(used.getSort().getOrderFor("id")).isNotNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void listWithoutSortKeyOrdersById() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.list(null, null, null, null, 0, 20);

        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getSort().stream().map(Sort.Order::getProperty)).containsExactly("id");
    }

    @SuppressWarnings("unchecked")
    @Test
    void listWrapsTheRepositoryPageInAPageResponse() {
        GithubRepositoryEntity entity = GithubRepositoryEntity.from(dto(5, "five", 50), Instant.now());
        Page<GithubRepositoryEntity> page = new PageImpl<>(List.of(entity), PageRequest.of(1, 10), 11);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<RepoView> response = service.list(null, null, null, null, 1, 10);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(11L);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.content()).extracting(RepoView::githubId).containsExactly(5L);
    }
}
