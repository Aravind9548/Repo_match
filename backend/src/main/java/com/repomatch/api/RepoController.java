package com.repomatch.api;

import java.time.Instant;
import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.repomatch.repo.RepoEntity;
import com.repomatch.repo.RepoSyncService;

import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api/repos")
@Validated
public class RepoController {

    private final RepoSyncService sync;

    public RepoController(RepoSyncService sync) {
        this.sync = sync;
    }

    public record RepoSummary(long id, String name, String url, String description, List<String> languages,
                              List<String> topics, int stars, boolean fork, boolean archived, Instant pushedAt,
                              int readmeChars, int profileChars, boolean embedded) {

        static RepoSummary of(RepoEntity r) {
            return new RepoSummary(r.getId(), r.getName(), r.getHtmlUrl(), r.getDescription(), r.languageNames(),
                    r.getTopics(), r.getStars(), r.isFork(), r.isArchived(), r.getPushedAt(),
                    r.getReadme() == null ? 0 : r.getReadme().length(), r.getProfileText().length(),
                    r.hasFreshEmbedding());
        }
    }

    public record RepoListResponse(AccountSummary account, boolean fromCache, List<RepoSummary> repos) {
    }

    @GetMapping("/{username}")
    public RepoListResponse list(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$", message = "must be a valid GitHub username")
            String username,
            @RequestParam(defaultValue = "false") boolean refresh) {
        RepoSyncService.SyncResult result = sync.sync(username, refresh);
        return new RepoListResponse(AccountSummary.of(result.account()), result.fromCache(),
                result.repos().stream().map(RepoSummary::of).toList());
    }
}
