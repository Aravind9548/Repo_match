package com.repomatch.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.repomatch.config.AppProperties;
import com.repomatch.jobs.JobFeedService;
import com.repomatch.jobs.JobKind;
import com.repomatch.jobs.JobMatchEntity;
import com.repomatch.jobs.JobWatchService;
import com.repomatch.jobs.OwnerKeys;
import com.repomatch.tracker.TrackerService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Job feed, saved alerts, and the scheduled refresh. */
@RestController
@Validated
public class JobsController {

    static final String KEY_HEADER = "X-Tracker-Key";
    /** Anyone can press "refresh"; GitHub doesn't need to be asked more often than this. */
    static final Duration MANUAL_REFRESH_EVERY = Duration.ofMinutes(10);

    private final JobFeedService feed;
    private final JobWatchService watches;
    private final TrackerService tracker;
    private final OwnerKeys keys;
    private final com.repomatch.jobs.WebJobSearchService web;
    private final com.repomatch.jobs.GoogleDiscoveryService discovery;
    private final com.repomatch.tailor.AutoPrepareService autoPrepare;
    private final com.repomatch.jobs.LinkRecheckService recheck;
    private final String cronSecret;
    private final AtomicReference<Instant> lastRefresh = new AtomicReference<>(Instant.EPOCH);

    public JobsController(JobFeedService feed, JobWatchService watches, TrackerService tracker, OwnerKeys keys,
                          com.repomatch.jobs.WebJobSearchService web,
                          com.repomatch.jobs.GoogleDiscoveryService discovery,
                          com.repomatch.tailor.AutoPrepareService autoPrepare,
                          com.repomatch.jobs.LinkRecheckService recheck, AppProperties props) {
        this.autoPrepare = autoPrepare;
        this.recheck = recheck;
        this.web = web;
        this.discovery = discovery;
        this.feed = feed;
        this.watches = watches;
        this.tracker = tracker;
        this.keys = keys;
        this.cronSecret = props.notifications().cronSecret();
    }

    // --- feed ---------------------------------------------------------------------------------------------------

    @GetMapping("/api/jobs")
    public JobFeedService.Page search(@RequestParam(required = false) @Size(max = 200) String q,
                                      @RequestParam(required = false) @Size(max = 100) String location,
                                      @RequestParam(required = false) JobKind kind,
                                      @RequestParam(defaultValue = "0") @Min(0) @Max(365) int sinceDays,
                                      @RequestParam(defaultValue = "0") @Min(0) int offset,
                                      @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit) {
        return feed.search(q, location, kind, sinceDays, offset, limit);
    }

    public record WebSearchBody(@Size(max = 200) String q, @Size(max = 100) String location, JobKind kind) {
    }

    /** Paid (one grounded Gemini call), so the UI only calls it on an explicit click. */
    @PostMapping("/api/jobs/web-search")
    public com.repomatch.jobs.WebJobSearchService.Result webSearch(@Valid @RequestBody WebSearchBody body,
                                                                   jakarta.servlet.http.HttpServletRequest http) {
        return web.search(body.q(), body.location(), body.kind(), http.getRemoteAddr());
    }

    public record PostingDetail(JobFeedService.PostingView posting, TrackerService.ApplicationView application) {
    }

    /** The tracker key is optional here: without one you just don't see your own application state. */
    @GetMapping("/api/jobs/{id}")
    public PostingDetail posting(@PathVariable long id, @RequestHeader(value = KEY_HEADER, required = false) String key) {
        JobFeedService.PostingView posting = feed.get(id);
        TrackerService.ApplicationView app = key == null || key.isBlank() ? null : tracker.forPosting(keys.resolve(key), id);
        return new PostingDetail(posting, app);
    }

    public record SourceView(String fullName, com.repomatch.jobs.SourceType type, String companyName, String branch,
                             boolean enabled, Instant lastCheckedAt, int lastCount, String lastError) {
    }

    @GetMapping("/api/jobs/sources")
    public List<SourceView> sources() {
        return feed.sources().stream().map(s -> new SourceView(s.getFullName(), s.getType(), s.getCompanyName(), s.getBranch(), s.isEnabled(),
                s.getLastCheckedAt(), s.getLastCount(), s.getLastError())).toList();
    }

