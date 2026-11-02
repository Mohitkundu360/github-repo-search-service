package com.example.github;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SchemaMigrationTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void bothMigrationsAreAppliedSuccessfully() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class);

        assertThat(versions).containsExactly("1", "2");
    }

    @Test
    void languageAndStarsIndexExistsWithTheExpectedDefinition() {
        String definition = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes "
                        + "WHERE tablename = 'github_repositories' AND indexname = 'idx_repos_lang_stars'",
                String.class);

        assertThat(definition).contains("lower(").contains("stars DESC");
    }

    @Test
    void githubIdIsGuardedByAUniqueConstraint() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint "
                        + "WHERE conname = 'uk_github_repositories_github_id' AND contype = 'u'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }
}
