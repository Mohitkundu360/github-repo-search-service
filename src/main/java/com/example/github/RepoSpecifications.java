package com.example.github;

import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

final class RepoSpecifications {

    private RepoSpecifications() {
    }

    static Specification<GithubRepositoryEntity> filter(String nameFilter, String language, Integer minStars) {
        Specification<GithubRepositoryEntity> spec = (root, query, cb) -> cb.conjunction();
        if (nameFilter != null && !nameFilter.isBlank()) {
            spec = spec.and(nameContains(nameFilter.trim()));
        }
        if (language != null && !language.isBlank()) {
            spec = spec.and(languageEquals(language.trim()));
        }
        if (minStars != null) {
            spec = spec.and(starsAtLeast(minStars));
        }
        return spec;
    }

    private static Specification<GithubRepositoryEntity> nameContains(String value) {
        String pattern = "%" + escapeLike(value.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.<String>get("name")), pattern, '\\');
    }

    private static Specification<GithubRepositoryEntity> languageEquals(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.<String>get("language")), lower);
    }

    private static Specification<GithubRepositoryEntity> starsAtLeast(int minStars) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.<Integer>get("stars"), minStars);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