    public record RunResult(JobFeedService.RefreshResult feed, com.repomatch.jobs.GoogleDiscoveryService.Run discovery,
                            JobWatchService.AlertRun alerts, com.repomatch.tailor.AutoPrepareService.Run prepared,
                            com.repomatch.jobs.LinkRecheckService.Run rechecked, TrackerService.ReminderRun reminders) {
    }

    /** Cloud Scheduler calls this with the shared secret: refresh, alert, remind. */
    @PostMapping("/api/jobs/cron")
    public ResponseEntity<RunResult> cron(@RequestHeader(value = "X-Cron-Secret", required = false) String secret,
                                          @RequestParam(defaultValue = "false") boolean forceDiscovery,
                                          jakarta.servlet.http.HttpServletRequest http) {
        if (cronSecret == null || cronSecret.isBlank() || !java.security.MessageDigest.isEqual(
                cronSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                (secret == null ? "" : secret).getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        lastRefresh.set(Instant.now());
        JobFeedService.RefreshResult r = feed.refresh();
        var found = discovery.runIfDue(forceDiscovery);
        List<Long> fresh = new java.util.ArrayList<>(r.newPostingIds());
        fresh.addAll(found.newPostingIds());
        // Dead links are closed before anyone is told about anything.
        com.repomatch.jobs.LinkRecheckService.Run rechecked = recheck.recheck();
        JobWatchService.AlertRun alerts = watches.alert(fresh);
        // Preparing comes after alerting, so a person sees the posting even if preparation fails.
        var prepared = autoPrepare.prepare(http == null ? "cron" : http.getRemoteAddr());
        return ResponseEntity.ok(new RunResult(r, found, alerts, prepared, rechecked, tracker.remind()));
    }

    /** The "refresh now" button: at most every ten minutes across all users. */
    @PostMapping("/api/jobs/refresh")
    public ResponseEntity<RunResult> refresh() {
        Instant previous = lastRefresh.get();
        Instant now = Instant.now();
        if (previous.plus(MANUAL_REFRESH_EVERY).isAfter(now) || !lastRefresh.compareAndSet(previous, now)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        JobFeedService.RefreshResult r = feed.refresh();
        return ResponseEntity.ok(new RunResult(r, null, watches.alert(r.newPostingIds()), null, null, null));
    }

    // --- tracker key ----------------------------------------------------------------------------------------------

    public record NewKey(String key) {
    }

    @PostMapping("/api/tracker/keys")
    public NewKey newKey() {
        return new NewKey(OwnerKeys.newKey());
    }

    // --- alerts ---------------------------------------------------------------------------------------------------

    public record WatchBody(@NotBlank @Size(max = 120) String name, @Email @Size(max = 320) String email,
                            @Size(max = 20) List<@Size(max = 60) String> keywords,
                            @Size(max = 20) List<@Size(max = 60) String> excludeKeywords,
                            @Size(max = 20) List<@Size(max = 60) String> locations, Set<JobKind> kinds, Boolean active,
                            Boolean autoPrepare, Long autoResumeId, @Size(max = 40) String autoGithubUsername) {
        JobWatchService.WatchInput input() {
            return new JobWatchService.WatchInput(name, email, keywords, excludeKeywords, locations, kinds,
                    autoPrepare, autoResumeId, autoGithubUsername);
        }
    }

    @GetMapping("/api/alerts")
    public List<JobWatchService.WatchView> listWatches(@RequestHeader(KEY_HEADER) String key) {
        return watches.list(keys.resolve(key));
    }

    @PostMapping("/api/alerts")
    public JobWatchService.WatchView createWatch(@RequestHeader(KEY_HEADER) String key, @Valid @RequestBody WatchBody body) {
        return watches.create(keys.resolve(key), body.input());
    }

    @PatchMapping("/api/alerts/{id}")
    public JobWatchService.WatchView updateWatch(@RequestHeader(KEY_HEADER) String key, @PathVariable long id,
                                                 @Valid @RequestBody WatchBody body) {
        return watches.update(keys.resolve(key), id, body.input(), body.active());
    }

    @DeleteMapping("/api/alerts/{id}")
    public ResponseEntity<Void> deleteWatch(@RequestHeader(KEY_HEADER) String key, @PathVariable long id) {
        watches.delete(keys.resolve(key), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/alerts/matches")
    public List<JobWatchService.MatchView> matches(@RequestHeader(KEY_HEADER) String key,
                                                   @RequestParam(required = false) JobMatchEntity.Status status) {
        return watches.matches(keys.resolve(key), status);
    }
}
