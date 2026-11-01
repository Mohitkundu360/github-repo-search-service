package com.example.github;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class GithubRepositoryEntityRepositoryTest {

    static {
        // The laptop's legacy zone id (Asia/Calcutta) is rejected by the Postgres container
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private GithubRepositoryEntityRepository repository;

    private static GithubRepoDto dto(long id, String name, String language, int stars) {
        return new GithubRepoDto(id, name, "octo/" + name, new GithubRepoDto.Owner("octo"), "desc",
                "https://github.com/octo/" + name, language, stars, 1, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private GithubRepositoryEntity save(long id, String name, String language, int stars) {
        return repository.saveAndFlush(GithubRepositoryEntity.from(dto(id, name, language, stars), Instant.now()));
    }

    private List<GithubRepositoryEntity> find(String name, String language, Integer minStars) {
        return repository.findAll(RepoSpecifications.filter(name, language, minStars),
                PageRequest.of(0, 50, Sort.by("id"))).getContent();
    }

    @Test
    void theDatabaseRejectsASecondRowWithTheSameGithubId() {
        save(1, "a", "Java", 5);

        assertThatThrownBy(() -> repository.saveAndFlush(
                GithubRepositoryEntity.from(dto(1, "other", "Java", 9), Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByGithubIdInReturnsOnlyTheRequestedIds() {
        save(1, "a", "Java", 5);
        save(2, "b", "Java", 5);
        save(3, "c", "Java", 5);

        assertThat(repository.findByGithubIdIn(List.of(2L, 3L, 99L)))
                .extracting(GithubRepositoryEntity::getGithubId)
                .containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void nameFilterIsCaseInsensitiveAndMatchesAnywhere() {
        save(1, "Spring-Boot", "Java", 5);
        save(2, "spring-data", "Java", 5);
        save(3, "hibernate", "Java", 5);

        assertThat(find("SPRING", null, null))
                .extracting(GithubRepositoryEntity::getName)
                .containsExactly("Spring-Boot", "spring-data");
    }

    @Test
    void languageFilterIsCaseInsensitiveAndExact() {
        save(1, "a", "Java", 5);
        save(2, "b", "JavaScript", 5);
        save(3, "c", null, 5);

        assertThat(find(null, "java", null))
                .extracting(GithubRepositoryEntity::getName)
                .containsExactly("a");
    }

    @Test
    void minStarsIncludesTheBoundary() {
        save(1, "a", "Java", 99);
        save(2, "b", "Java", 100);
        save(3, "c", "Java", 101);

        assertThat(find(null, null, 100))
                .extracting(GithubRepositoryEntity::getName)
                .containsExactly("b", "c");
    }

    @Test
    void filtersCombineWithAnd() {
        save(1, "spring-boot", "Java", 500);
        save(2, "spring-data", "Java", 50);
        save(3, "spring-py", "Python", 500);
        save(4, "hibernate", "Java", 500);

        assertThat(find("spring", "java", 100))
                .extracting(GithubRepositoryEntity::getName)
                .containsExactly("spring-boot");
    }

    @Test
    void blankFiltersAreIgnored() {
        save(1, "a", "Java", 5);
        save(2, "b", "Python", 5);

        assertThat(find("  ", " ", null)).hasSize(2);
        assertThat(find(null, null, null)).hasSize(2);
    }

    @Test
    void likeWildcardsInTheNameFilterAreTreatedAsLiterals() {
        save(1, "100%_done", "Java", 5);
        save(2, "100xxdone", "Java", 5);
        save(3, "a_b", "Java", 5);
        save(4, "aXb", "Java", 5);

        assertThat(find("%", null, null))
                .extracting(GithubRepositoryEntity::getName)
                .containsExactly("100%_done");
        assertThat(find("_", null, null))
                .extracting(GithubRepositoryEntity::getName)
                .containsExactlyInAnyOrder("100%_done", "a_b");
    }

    @Test
    void pagingWithTiedSortValuesNeverRepeatsOrSkipsRows() {
        for (long i = 1; i <= 25; i++) {
            save(i, "repo-" + i, "Java", 5);
        }
        Sort sort = Sort.by(Sort.Direction.DESC, "stars").and(Sort.by("id"));

        List<Long> seen = new ArrayList<>();
        Page<GithubRepositoryEntity> last = null;
        for (int page = 0; page < 3; page++) {
            last = repository.findAll(RepoSpecifications.filter(null, null, null), PageRequest.of(page, 10, sort));
            last.getContent().forEach(entity -> seen.add(entity.getGithubId()));
        }

        Set<Long> distinct = new HashSet<>(seen);
        assertThat(seen).hasSize(25);
        assertThat(distinct).hasSize(25);
        assertThat(last.getTotalElements()).isEqualTo(25L);
        assertThat(last.getTotalPages()).isEqualTo(3);
        assertThat(last.getContent()).hasSize(5);
    }
}
