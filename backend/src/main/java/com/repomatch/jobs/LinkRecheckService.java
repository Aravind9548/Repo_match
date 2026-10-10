package com.repomatch.jobs;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Opens postings again to find the ones that have died since they were listed.
 *
 * <p>A board stops listing a role and the next refresh closes it, but a posting that lives on a Google Form or a
 * company's own page is never listed anywhere again — it just quietly stops accepting applications while still
 * answering 200 OK. Nothing closes those, so someone tailors a resume for a job that ended weeks ago.
 *
 * <p>So the oldest-checked postings are opened again on every scheduled run, in parallel and capped, and the dead
 * ones are closed. A page that merely refuses robots is left alone: being blocked is not being gone.
 */
@Service
public class LinkRecheckService {

    private static final Logger log = LoggerFactory.getLogger(LinkRecheckService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_PAGE_BYTES = 400_000;

    private final JdbcTemplate jdbc;
    private final int perRun;
    private final Duration interval;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(TIMEOUT)
            .build();

    public LinkRecheckService(JdbcTemplate jdbc,
                              @Value("${repomatch.recheck.per-run:300}") int perRun,
                              @Value("${repomatch.recheck.interval-days:7}") int intervalDays) {
        this.jdbc = jdbc;
        this.perRun = perRun;
        this.interval = Duration.ofDays(intervalDays);
    }

    public record Run(int checked, int closed) {
    }

    /** Re-opens the postings checked longest ago; closes the ones that are gone or no longer taking applications. */
    public Run recheck() {
        Instant due = Instant.now().minus(interval);
        // Board postings de-list themselves: the next refresh stops seeing them and closes them. These two don't —
        // a list's link and a search result keep working long after the role is over — so only they are swept.
        List<Posting> batch = jdbc.query("""
                        select p.id, p.apply_url from job_posting p
                        join job_source s on s.id = p.source_id
                        where not p.closed and s.type in ('GITHUB_LIST', 'GOOGLE_SEARCH')
                          and (p.last_checked_at is null or p.last_checked_at < ?)
                        order by p.last_checked_at nulls first, p.id
                        limit ?""",
                (rs, i) -> new Posting(rs.getLong("id"), rs.getString("apply_url")),
                java.sql.Timestamp.from(due), perRun);
        if (batch.isEmpty()) {
            return new Run(0, 0);
        }

        AtomicInteger closed = new AtomicInteger();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<? extends Future<?>> futures = batch.stream().map(p -> pool.submit(() -> {
                if (isGone(p.applyUrl())) {
                    closed.incrementAndGet();
                    jdbc.update("update job_posting set closed = true, last_checked_at = now() where id = ?", p.id());
                } else {
                    jdbc.update("update job_posting set last_checked_at = now() where id = ?", p.id());
                }
            })).toList();
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    // one unreachable link mustn't stop the sweep; it keeps its old last_checked_at and comes round again
                    log.debug("recheck failed: {}", e.getMessage());
                }
            }
        }
        log.info("link recheck: {} opened again, {} closed", batch.size(), closed.get());
        return new Run(batch.size(), closed.get());
    }

    /** True only when the page says it is gone. Anything uncertain — blocked, timed out, moved — is left open. */
    private boolean isGone(String url) {
        if (url == null || !url.startsWith("http")) {
            return false;
        }
        try {
            HttpResponse<java.io.InputStream> r = http.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(TIMEOUT)
                            .header("User-Agent", "Mozilla/5.0 (compatible; RepoMatch link check)")
                            .header("Accept", "text/html,application/xhtml+xml")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            try (java.io.InputStream body = r.body()) {
                if (r.statusCode() == 404 || r.statusCode() == 410) {
                    return true;
                }
                if (r.statusCode() >= 400) {
                    return false; // 401/403/429/999: the site refuses us, not the applicant
                }
                String html = new String(body.readNBytes(MAX_PAGE_BYTES), java.nio.charset.StandardCharsets.UTF_8);
                return PostingCheck.looksClosed(html);
            }
        } catch (Exception e) {
            return false; // unreachable today is not the same as gone
        }
    }

    private record Posting(long id, String applyUrl) {
    }
}
