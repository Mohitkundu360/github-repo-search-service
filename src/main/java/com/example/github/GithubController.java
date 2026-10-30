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

    /** Lists stored repositories. sortBy: name, language (ascending); stars, forks, updated (descending). */
    @GetMapping("/repositories")
    public List<RepoView> list(
            @RequestParam(required = false) String nameFilter,
            @RequestParam(required = false) String sortBy) {
        return githubService.list(nameFilter, sortBy);
    }
}
