package com.repomatch.jobs;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.repomatch.ai.AiServiceClient;
import com.repomatch.ai.AiServiceException;
import com.repomatch.ai.dto.WebSearchRequest;
import com.repomatch.ai.dto.WebSearchResponse;
import com.repomatch.config.AppProperties;

/**
 * Once a day, runs a fixed set of Google searches (via Gemini grounding) for internships in India and adds the
 * postings whose links open to the feed. This reaches employers with no public job API — company career pages,
 * portals — which is where most Indian internships are listed.
 *
 * <p>Costs real money per search, so: at most once per {@link #MIN_INTERVAL}, a hard cap on queries, and a failed
 * query is skipped, never retried.
 */
@Service
public class GoogleDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDiscoveryService.class);
    static final Duration MIN_INTERVAL = Duration.ofHours(20);
    static final int RESULTS_PER_QUERY = 15;
    static final int PARALLEL_QUERIES = 4;

    private final AiServiceClient ai;
    private final WebJobSearchService web;
    private final JobFeedService feed;
    private final JobSourceRepository sources;
    private final AppProperties.Discovery config;

    public GoogleDiscoveryService(AiServiceClient ai, WebJobSearchService web, JobFeedService feed,
                                  JobSourceRepository sources, AppProperties props) {
        this.ai = ai;
        this.web = web;
        this.feed = feed;
        this.sources = sources;
        this.config = props.discovery();
    }

    public record Run(boolean ran, String reason, int queries, int found, int kept, int added, List<Long> newPostingIds) {
        static Run skipped(String reason) {
            return new Run(false, reason, 0, 0, 0, 0, List.of());
        }
    }

    /** Runs when enabled and a day has passed; otherwise explains why not. */
    public Run runIfDue(boolean force) {
        if (config == null || !config.enabled()) {
            return Run.skipped("disabled");
        }
        JobSourceEntity source = sources.findByFullNameIgnoreCase("google:daily-discovery").orElse(null);
        if (source == null || !source.isEnabled()) {
            return Run.skipped("no discovery source");
        }
        Instant last = source.getLastCheckedAt();
        if (!force && last != null && last.plus(MIN_INTERVAL).isAfter(Instant.now())) {
            return Run.skipped("ran " + Duration.between(last, Instant.now()).toHours() + "h ago");
        }

        List<String> queries = config.queries().stream().limit(Math.max(0, config.maxQueriesPerRun())).toList();
        Map<String, FetchedPosting> postings = new LinkedHashMap<>();
        int found = 0;
        int ran = 0;
        // A few searches at a time: each takes ~30s, and the scheduled request shouldn't run for many minutes.
        java.util.concurrent.Semaphore permits = new java.util.concurrent.Semaphore(PARALLEL_QUERIES);
        List<java.util.concurrent.Future<List<WebJobSearchService.WebJob>>> futures = new ArrayList<>();
        List<Integer> foundCounts = java.util.Collections.synchronizedList(new ArrayList<>());
        try (java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            for (String query : queries) {
                futures.add(pool.submit(() -> {
                    permits.acquire();
                    try {
                        WebSearchResponse res = ai.webSearch(new WebSearchRequest(query, RESULTS_PER_QUERY));
                        List<WebSearchResponse.Job> jobs = res.jobs() == null ? List.of() : res.jobs();
                        foundCounts.add(jobs.size());
                        return web.verifyAll(jobs);
                    } catch (AiServiceException e) {
                        log.warn("google discovery query '{}' failed: {}", query, e.getMessage());
                        return null;
                    } finally {
                        permits.release();
                    }
                }));
            }
            for (var f : futures) {
                List<WebJobSearchService.WebJob> jobs;
                try {
                    jobs = f.get();
                } catch (Exception e) {
                    jobs = null;
                }
                if (jobs == null) {
                    continue;
                }
                ran++;
                for (WebJobSearchService.WebJob j : jobs) {
                    if (j.postingId() != null || j.title() == null || j.title().isBlank()) {
                        continue; // already in the feed from a real board
                    }
                    postings.putIfAbsent(JobFeedService.canonicalUrl(j.url()), toPosting(j));
                }
            }
        }
        found = foundCounts.stream().mapToInt(Integer::intValue).sum();

        List<Long> fresh = new ArrayList<>();
        JobFeedService.SourceResult stored = feed.store(source, new ArrayList<>(postings.values()), Instant.now(), fresh);
        log.info("google discovery: {} queries, {} found, {} kept, {} new", ran, found, postings.size(), stored.added());
        return new Run(true, null, ran, found, postings.size(), stored.added(), fresh);
    }

    static FetchedPosting toPosting(WebJobSearchService.WebJob j) {
        String location = j.location() == null || j.location().isBlank() ? "India" : j.location();
        Places.Place place = Places.of(location);
        Instant posted = JobListParser.dateOf(j.posted());
        if (posted == null && j.posted() != null && !j.posted().isBlank()) {
            Duration age = JobFeedService.ageOf(j.posted());
            posted = age.isZero() ? null : Instant.now().minus(age);
        }
        String company = j.company() == null || j.company().isBlank() ? "Unknown company" : j.company();
        return new FetchedPosting(company, j.title(), location, place.country() != null ? place.country() : "IN",
                place.remote(), j.url(), false, j.posted(), posted, false);
    }
}
