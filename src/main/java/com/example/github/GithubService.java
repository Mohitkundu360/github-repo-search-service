package com.example.github;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
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

    private static final String GITHUB_SEARCH_URL = "https://api.github.com/search/repositories";
    private static final int MAX_QUERY_LENGTH = 256;
    private static final int PER_PAGE = 30;
    static final int MAX_PAGE_SIZE = 100;

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "name", "name",
            "language", "language",
            "stars", "stars",
            "forks", "forks",
            "updated", "githubUpdatedAt");
    private static final Set<String> DESCENDING = Set.of("stars", "forks", "updated");

    private final GithubRepositoryEntityRepository repository;
    private final RestClient restClient;

    public GithubService(GithubRepositoryEntityRepository repository, RestClient.Builder restClientBuilder) {
        this.repository = repository;
        this.restClient = restClientBuilder
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .build();
    }

    @Transactional
    public List<RepoView> searchAndStore(String query) {
        String q = validateQuery(query);
        GithubSearchResponse response = fetch(q);

        List<GithubRepoDto> items = (response == null || response.items() == null) ? List.of() : response.items();
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

    private GithubSearchResponse fetch(String q) {
        URI uri = UriComponentsBuilder.fromUriString(GITHUB_SEARCH_URL)
                .queryParam("q", "{q}")
                .queryParam("per_page", PER_PAGE)
                .encode()
                .buildAndExpand(Map.of("q", q))
                .toUri();
        try {
            return restClient.get().uri(uri).retrieve().body(GithubSearchResponse.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            throw new GithubApiException("GitHub API returned HTTP " + status, status, e);
        } catch (RestClientException e) {
            throw new GithubApiException("Could not reach the GitHub API", 0, e);
        }
    }
}
