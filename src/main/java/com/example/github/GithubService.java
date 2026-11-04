package com.example.github;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GithubService {

    private static final int MAX_QUERY_LENGTH = 256;
    static final int MAX_PAGE_SIZE = 100;

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "name", "name",
            "language", "language",
            "stars", "stars",
            "forks", "forks",
            "updated", "githubUpdatedAt");
    private static final Set<String> DESCENDING = Set.of("stars", "forks", "updated");

    private final GithubRepositoryEntityRepository repository;
    private final GithubClient githubClient;

    public GithubService(GithubRepositoryEntityRepository repository, GithubClient githubClient) {
        this.repository = repository;
        this.githubClient = githubClient;
    }

    @Transactional
    public List<RepoView> searchAndStore(String query) {
        String q = validateQuery(query);
        List<GithubRepoDto> items = githubClient.searchRepositories(q);
        if (items.isEmpty()) {
            return List.of();
        }

        Map<Long, GithubRepoDto> unique = new LinkedHashMap<>();
        for (GithubRepoDto item : items) {
            unique.putIfAbsent(item.id(), item);
        }

        Map<Long, GithubRepositoryEntity> existing = repository.findByGithubIdIn(unique.keySet()).stream()
                .collect(Collectors.toMap(GithubRepositoryEntity::getGithubId, Function.identity()));

        Instant now = Instant.now();
        List<GithubRepositoryEntity> toSave = new ArrayList<>();
        for (GithubRepoDto dto : unique.values()) {
            GithubRepositoryEntity entity = existing.get(dto.id());
            if (entity == null) {
                entity = GithubRepositoryEntity.from(dto, now);
            } else {
                entity.updateFrom(dto, now);
            }
            toSave.add(entity);
        }

        return repository.saveAll(toSave).stream().map(RepoView::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<RepoView> list(String nameFilter, String language, Integer minStars,
                                       String sortBy, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be 0 or greater.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        if (minStars != null && minStars < 0) {
            throw new IllegalArgumentException("minStars must be 0 or greater.");
        }

        Pageable pageable = PageRequest.of(page, size, resolveSort(sortBy));
        Specification<GithubRepositoryEntity> spec = RepoSpecifications.filter(nameFilter, language, minStars);
        Page<GithubRepositoryEntity> result = repository.findAll(spec, pageable);
        return PageResponse.from(result.map(RepoView::from));
    }

    private String validateQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query must not be empty.");
        }
        String trimmed = query.trim();
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("Query must be at most " + MAX_QUERY_LENGTH + " characters.");
        }
        return trimmed;
    }

    private Sort resolveSort(String sortBy) {
        Sort byId = Sort.by("id");
        if (sortBy == null || sortBy.isBlank()) {
            return byId;
        }
        String key = sortBy.trim();
        String field = SORT_FIELDS.get(key);
        if (field == null) {
            throw new IllegalArgumentException(
                    "Unsupported sortBy '" + key + "'. Allowed: " + new TreeSet<>(SORT_FIELDS.keySet()));
        }
        Sort.Direction direction = DESCENDING.contains(key) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, field).and(byId);
    }
}
