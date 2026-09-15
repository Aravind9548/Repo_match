package com.repomatch.jobs;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.repomatch.ai.AiServiceClient;
import com.repomatch.ai.dto.WebSearchRequest;
import com.repomatch.ai.dto.WebSearchResponse;
import com.repomatch.analysis.UsageGuard;

/**
 * "Search the web": Gemini grounded in Google Search finds postings outside the synced boards, then every link is
 * opened here before it's shown. Google's grounding links are short-lived redirects, so each is resolved to the
 * real page; links that don't resolve are dropped, and pages that refuse automated visits are flagged, not hidden.
 */
@Service
public class WebJobSearchService {

    private static final Logger log = LoggerFactory.getLogger(WebJobSearchService.class);
    static final int LIMIT = 12;
    private static final Duration LINK_TIMEOUT = Duration.ofSeconds(10);

    private final AiServiceClient ai;
    private final UsageGuard usage;
    private final JdbcTemplate jdbc;
    private final HttpClient noRedirects = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(LINK_TIMEOUT).build();
    private final HttpClient followRedirects = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(LINK_TIMEOUT).build();

    public WebJobSearchService(AiServiceClient ai, UsageGuard usage, JdbcTemplate jdbc) {
        this.ai = ai;
        this.usage = usage;
        this.jdbc = jdbc;
    }

    /**
     * @param status      "open" (page loaded), "blocked" (site refuses bots — open it yourself), or "gone"
     * @param postingId   set when the same job is already in the feed, so it can be approved from there
     */
    public record WebJob(String title, String company, String location, String posted, String url, String status,
                         Long postingId) {
    }

    public record Result(String query, List<WebJob> jobs, int dropped, List<String> searchQueries, String model) {
    }

    public Result search(String q, String location, JobKind kind, String clientKey) {
        String query = phrase(q, location, kind);
        usage.acquireLlmCall(clientKey);
        WebSearchResponse res = ai.webSearch(new WebSearchRequest(query, LIMIT));
        List<WebSearchResponse.Job> found = res.jobs() == null ? List.of() : res.jobs();

        List<WebJob> checked = verifyAll(found);
        int dropped = found.size() - checked.size();
        log.info("web job search '{}': {} found, {} kept, {} dropped", query, found.size(), checked.size(), dropped);
        return new Result(query, checked, dropped, res.searchQueries() == null ? List.of() : res.searchQueries(), res.model());
    }

    /** The search phrase Google gets: the person's words plus the filters they set, in plain language. */
    static String phrase(String q, String location, JobKind kind) {
        StringBuilder b = new StringBuilder(q == null ? "" : q.strip());
        String lower = b.toString().toLowerCase(java.util.Locale.ROOT);
        if (kind == JobKind.INTERNSHIP && !lower.contains("intern")) {
            b.append(" internship");
        } else if (kind == JobKind.NEW_GRAD && !lower.contains("grad")) {
            b.append(" new graduate entry level");
        }
        if (location != null && !location.isBlank() && !lower.contains(location.toLowerCase(java.util.Locale.ROOT))) {
            b.append(" in ").append(location.strip());
        }
        String s = b.toString().strip();
        return s.isEmpty() ? "software engineering internship" : s;
    }

    /** Resolves and opens every link in parallel; dead ones are dropped. */
    public List<WebJob> verifyAll(List<WebSearchResponse.Job> found) {
        List<WebJob> checked = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<WebJob>> futures = found.stream().map(j -> pool.submit(() -> verify(j))).toList();
            for (Future<WebJob> f : futures) {
                try {
                    WebJob j = f.get();
                    if (j != null) {
                        checked.add(j);
                    }
                } catch (Exception ignored) {
                    // an unreachable link is simply not shown
                }
            }
        }
        return checked;
    }

    private WebJob verify(WebSearchResponse.Job j) {
        String url = resolve(j.url());
        if (url == null) {
            return null;
        }
        String status = check(url);
        if ("gone".equals(status)) {
            return null;
        }
        String canonical = JobFeedService.canonicalUrl(url);
        List<Long> ids = jdbc.queryForList("select id from job_posting where apply_url = ? and not closed", Long.class, canonical);
        return new WebJob(j.title(), j.company(), j.location(), j.posted(), url, status, ids.isEmpty() ? null : ids.getFirst());
    }

    /** Grounding links are redirects to the real page; read the first hop's Location without visiting it twice. */
    private String resolve(String url) {
        if (url == null || !url.startsWith("http")) {
            return null;
        }
        if (!url.contains("vertexaisearch.cloud.google.com")) {
            return url;
        }
        try {
            HttpResponse<Void> r = noRedirects.send(request(url, "HEAD"), HttpResponse.BodyHandlers.discarding());
            if (r.statusCode() / 100 == 3) {
                return r.headers().firstValue("location").orElse(null);
            }
            r = noRedirects.send(request(url, "GET"), HttpResponse.BodyHandlers.discarding());
            return r.statusCode() / 100 == 3 ? r.headers().firstValue("location").orElse(null) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String check(String url) {
        try {
            HttpResponse<Void> r = followRedirects.send(request(url, "GET"), HttpResponse.BodyHandlers.discarding());
            int code = r.statusCode();
            if (code < 400) {
                return "open";
            }
            // LinkedIn answers bots with 999, many career sites with 401/403/429: real pages, just not for us.
            return code == 404 || code == 410 ? "gone" : "blocked";
        } catch (Exception e) {
            return "blocked";
        }
    }

    private static HttpRequest request(String url, String method) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(LINK_TIMEOUT)
                .header("User-Agent", "Mozilla/5.0 (compatible; RepoMatch link check)")
                .header("Accept", "text/html,application/xhtml+xml")
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
    }
}
