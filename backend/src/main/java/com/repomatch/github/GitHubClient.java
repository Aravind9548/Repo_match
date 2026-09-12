package com.repomatch.github;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.repomatch.github.dto.GitHubRepo;
import com.repomatch.github.dto.GitHubTree;
import com.repomatch.github.dto.GitHubUser;

/** Thin wrapper over the GitHub REST API: users, repos, READMEs and language breakdowns. */
@Component
public class GitHubClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);
    private static final int PAGE_SIZE = 100;

    private final RestClient http;

    public GitHubClient(@Qualifier("gitHubRestClient") RestClient http) {
        this.http = http;
    }

    public GitHubUser getUser(String login) {
        return call(() -> http.get().uri("/users/{login}", login)
                .retrieve()
                .onStatus(s -> s.value() == 404, (req, res) -> {
                    throw new GitHubUserNotFoundException(login);
                })
                .onStatus(s -> s.isError(), (req, res) -> throwForStatus(res))
                .body(GitHubUser.class));
    }

    public List<GitHubRepo> listRepos(String login) {
        List<GitHubRepo> all = new ArrayList<>();
        for (int page = 1; ; page++) {
            int p = page;
            List<GitHubRepo> batch = call(() -> http.get()
                    .uri("/users/{login}/repos?type=owner&sort=pushed&per_page={size}&page={page}", login, PAGE_SIZE, p)
                    .retrieve()
                    .onStatus(s -> s.value() == 404, (req, res) -> {
                        throw new GitHubUserNotFoundException(login);
                    })
                    .onStatus(s -> s.isError(), (req, res) -> throwForStatus(res))
                    .body(new ParameterizedTypeReference<List<GitHubRepo>>() {}));
            if (batch == null || batch.isEmpty()) {
                break;
            }
            all.addAll(batch);
            if (batch.size() < PAGE_SIZE) {
                break;
            }
        }
        return all;
    }

    /** Raw README markdown, or empty when the repo has none. */
    public Optional<String> getReadme(String owner, String repo) {
        return Optional.ofNullable(call(() -> http.get().uri("/repos/{owner}/{repo}/readme", owner, repo)
                .header(HttpHeaders.ACCEPT, "application/vnd.github.raw+json")
                .exchange((req, res) -> {
                    if (res.getStatusCode().value() == 404) {
                        return null;
                    }
                    if (res.getStatusCode().isError()) {
                        throwForStatus(res);
                    }
                    return new String(res.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                })));
    }

    /** Bytes of code per language, e.g. {"Java": 51234, "Dockerfile": 312}. */
    public Map<String, Long> getLanguages(String owner, String repo) {
        Map<String, Long> langs = call(() -> http.get().uri("/repos/{owner}/{repo}/languages", owner, repo)
                .exchange((req, res) -> {
                    if (res.getStatusCode().value() == 404) {
                        return null;
                    }
                    if (res.getStatusCode().isError()) {
                        throwForStatus(res);
                    }
                    return res.bodyTo(new ParameterizedTypeReference<Map<String, Long>>() {});
                }));
        return langs == null ? Map.of() : langs;
    }

    /** Every file in a branch, flattened. Truncated for very large repos (GitHub's own limit). */
    public GitHubTree getTree(String owner, String repo, String branch) {
        GitHubTree tree = call(() -> http.get()
                .uri("/repos/{owner}/{repo}/git/trees/{branch}?recursive=1", owner, repo, branch)
                .exchange((req, res) -> {
                    if (res.getStatusCode().value() == 404 || res.getStatusCode().value() == 409) {
                        return null; // unknown branch, or an empty repository
                    }
                    if (res.getStatusCode().isError()) {
                        throwForStatus(res);
                    }
                    return res.bodyTo(GitHubTree.class);
                }));
        return tree == null ? new GitHubTree(null, List.of(), false) : tree;
    }

    /** Raw text of one file, or empty when it's missing or not text. */
    public Optional<String> getFileContent(String owner, String repo, String path, String ref) {
        return Optional.ofNullable(call(() -> http.get()
                .uri("/repos/{owner}/{repo}/contents/{path}?ref={ref}", owner, repo, path, ref)
                .header(HttpHeaders.ACCEPT, "application/vnd.github.raw+json")
                .exchange((req, res) -> {
                    if (res.getStatusCode().value() == 404 || res.getStatusCode().value() == 403) {
                        return null;
                    }
                    if (res.getStatusCode().isError()) {
                        throwForStatus(res);
                    }
                    return new String(res.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                })));
    }

    private static void throwForStatus(ClientHttpResponse res) throws java.io.IOException {
        HttpHeaders h = res.getHeaders();
        int status = res.getStatusCode().value();
        boolean rateLimited = status == HttpStatus.TOO_MANY_REQUESTS.value()
                || (status == HttpStatus.FORBIDDEN.value() && "0".equals(h.getFirst("X-RateLimit-Remaining")));
        if (rateLimited) {
            String reset = h.getFirst("X-RateLimit-Reset");
            Instant resetAt = reset != null ? Instant.ofEpochSecond(Long.parseLong(reset)) : null;
            throw new GitHubRateLimitException(resetAt);
        }
        throw new GitHubApiException("GitHub API returned HTTP " + status);
    }

    private static <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (GitHubException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("GitHub request failed: {}", e.getMessage());
            throw new GitHubApiException("Could not reach GitHub: " + e.getMessage());
        }
    }
}
