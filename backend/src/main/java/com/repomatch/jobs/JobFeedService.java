package com.repomatch.jobs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.repomatch.github.GitHubClient;
import com.repomatch.github.GitHubException;

/** Keeps the job feed in step with the curated GitHub lists. */
@Service
public class JobFeedService {

    private static final Logger log = LoggerFactory.getLogger(JobFeedService.class);

    private final JobSourceRepository sources;
    private final JobPostingRepository postings;
    private final GitHubClient github;
    private final TransactionTemplate tx;

    public JobFeedService(JobSourceRepository sources, JobPostingRepository postings, GitHubClient github,
                          TransactionTemplate tx) {
        this.sources = sources;
        this.postings = postings;
        this.github = github;
        this.tx = tx;
    }

    public record SourceResult(String source, int listed, int added, int closed, String error) {
    }

    public record RefreshResult(Instant startedAt, List<SourceResult> sources, List<Long> newPostingIds) {
    }

    /** Re-reads every enabled list; returns the postings seen for the first time. */
    public RefreshResult refresh() {
        Instant started = Instant.now();
        List<SourceResult> results = new ArrayList<>();
        List<Long> fresh = new ArrayList<>();
        for (JobSourceEntity source : sources.findByEnabledTrueOrderById()) {
            results.add(refreshOne(source, started, fresh));
        }
        log.info("job feed refreshed: {} new postings from {} lists", fresh.size(), results.size());
        return new RefreshResult(started, results, fresh);
    }

    private SourceResult refreshOne(JobSourceEntity source, Instant now, List<Long> fresh) {
        String[] ownerRepo = source.getFullName().split("/", 2);
        List<JobListParser.Posting> listed;
        try {
            String markdown = github.getFileContent(ownerRepo[0], ownerRepo[1], source.getPath(), source.getBranch())
                    .orElseThrow(() -> new IllegalStateException(source.getPath() + " not found on " + source.getBranch()));
            listed = JobListParser.parse(markdown);
        } catch (GitHubException | IllegalStateException e) {
            tx.executeWithoutResult(s -> {
                source.setLastCheckedAt(now);
                source.setLastError(e.getMessage());
                sources.save(source);
            });
            return new SourceResult(source.getFullName(), 0, 0, 0, e.getMessage());
        }

        // Keep one row per URL even when a list repeats a job.
        Map<String, JobListParser.Posting> byUrl = listed.stream()
                .collect(Collectors.toMap(JobListParser.Posting::applyUrl, Function.identity(), (a, b) -> a,
                        java.util.LinkedHashMap::new));

        int[] counts = tx.execute(s -> {
            Map<String, JobPostingEntity> existing = new java.util.HashMap<>();
            List<String> urls = new ArrayList<>(byUrl.keySet());
            for (int i = 0; i < urls.size(); i += 500) {
                postings.findByApplyUrlIn(urls.subList(i, Math.min(urls.size(), i + 500)))
                        .forEach(p -> existing.put(p.getApplyUrl(), p));
            }

            int added = 0;
            List<JobPostingEntity> newRows = new ArrayList<>();
            for (JobListParser.Posting p : byUrl.values()) {
                JobPostingEntity row = existing.get(p.applyUrl());
                if (row == null) {
                    row = new JobPostingEntity();
                    row.setSourceId(source.getId());
                    row.setApplyUrl(p.applyUrl());
                    // The list knows how old a posting is; without this a first sync makes 2,000 jobs look "new today".
                    row.setFirstSeenAt(now.minus(ageOf(p.ageLabel())));
                    newRows.add(row);
                    added++;
                } else if (!row.getSourceId().equals(source.getId())) {
                    // Also listed elsewhere: the first list to find it owns it, but it's clearly still live.
                    row.setLastSeenAt(now);
                    continue;
                }
                row.setCompany(p.company());
                row.setRole(p.role());
                row.setLocation(p.location());
                row.setKind(JobKind.of(p.role(), source.getDefaultKind()));
                row.setClosed(p.closed());
                row.setAgeLabel(p.ageLabel());
                row.setLastSeenAt(now);
            }
            postings.saveAll(existing.values());
            boolean firstSync = source.getLastCheckedAt() == null;
            postings.saveAll(newRows).forEach(r -> {
                // A brand-new list's whole backlog isn't news; alert only on what appears after we started watching.
                if (!firstSync) {
                    fresh.add(r.getId());
                }
            });
            int closed = postings.closeMissing(source.getId(), now);

            source.setLastCheckedAt(now);
            source.setLastCount(byUrl.size());
            source.setLastError(null);
            sources.save(source);
            return new int[] {added, closed};
        });
        return new SourceResult(source.getFullName(), byUrl.size(), counts[0], counts[1], null);
    }

