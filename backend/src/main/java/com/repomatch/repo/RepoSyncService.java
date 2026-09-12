package com.repomatch.repo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.repomatch.config.AppProperties;
import com.repomatch.github.GitHubClient;
import com.repomatch.github.dto.GitHubRepo;
import com.repomatch.github.dto.GitHubUser;

/**
 * Keeps Postgres in sync with a user's public GitHub repos.
 *
 * <p>Two cache layers keep GitHub API usage low: a whole-account TTL (skip GitHub entirely
 * for recently synced users), and per-repo {@code pushed_at} checks (only re-download the
 * README and language stats of repos that actually changed).
 */
@Service
public class RepoSyncService {

    private static final Logger log = LoggerFactory.getLogger(RepoSyncService.class);

    private final GitHubClient gitHub;
    private final GithubAccountRepository accounts;
    private final RepoRepository repos;
    private final AppProperties.GitHub config;

    public RepoSyncService(GitHubClient gitHub, GithubAccountRepository accounts, RepoRepository repos,
                           AppProperties props) {
        this.gitHub = gitHub;
        this.accounts = accounts;
        this.repos = repos;
        this.config = props.github();
    }

    public record SyncResult(GithubAccount account, List<RepoEntity> repos, boolean fromCache) {
    }

    public SyncResult sync(String username, boolean forceRefresh) {
        String login = username.trim();
        String key = login.toLowerCase(Locale.ROOT);

        var cached = accounts.findById(key);
        if (!forceRefresh && cached.isPresent()
                && cached.get().getSyncedAt().isAfter(Instant.now().minus(config.cacheTtl()))) {
            log.debug("Serving {} from cache", key);
            return new SyncResult(cached.get(), repos.findByOwnerLoginOrderByPushedAtDesc(key), true);
        }

        GitHubUser user = gitHub.getUser(login);
        List<GitHubRepo> remote = gitHub.listRepos(user.login());

        GithubAccount account = cached.orElseGet(GithubAccount::new);
        account.setLogin(key);
        account.setDisplayLogin(user.login());
        account.setName(user.name());
        account.setAvatarUrl(user.avatarUrl());
        account.setHtmlUrl(user.htmlUrl());
        account.setPublicRepos(user.publicRepos());
        account.setSyncedAt(Instant.now());
        accounts.save(account);

        Map<Long, RepoEntity> existing = repos.findByOwnerLoginOrderByPushedAtDesc(key).stream()
                .collect(Collectors.toMap(RepoEntity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<RepoEntity> updated = refreshRepos(remote, existing, key, forceRefresh);

        Set<Long> remoteIds = remote.stream().map(GitHubRepo::id).collect(Collectors.toSet());
        List<RepoEntity> deleted = existing.values().stream().filter(r -> !remoteIds.contains(r.getId())).toList();
        repos.deleteAll(deleted);
        List<RepoEntity> saved = new ArrayList<>(repos.saveAll(updated));
        saved.sort(Comparator.comparing(RepoEntity::getPushedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        log.info("Synced {}: {} repos ({} removed)", key, saved.size(), deleted.size());
        return new SyncResult(account, saved, false);
    }

    private List<RepoEntity> refreshRepos(List<GitHubRepo> remote, Map<Long, RepoEntity> existing,
                                          String ownerKey, boolean forceRefresh) {
        Semaphore permits = new Semaphore(config.maxParallelRequests());
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<RepoEntity>> futures = remote.stream()
                    .map(r -> exec.submit(() -> refreshOne(r, existing.get(r.id()), ownerKey, forceRefresh, permits)))
                    .toList();
            List<RepoEntity> result = new ArrayList<>(futures.size());
            for (Future<RepoEntity> f : futures) {
                result.add(f.get());
            }
            return result;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while syncing repositories", e);
        }
    }

    private RepoEntity refreshOne(GitHubRepo r, RepoEntity prev, String ownerKey, boolean forceRefresh,
                                  Semaphore permits) throws InterruptedException {
        boolean unchanged = prev != null && Objects.equals(prev.getPushedAt(), r.pushedAt()) && prev.isFork() == r.fork();

        String readme;
        Map<String, Long> languages;
        if (r.fork()) {
            // Forks are mostly someone else's code: keep metadata only, don't spend API calls on them.
            readme = null;
            languages = r.language() != null ? Map.of(r.language(), 1L) : Map.of();
        } else if (unchanged && !forceRefresh) {
            readme = prev.getReadme();
            languages = prev.getLanguages();
        } else {
            permits.acquire();
            try {
                String owner = r.owner().login();
                readme = gitHub.getReadme(owner, r.name())
                        .map(md -> ReadmeCleaner.clean(md, config.maxReadmeChars()))
                        .orElse(null);
                languages = gitHub.getLanguages(owner, r.name());
            } finally {
                permits.release();
            }
        }

        RepoEntity e = prev != null ? prev : new RepoEntity();
        e.setId(r.id());
        e.setOwnerLogin(ownerKey);
        e.setName(r.name());
        e.setFullName(r.fullName());
        e.setDescription(r.description());
        e.setHtmlUrl(r.htmlUrl());
        e.setHomepage(r.homepage() == null || r.homepage().isBlank() ? null : r.homepage());
        e.setPrimaryLanguage(r.language());
        e.setLanguages(new LinkedHashMap<>(languages));
        e.setTopics(r.topics() == null ? new ArrayList<>() : new ArrayList<>(r.topics()));
        e.setStars(r.stargazersCount());
        e.setFork(r.fork());
        e.setArchived(r.archived());
        e.setDefaultBranch(r.defaultBranch());
        e.setPushedAt(r.pushedAt());
        e.setReadme(readme);
        e.setProfileText(ProfileBuilder.build(e));
        e.setProfileHash(ProfileBuilder.sha256(e.getProfileText()));
        e.setFetchedAt(Instant.now());
        return e;
    }
}
