package com.example.github;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/github")
public class GithubController {

    private final GithubService githubService;

    public GithubController(GithubService githubService) {
        this.githubService = githubService;
    }

    /** Searches GitHub, stores the results (upsert by GitHub repo id) and returns them. */
    @PostMapping("/search")
    public List<RepoView> search(@RequestBody GithubSearchRequest request) {
        return githubService.searchAndStore(request.getQuery());
    }

    /**
     * Lists stored repositories with optional filters and paging.
     * sortBy: name, language (ascending); stars, forks, updated (descending). size is 1-100.
     */
    @GetMapping("/repositories")
    public PageResponse<RepoView> list(
            @RequestParam(required = false) String nameFilter,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) Integer minStars,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return githubService.list(nameFilter, language, minStars, sortBy, page, size);
    }
}
