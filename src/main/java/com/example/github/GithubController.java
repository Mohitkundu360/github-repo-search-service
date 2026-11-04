package com.example.github;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/github")
@Tag(name = "Repositories", description = "Search GitHub and browse the stored repositories")
public class GithubController {

    private final GithubService githubService;

    public GithubController(GithubService githubService) {
        this.githubService = githubService;
    }

    @Operation(summary = "Search GitHub and store the results",
            description = "Queries GitHub's repository search (first 30 results), upserts them keyed by "
                    + "GitHub's repository id and returns what was stored.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
            mediaType = "application/json",
            examples = @ExampleObject(value = "{\"query\":\"spring boot\"}")))
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Blank or too long query"),
            @ApiResponse(responseCode = "429",
                    description = "GitHub rate limit reached. The Retry-After header gives the seconds to wait"),
            @ApiResponse(responseCode = "502", description = "GitHub returned an error or was unreachable"),
            @ApiResponse(responseCode = "504", description = "GitHub did not answer in time")
    })
    @PostMapping("/search")
    public List<RepoView> search(@RequestBody GithubSearchRequest request) {
        return githubService.searchAndStore(request.getQuery());
    }

    @Operation(summary = "List stored repositories",
            description = "Filters by name, language and minimum stars, with sorting and page/size paging.")
    @ApiResponse(responseCode = "400",
            description = "Invalid page, size, minStars or sortBy (the message names the allowed values)")
    @GetMapping("/repositories")
    public PageResponse<RepoView> list(
            @Parameter(description = "Case-insensitive part of the repository name", example = "spring")
            @RequestParam(required = false) String nameFilter,
            @Parameter(description = "Language, exact match, case-insensitive", example = "Java")
            @RequestParam(required = false) String language,
            @Parameter(description = "Minimum star count", example = "1000")
            @RequestParam(required = false) Integer minStars,
            @Parameter(description = "name, language (ascending) or stars, forks, updated (descending)",
                    example = "stars")
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1 to 100")
            @RequestParam(defaultValue = "20") int size) {
        return githubService.list(nameFilter, language, minStars, sortBy, page, size);
    }
}
