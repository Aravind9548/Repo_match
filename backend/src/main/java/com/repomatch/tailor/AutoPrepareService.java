package com.repomatch.tailor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.repomatch.jobs.JobMatchEntity;
import com.repomatch.jobs.JobMatchRepository;
import com.repomatch.jobs.JobWatchEntity;
import com.repomatch.jobs.JobWatchRepository;
import com.repomatch.tracker.TrackerService;

/**
 * Gets an application ready before the person looks at it.
 *
 * <p>When an alert has auto-prepare on, each new match is tracked, its resume tailored and its answers drafted, so
 * the kit is waiting. It stops at a daily cap because every prepared application costs two model calls, and it never
 * submits anything — the person still reviews and applies.
 */
@Service
public class AutoPrepareService {

    private static final Logger log = LoggerFactory.getLogger(AutoPrepareService.class);
    /** Only just-found matches are prepared: an older one the person left alone is a decision, not a backlog. */
    static final Duration FRESH = Duration.ofHours(24);

    private final JobWatchRepository watches;
    private final JobMatchRepository matches;
    private final TrackerService tracker;
    private final TailorService tailor;
    private final ApplyKitService kits;
    private final int dailyCap;

    public AutoPrepareService(JobWatchRepository watches, JobMatchRepository matches, TrackerService tracker,
                              TailorService tailor, ApplyKitService kits,
                              @Value("${repomatch.auto-prepare.daily-cap:5}") int dailyCap) {
        this.watches = watches;
        this.matches = matches;
        this.tracker = tracker;
        this.tailor = tailor;
        this.kits = kits;
        this.dailyCap = dailyCap;
    }

    public record Run(int prepared, int skipped, List<String> errors) {
    }

    /** Called after alerts on the scheduled refresh. Each prepared application = one tailoring + one kit. */
    public Run prepare(String clientKey) {
        int prepared = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (JobWatchEntity watch : watches.findByActiveTrue()) {
            if (!watch.isAutoPrepare() || watch.getAutoResumeId() == null) {
                continue;
            }
            String owner = watch.getOwnerKeyHash();
            long already = kits.preparedToday(owner);
            Instant cutoff = Instant.now().minus(FRESH);
            for (JobMatchEntity match : matches.findByWatchIdAndEmailedAtIsNull(watch.getId())) {
                if (match.getStatus() != JobMatchEntity.Status.NEW || match.getMatchedAt().isBefore(cutoff)) {
                    continue;
                }
                if (already + prepared >= dailyCap) {
                    skipped++;
                    continue;
                }
                try {
                    prepareOne(owner, watch, match.getPostingId(), clientKey);
                    prepared++;
                } catch (RuntimeException e) {
                    // One posting failing (quota, a page that won't load) mustn't stop the rest.
                    log.warn("auto-prepare failed for posting {}: {}", match.getPostingId(), e.getMessage());
                    errors.add(e.getMessage());
                }
            }
        }
        if (prepared > 0 || skipped > 0) {
            log.info("auto-prepare: {} prepared, {} over the daily cap of {}", prepared, skipped, dailyCap);
        }
        return new Run(prepared, skipped, errors);
    }

    private void prepareOne(String owner, JobWatchEntity watch, long postingId, String clientKey) {
        var app = tracker.approve(owner, postingId);
        TailorService.RunView run = tailor.start(owner, app.id(), postingId, watch.getAutoResumeId(), clientKey);
        if (run.jobDescription() == null || run.jobDescription().strip().length() < 40) {
            return; // without the description, tailoring would be guesswork
        }
        run = tailor.suggest(owner, run.id(), new TailorService.SuggestInput(null, run.jobDescription(),
                watch.getAutoGithubUsername(), "Keep it to one page.", true, "pro"), clientKey);
        tailor.build(owner, run.id(), run.acceptedIds(), false);
        kits.build(owner, app.id(), watch.getAutoGithubUsername(), "pro", clientKey);
    }
}
