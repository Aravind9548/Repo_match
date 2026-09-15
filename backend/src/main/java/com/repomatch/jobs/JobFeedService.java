package com.repomatch.jobs;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.repomatch.github.GitHubClient;
import com.repomatch.github.GitHubException;

/** Keeps the job feed in step with GitHub lists and company job boards, and searches it. */
@Service
public class JobFeedService {

    private static final Logger log = LoggerFactory.getLogger(JobFeedService.class);

    /** Parallel fetches: polite to the job boards, still ~50 sources in well under a minute. */
    static final int PARALLEL_FETCHES = 8;
    static final int WRITE_BATCH = 500;

    private final JobSourceRepository sources;
    private final JobPostingRepository postings;
    private final GitHubClient github;
    private final BoardFetcher boards;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JobFeedService(JobSourceRepository sources, JobPostingRepository postings, GitHubClient github,
                          BoardFetcher boards, JdbcTemplate jdbc, TransactionTemplate tx) {
        this.sources = sources;
        this.postings = postings;
        this.github = github;
        this.boards = boards;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    public record SourceResult(String source, int listed, int added, int closed, String error) {
    }

    public record RefreshResult(Instant startedAt, long millis, int totalOpen, List<SourceResult> sources,
                                List<Long> newPostingIds) {
    }

    // --- refresh -------------------------------------------------------------------------------------------------

    /** Re-reads every enabled source; returns the postings seen for the first time. */
    public RefreshResult refresh() {
        Instant started = Instant.now();
        // Google discovery has its own daily schedule and cost cap (GoogleDiscoveryService).
        List<JobSourceEntity> enabled = sources.findByEnabledTrueOrderById().stream()
                .filter(s -> s.getType() != SourceType.GOOGLE_SEARCH)
                .toList();

        // Fetch in parallel (network-bound), write one source at a time (the database is the bottleneck).
        Map<JobSourceEntity, Future<List<FetchedPosting>>> fetches = new LinkedHashMap<>();
        Semaphore permits = new Semaphore(PARALLEL_FETCHES);
        List<SourceResult> results = new ArrayList<>();
        List<Long> fresh = Collections.synchronizedList(new ArrayList<>());
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (JobSourceEntity s : enabled) {
                fetches.put(s, pool.submit(() -> {
                    permits.acquire();
                    try {
                        return fetch(s);
                    } finally {
                        permits.release();
                    }
                }));
            }
            for (Map.Entry<JobSourceEntity, Future<List<FetchedPosting>>> e : fetches.entrySet()) {
                JobSourceEntity source = e.getKey();
                try {
                    List<FetchedPosting> listed = e.getValue().get();
                    results.add(store(source, listed, started, fresh));
                } catch (Exception ex) {
                    Throwable cause = ex instanceof java.util.concurrent.ExecutionException ? ex.getCause() : ex;
                    String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                    log.warn("job source {} failed: {}", source.getFullName(), message);
                    tx.executeWithoutResult(t -> jdbc.update(
                            "update job_source set last_checked_at = ?, last_error = ? where id = ?",
                            Timestamp.from(started), clip(message, 1000), source.getId()));
                    results.add(new SourceResult(source.getFullName(), 0, 0, 0, message));
                }
            }
        }
        Integer open = jdbc.queryForObject("select count(*) from job_posting where not closed", Integer.class);
        long millis = Duration.between(started, Instant.now()).toMillis();
        log.info("job feed refreshed in {} ms: {} new, {} open, {} sources", millis, fresh.size(), open, results.size());
        return new RefreshResult(started, millis, open == null ? 0 : open, results, List.copyOf(fresh));
    }