    /** One posting as the API shows it. */
    public record PostingView(long id, String company, String role, String location, JobKind kind, String applyUrl,
                              boolean closed, String ageLabel, Instant firstSeenAt, String source) {
    }

    public record Page(List<PostingView> items, int total, int offset, int limit) {
    }

    /** Filters the open feed. Matching is simple substring logic, the same rules a watch uses. */
    public Page search(String q, String location, JobKind kind, int sinceDays, int offset, int limit) {
        Map<Long, String> sourceNames = sources.findAll().stream()
                .collect(Collectors.toMap(JobSourceEntity::getId, JobSourceEntity::getFullName));
        Instant since = sinceDays > 0 ? Instant.now().minus(java.time.Duration.ofDays(sinceDays)) : Instant.EPOCH;
        List<String> terms = q == null ? List.of()
                : java.util.Arrays.stream(q.toLowerCase(Locale.ROOT).split("[,\\s]+")).filter(t -> !t.isBlank()).toList();
        String where = location == null ? "" : location.toLowerCase(Locale.ROOT).strip();

        List<JobPostingEntity> matching = postings.findOpen().stream()
                .filter(p -> kind == null || p.getKind() == kind)
                .filter(p -> !p.getFirstSeenAt().isBefore(since))
                .filter(p -> {
                    String hay = (p.getRole() + " " + p.getCompany()).toLowerCase(Locale.ROOT);
                    return terms.stream().allMatch(hay::contains);
                })
                .filter(p -> where.isEmpty() || (p.getLocation() != null && p.getLocation().toLowerCase(Locale.ROOT).contains(where)))
                .toList();

        List<PostingView> page = matching.stream().skip(offset).limit(limit)
                .map(p -> view(p, sourceNames.get(p.getSourceId())))
                .toList();
        return new Page(page, matching.size(), offset, limit);
    }

    public PostingView get(long id) {
        JobPostingEntity p = postings.findById(id).orElseThrow(() -> new PostingNotFoundException(id));
        return view(p, sources.findById(p.getSourceId()).map(JobSourceEntity::getFullName).orElse(null));
    }

    public List<JobSourceEntity> sources() {
        return sources.findAll(org.springframework.data.domain.Sort.by("id"));
    }

    /** "0d", "12d", "2mo" → a duration; anything unreadable counts as today. */
    static java.time.Duration ageOf(String label) {
        if (label == null) {
            return java.time.Duration.ZERO;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*(mo|d|w|h)").matcher(label.toLowerCase(Locale.ROOT));
        if (!m.find()) {
            return java.time.Duration.ZERO;
        }
        long n = Long.parseLong(m.group(1));
        return switch (m.group(2)) {
            case "mo" -> java.time.Duration.ofDays(n * 30);
            case "w" -> java.time.Duration.ofDays(n * 7);
            case "h" -> java.time.Duration.ofHours(n);
            default -> java.time.Duration.ofDays(n);
        };
    }

    static PostingView view(JobPostingEntity p, String source) {
        return new PostingView(p.getId(), p.getCompany(), p.getRole(), p.getLocation(), p.getKind(), p.getApplyUrl(),
                p.isClosed(), p.getAgeLabel(), p.getFirstSeenAt(), source);
    }

    public static class PostingNotFoundException extends RuntimeException {
        public PostingNotFoundException(long id) {
            super("Job posting " + id + " doesn't exist");
        }
    }
}