    private List<FetchedPosting> fetch(JobSourceEntity source) {
        if (source.getType() != SourceType.GITHUB_LIST) {
            return boards.fetch(source);
        }
        String[] ownerRepo = source.getFullName().split("/", 2);
        String markdown;
        try {
            markdown = github.getFileContent(ownerRepo[0], ownerRepo[1], source.getPath(), source.getBranch())
                    .orElseThrow(() -> new IllegalStateException(source.getPath() + " not found on " + source.getBranch()));
        } catch (GitHubException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        Instant now = Instant.now();
        return JobListParser.parse(markdown).stream().map(p -> {
            Places.Place place = Places.of(p.location());
            // Lists give either a date ("12 Sept 2026") or an age ("3d").
            Instant date = JobListParser.dateOf(p.ageLabel());
            return new FetchedPosting(p.company(), p.role(), p.location(), place.country(), place.remote(), p.applyUrl(),
                    p.closed(), p.ageLabel(), date != null ? date : now.minus(ageOf(p.ageLabel())), false);
        }).toList();
    }

    /** How long a Google-found posting survives without Google finding it again (results vary day to day). */
    static final Duration SEARCH_RESULT_TTL = Duration.ofDays(21);

    /** Upserts one source's postings in bulk and closes the ones it no longer lists. */
    SourceResult store(JobSourceEntity source, List<FetchedPosting> listed, Instant now, List<Long> fresh) {
        boolean firstSync = source.getLastCheckedAt() == null;
        Map<String, FetchedPosting> byUrl = new LinkedHashMap<>();
        for (FetchedPosting p : listed) {
            if (p.applyUrl() != null && !p.applyUrl().isBlank() && !p.role().isBlank()) {
                byUrl.putIfAbsent(canonicalUrl(p.applyUrl()), p);
            }
        }
        Timestamp at = Timestamp.from(now);

        return tx.execute(t -> {
            List<Object[]> rows = new ArrayList<>(byUrl.size());
            for (Map.Entry<String, FetchedPosting> e : byUrl.entrySet()) {
                FetchedPosting p = e.getValue();
                Places.Place place = Places.of(p.location());
                String country = p.country() != null ? p.country() : place.country();
                JobKind kind = p.intern() ? JobKind.INTERNSHIP : JobKind.of(p.role(), source.getDefaultKind());
                rows.add(new Object[] {source.getId(), clip(p.company(), 300), clip(p.role(), 500), clip(p.location(), 500),
                        kind.name(), e.getKey(), p.closed(), clip(p.ageLabel(), 20), at, at, country, p.remote() || place.remote(),
                        p.postedAt() == null ? null : Timestamp.from(p.postedAt()),
                        SearchText.build(p.company(), p.role(), p.location(), place)});
            }
            // One statement per row, batched: new rows are inserted, known rows refreshed. A job another source
            // found first keeps its owner; seeing it here still proves it's live.
            for (int i = 0; i < rows.size(); i += WRITE_BATCH) {
                jdbc.batchUpdate("""
                        insert into job_posting (source_id, company, role, location, kind, apply_url, closed, age_label,
                                                 first_seen_at, last_seen_at, country, remote, posted_at, search_text)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (apply_url) do update set
                            last_seen_at = excluded.last_seen_at,
                            company = case when job_posting.source_id = excluded.source_id then excluded.company else job_posting.company end,
                            role = case when job_posting.source_id = excluded.source_id then excluded.role else job_posting.role end,
                            location = case when job_posting.source_id = excluded.source_id then excluded.location else job_posting.location end,
                            kind = case when job_posting.source_id = excluded.source_id then excluded.kind else job_posting.kind end,
                            closed = case when job_posting.source_id = excluded.source_id then excluded.closed else job_posting.closed end,
                            age_label = case when job_posting.source_id = excluded.source_id then excluded.age_label else job_posting.age_label end,
                            country = coalesce(job_posting.country, excluded.country),
                            remote = job_posting.remote or excluded.remote,
                            posted_at = coalesce(job_posting.posted_at, excluded.posted_at),
                            search_text = case when job_posting.source_id = excluded.source_id then excluded.search_text else job_posting.search_text end
                        """, rows.subList(i, Math.min(rows.size(), i + WRITE_BATCH)));
            }

            // Rows created in this run carry this run's timestamp as first_seen_at.
            List<Long> added = jdbc.queryForList(
                    "select id from job_posting where source_id = ? and first_seen_at = ?", Long.class, source.getId(), at);
            // A board or list that stops showing a job means it's gone. Search results are a sample, so those
            // only expire after going unseen for a while.
            Timestamp closeBefore = source.getType() == SourceType.GOOGLE_SEARCH ? Timestamp.from(now.minus(SEARCH_RESULT_TTL)) : at;
            int closed = jdbc.update(
                    "update job_posting set closed = true where source_id = ? and last_seen_at < ? and not closed",
                    source.getId(), closeBefore);
            jdbc.update("update job_source set last_checked_at = ?, last_count = ?, last_error = null where id = ?",
                    at, byUrl.size(), source.getId());
            // A new source's backlog isn't news; alert only on what appears after we started watching it.
            if (!firstSync) {
                fresh.addAll(added);
            }
            return new SourceResult(source.getFullName(), byUrl.size(), added.size(), closed, null);
        });
    }

    /** Same job, different URL spellings: tracking params, regional hosts, a trailing /apply. */
    static String canonicalUrl(String url) {
        String u = JobListParser.normalizeUrl(url);
        u = u.replaceFirst("^http://", "https://")
                .replaceFirst("://(boards|job-boards)(\\.eu)?\\.greenhouse\\.io/", "://job-boards.greenhouse.io/")
                .replaceFirst("(jobs\\.lever\\.co/[^/?]+/[0-9a-f-]{36})/apply", "$1")
                .replaceFirst("(jobs\\.ashbyhq\\.com/[^/?]+/[0-9a-f-]{36})/application", "$1")
                .replaceFirst("/+$", "");
        return clip(u, 2000);
    }

    // --- search --------------------------------------------------------------------------------------------------

    /** One posting as the API shows it. */
    public record PostingView(long id, String company, String role, String location, JobKind kind, String applyUrl,
                              boolean closed, String ageLabel, Instant firstSeenAt, Instant postedAt, String country,
                              boolean remote, String source, SourceType sourceType) {
    }

    public record Facets(Map<String, Integer> kinds, Map<String, Integer> countries, int remote) {
    }

    public record Page(List<PostingView> items, int total, int offset, int limit, String understood, Facets facets) {
    }

    private record Where(String sql, List<Object> params) {
    }

    /**
     * Searches the open feed in the database. The query is parsed first ("indian internship" → country and kind),
     * then each remaining concept must match as a whole word; postings whose title matches rank above those that
     * only match on company or location.
     */
    public Page search(String q, String location, JobKind kindFilter, int sinceDays, int offset, int limit) {
        JobQuery query = JobQuery.parse(q == null ? "" : q, location);
        JobKind kind = kindFilter != null ? kindFilter : query.kind();

        Where base = where(query, sinceDays, false);
        Where full = where(query, sinceDays, true);
        List<Object> fullParams = new ArrayList<>(full.params());
        String kindClause = "";
        if (kind != null) {
            kindClause = " and p.kind = ?";
            fullParams.add(kind.name());
        }

        StringBuilder score = new StringBuilder("0");
        List<Object> scoreParams = new ArrayList<>();
        for (var concept : query.concepts()) {
            score.append(" + case when lower(p.role) ~ ? then 2 else 0 end");
            scoreParams.add(SearchText.regex(concept));
        }

        Integer total = jdbc.queryForObject("select count(*) from job_posting p where " + full.sql() + kindClause,
                Integer.class, fullParams.toArray());

        List<Object> pageParams = new ArrayList<>(scoreParams);
        pageParams.addAll(fullParams);
        pageParams.add(limit);
        pageParams.add(offset);
        List<PostingView> items = jdbc.query("""
                select p.*, s.full_name, s.type as source_type, (%s) as score
                from job_posting p join job_source s on s.id = p.source_id
                where %s%s
                order by score desc, coalesce(p.posted_at, p.first_seen_at) desc, p.id desc
                limit ? offset ?
                """.formatted(score, full.sql(), kindClause), (rs, i) -> new PostingView(
                rs.getLong("id"), rs.getString("company"), rs.getString("role"), rs.getString("location"),
                JobKind.valueOf(rs.getString("kind")), rs.getString("apply_url"), rs.getBoolean("closed"),
                rs.getString("age_label"), rs.getTimestamp("first_seen_at").toInstant(),
                rs.getTimestamp("posted_at") == null ? null : rs.getTimestamp("posted_at").toInstant(),
                rs.getString("country"), rs.getBoolean("remote"), rs.getString("full_name"),
                SourceType.valueOf(rs.getString("source_type"))), pageParams.toArray());

        return new Page(items, total == null ? 0 : total, offset, limit, describe(query, kind), facets(base));
    }

    /** Counts per kind and country for the same search, so an empty result can say where the jobs are. */
    private Facets facets(Where w) {
        Map<String, Integer> kinds = new LinkedHashMap<>();
        jdbc.query("select p.kind, count(*) from job_posting p where " + w.sql() + " group by p.kind order by 2 desc",
                rs -> {
                    kinds.put(rs.getString(1), rs.getInt(2));
                }, w.params().toArray());
        Map<String, Integer> countries = new LinkedHashMap<>();
        jdbc.query("""
                select p.country, count(*) from job_posting p where %s and p.country is not null
                group by p.country order by 2 desc limit 8""".formatted(w.sql()),
                rs -> {
                    countries.put(rs.getString(1), rs.getInt(2));
                }, w.params().toArray());
        Integer remote = jdbc.queryForObject("select count(*) from job_posting p where " + w.sql() + " and p.remote",
                Integer.class, w.params().toArray());
        return new Facets(kinds, countries, remote == null ? 0 : remote);
    }

    /** @param withPlace false for facets, which show the spread across countries for the rest of the query */
    private Where where(JobQuery query, int sinceDays, boolean withPlace) {
        StringBuilder sql = new StringBuilder("not p.closed");
        List<Object> params = new ArrayList<>();
        if (sinceDays > 0) {
            sql.append(" and coalesce(p.posted_at, p.first_seen_at) >= ?");
            params.add(Timestamp.from(Instant.now().minus(Duration.ofDays(sinceDays))));
        }
        for (var concept : query.concepts()) {
            sql.append(" and p.search_text ~ ?");
            params.add(SearchText.regex(concept));
        }
        if (withPlace) {
            if (query.country() != null) {
                sql.append(" and p.country = ?");
                params.add(query.country());
            }
            if (!query.cityAliases().isEmpty()) {
                sql.append(" and p.search_text ~ ?");
                params.add(SearchText.regex(query.cityAliases()));
            }
            if (query.remote()) {
                sql.append(" and p.remote");
            }
        }
        return new Where(sql.toString(), params);
    }

    private static String describe(JobQuery query, JobKind kind) {
        JobQuery shown = new JobQuery(kind, query.country(), query.remote(), query.cityAliases(), query.concepts());
        return shown.describe();
    }

    public PostingView get(long id) {
        List<PostingView> rows = jdbc.query("""
                select p.*, s.full_name, s.type as source_type from job_posting p join job_source s on s.id = p.source_id
                where p.id = ?""", (rs, i) -> new PostingView(
                rs.getLong("id"), rs.getString("company"), rs.getString("role"), rs.getString("location"),
                JobKind.valueOf(rs.getString("kind")), rs.getString("apply_url"), rs.getBoolean("closed"),
                rs.getString("age_label"), rs.getTimestamp("first_seen_at").toInstant(),
                rs.getTimestamp("posted_at") == null ? null : rs.getTimestamp("posted_at").toInstant(),
                rs.getString("country"), rs.getBoolean("remote"), rs.getString("full_name"),
                SourceType.valueOf(rs.getString("source_type"))), id);
        if (rows.isEmpty()) {
            throw new PostingNotFoundException(id);
        }
        return rows.getFirst();
    }

    public List<JobSourceEntity> sources() {
        return sources.findAll(org.springframework.data.domain.Sort.by("id"));
    }

    /** "0d", "12d", "2mo" → a duration; anything unreadable counts as today. */
    static Duration ageOf(String label) {
        if (label == null) {
            return Duration.ZERO;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*(mo|d|w|h)").matcher(label.toLowerCase(Locale.ROOT));
        if (!m.find()) {
            return Duration.ZERO;
        }
        long n = Long.parseLong(m.group(1));
        return switch (m.group(2)) {
            case "mo" -> Duration.ofDays(n * 30);
            case "w" -> Duration.ofDays(n * 7);
            case "h" -> Duration.ofHours(n);
            default -> Duration.ofDays(n);
        };
    }

    static PostingView view(JobPostingEntity p, String source, SourceType type) {
        return new PostingView(p.getId(), p.getCompany(), p.getRole(), p.getLocation(), p.getKind(), p.getApplyUrl(),
                p.isClosed(), p.getAgeLabel(), p.getFirstSeenAt(), p.getPostedAt(), p.getCountry(), p.isRemote(), source,
                type);
    }

    private static String clip(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    public static class PostingNotFoundException extends RuntimeException {
        public PostingNotFoundException(long id) {
            super("Job posting " + id + " doesn't exist");
        }
    }
}
