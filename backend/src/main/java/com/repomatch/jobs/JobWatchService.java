package com.repomatch.jobs;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repomatch.notify.Mailer;

/** Saved searches, the postings they catch, and the alert emails about them. */
@Service
public class JobWatchService {

    /** A new watch shows what's already out there from this far back, in the app only (no email flood). */
    static final Duration BACKFILL = Duration.ofDays(14);
    static final int MAX_BACKFILL = 60;
    static final int MAX_WATCHES_PER_OWNER = 10;
    static final int MAX_ITEMS_PER_EMAIL = 25;

    private final JobWatchRepository watches;
    private final JobMatchRepository matches;
    private final JobPostingRepository postings;
    private final JobSourceRepository sources;
    private final Mailer mailer;
    private final OwnerKeys ownerKeys;

    public JobWatchService(JobWatchRepository watches, JobMatchRepository matches, JobPostingRepository postings,
                           JobSourceRepository sources, Mailer mailer, OwnerKeys ownerKeys) {
        this.watches = watches;
        this.matches = matches;
        this.postings = postings;
        this.sources = sources;
        this.mailer = mailer;
        this.ownerKeys = ownerKeys;
    }

    public record WatchInput(String name, String email, List<String> keywords, List<String> excludeKeywords,
                             List<String> locations, Set<JobKind> kinds, Boolean autoPrepare, Long autoResumeId,
                             String autoGithubUsername) {
    }

    public record WatchView(long id, String name, String email, List<String> keywords, List<String> excludeKeywords,
                            List<String> locations, List<String> kinds, boolean active, boolean autoPrepare,
                            Long autoResumeId, String autoGithubUsername, Instant createdAt,
                            Instant lastNotifiedAt, long newMatches) {
    }

    public record MatchView(long id, long watchId, String watchName, JobMatchEntity.Status status, Instant matchedAt,
                            boolean emailed, JobFeedService.PostingView posting) {
    }

    @Transactional
    public WatchView create(String ownerHash, WatchInput in) {
        if (watches.findByOwnerKeyHashOrderByCreatedAtDesc(ownerHash).size() >= MAX_WATCHES_PER_OWNER) {
            throw new IllegalArgumentException("You can keep up to " + MAX_WATCHES_PER_OWNER + " alerts. Delete one first.");
        }
        JobWatchEntity w = new JobWatchEntity();
        w.setOwnerKeyHash(ownerHash);
        w.setCreatedAt(Instant.now());
        apply(w, in);
        watches.save(w);

        // Backfill so a brand-new alert isn't an empty page.
        Instant since = Instant.now().minus(BACKFILL);
        postings.findOpenListedSince(since).stream()
                .filter(w::matches)
                .sorted((a, b) -> b.getListedAt().compareTo(a.getListedAt()))
                .limit(MAX_BACKFILL)
                .forEach(p -> addMatch(w, p, Instant.now()));
        return view(w, countNew(w.getId()));
    }

    @Transactional
    public WatchView update(String ownerHash, long id, WatchInput in, Boolean active) {
        JobWatchEntity w = owned(ownerHash, id);
        apply(w, in);
        if (active != null) {
            w.setActive(active);
        }
        return view(watches.save(w), countNew(id));
    }

    @Transactional
    public void delete(String ownerHash, long id) {
        watches.delete(owned(ownerHash, id));
    }

    @Transactional(readOnly = true)
    public List<WatchView> list(String ownerHash) {
        return watches.findByOwnerKeyHashOrderByCreatedAtDesc(ownerHash).stream()
                .map(w -> view(w, countNew(w.getId())))
                .toList();
    }

    /** Every match across this person's watches, newest first, with the posting inlined. */
    @Transactional(readOnly = true)
    public List<MatchView> matches(String ownerHash, JobMatchEntity.Status status) {
        Map<Long, JobWatchEntity> mine = watches.findByOwnerKeyHashOrderByCreatedAtDesc(ownerHash).stream()
                .collect(Collectors.toMap(JobWatchEntity::getId, Function.identity()));
        if (mine.isEmpty()) {
            return List.of();
        }
        List<JobMatchEntity> rows = matches.findByWatchIdInOrderByMatchedAtDesc(mine.keySet()).stream()
                .filter(m -> status == null || m.getStatus() == status)
                .toList();
        Map<Long, JobPostingEntity> byId = postings.findAllById(rows.stream().map(JobMatchEntity::getPostingId).toList())
                .stream().collect(Collectors.toMap(JobPostingEntity::getId, Function.identity()));
        Map<Long, JobSourceEntity> sourceNames = sourceNames();

        // One posting can match two watches; show it once, under the first.
        Map<Long, MatchView> unique = new LinkedHashMap<>();
        for (JobMatchEntity m : rows) {
            JobPostingEntity p = byId.get(m.getPostingId());
            if (p != null && !unique.containsKey(p.getId())) {
                unique.put(p.getId(), new MatchView(m.getId(), m.getWatchId(), mine.get(m.getWatchId()).getName(),
                        m.getStatus(), m.getMatchedAt(), m.getEmailedAt() != null,
                        JobFeedService.view(p, sourceNames.get(p.getSourceId()).getFullName(), sourceNames.get(p.getSourceId()).getType())));
            }
        }
        return new ArrayList<>(unique.values());
    }

    /** Marks every match of this posting (across the person's watches) approved or skipped. */
    @Transactional
    public void decide(String ownerHash, long postingId, JobMatchEntity.Status status) {
        List<Long> mine = watches.findByOwnerKeyHashOrderByCreatedAtDesc(ownerHash).stream().map(JobWatchEntity::getId).toList();
        if (mine.isEmpty()) {
            return;
        }
        matches.findByWatchIdInOrderByMatchedAtDesc(mine).stream()
                .filter(m -> m.getPostingId() == postingId)
                .forEach(m -> m.setStatus(status));
    }

    public record AlertRun(int matched, int emailsSent, boolean mailEnabled) {
    }

    /** Called after a feed refresh: match the new postings against active watches and email a digest per watch. */
    @Transactional
    public AlertRun alert(Collection<Long> newPostingIds) {
        Instant now = Instant.now();
        List<JobPostingEntity> fresh = newPostingIds.isEmpty() ? List.of() : postings.findAllById(newPostingIds);
        int matched = 0;
        int sent = 0;
        for (JobWatchEntity w : watches.findByActiveTrue()) {
            for (JobPostingEntity p : fresh) {
                if (w.matches(p) && addMatch(w, p, now)) {
                    matched++;
                }
            }
            List<JobMatchEntity> unsent = matches.findByWatchIdAndEmailedAtIsNull(w.getId()).stream()
                    .filter(m -> m.getStatus() == JobMatchEntity.Status.NEW)
                    .filter(m -> m.getMatchedAt().isAfter(w.getCreatedAt().plusSeconds(1))) // backfill stays in-app
                    .toList();
            if (!unsent.isEmpty() && w.getEmail() != null && sendDigest(w, unsent)) {
                unsent.forEach(m -> m.setEmailedAt(now));
                w.setLastNotifiedAt(now);
                sent++;
            }
        }
        return new AlertRun(matched, sent, mailer.enabled());
    }

    private boolean sendDigest(JobWatchEntity w, List<JobMatchEntity> unsent) {
        Map<Long, JobPostingEntity> byId = postings.findAllById(unsent.stream().map(JobMatchEntity::getPostingId).toList())
                .stream().collect(Collectors.toMap(JobPostingEntity::getId, Function.identity()));
        List<JobPostingEntity> items = unsent.stream().map(m -> byId.get(m.getPostingId()))
                .filter(java.util.Objects::nonNull).limit(MAX_ITEMS_PER_EMAIL).toList();
        if (items.isEmpty()) {
            return false;
        }

        StringBuilder html = new StringBuilder("""
                <div style="font-family:Arial,sans-serif;max-width:620px;color:#0b1220">
                <p style="font-size:12px;letter-spacing:.12em;text-transform:uppercase;color:#2563ff;margin:0 0 6px">RepoMatch alert</p>
                <h2 style="margin:0 0 18px">%d new for “%s”</h2>
                """.formatted(unsent.size(), Mailer.escape(w.getName())));
        StringBuilder text = new StringBuilder(unsent.size() + " new postings for \"" + w.getName() + "\"\n\n");
        String signed = ownerKeys.signedLink(w.getOwnerKeyHash());
        String keyParam = signed == null ? "" : "?key=" + signed;
        for (JobPostingEntity p : items) {
            String review = mailer.link("/jobs/" + p.getId() + keyParam);
            html.append("""
                    <div style="border:1px solid #dfe3ec;padding:14px 16px;margin-bottom:10px">
                      <div style="font-weight:bold">%s</div>
                      <div style="margin:4px 0 10px;color:#46506a">%s · %s</div>
                      <a href="%s" style="background:#2563ff;color:#fff;padding:8px 14px;text-decoration:none;font-size:13px">Review &amp; approve</a>
                    </div>
                    """.formatted(Mailer.escape(p.getRole()), Mailer.escape(p.getCompany()),
                    Mailer.escape(p.getLocation() == null ? "Location not listed" : p.getLocation()), review));
            text.append("- ").append(p.getRole()).append(" · ").append(p.getCompany()).append("\n  ").append(review).append("\n");
        }
        if (unsent.size() > items.size()) {
            html.append("<p>…and %d more in the app.</p>".formatted(unsent.size() - items.size()));
        }
        html.append("""
                <p style="font-size:12px;color:#7a839a;margin-top:22px">Nothing is submitted for you. Approve in the app, pick a resume, and apply yourself.
                <a href="%s">Manage alerts</a></p></div>""".formatted(mailer.link("/alerts" + keyParam)));
        return mailer.send(w.getEmail(), unsent.size() + " new: " + items.getFirst().getRole() + " at " + items.getFirst().getCompany()
                + (unsent.size() > 1 ? " and more" : ""), html.toString(), text.toString());
    }

    private boolean addMatch(JobWatchEntity w, JobPostingEntity p, Instant at) {
        if (matches.existsByWatchIdAndPostingId(w.getId(), p.getId())) {
            return false;
        }
        JobMatchEntity m = new JobMatchEntity();
        m.setWatchId(w.getId());
        m.setPostingId(p.getId());
        m.setStatus(JobMatchEntity.Status.NEW);
        m.setMatchedAt(at);
        matches.save(m);
        return true;
    }

    private static void apply(JobWatchEntity w, WatchInput in) {
        w.setName(in.name().strip());
        w.setEmail(in.email() == null || in.email().isBlank() ? null : in.email().strip());
        w.setKeywords(clean(in.keywords()));
        w.setExcludeKeywords(clean(in.excludeKeywords()));
        w.setLocations(clean(in.locations()));
        w.setKinds(in.kinds() == null ? Set.of() : in.kinds());
        w.setAutoPrepare(Boolean.TRUE.equals(in.autoPrepare()));
        w.setAutoResumeId(in.autoResumeId());
        w.setAutoGithubUsername(in.autoGithubUsername() == null || in.autoGithubUsername().isBlank()
                ? null : in.autoGithubUsername().strip());
    }

    private static String[] clean(List<String> values) {
        return values == null ? new String[0]
                : values.stream().map(String::strip).filter(s -> !s.isEmpty()).distinct().limit(20).toArray(String[]::new);
    }

    private JobWatchEntity owned(String ownerHash, long id) {
        return watches.findByIdAndOwnerKeyHash(id, ownerHash).orElseThrow(() -> new WatchNotFoundException(id));
    }

    private long countNew(long watchId) {
        return matches.findByWatchIdInOrderByMatchedAtDesc(List.of(watchId)).stream()
                .filter(m -> m.getStatus() == JobMatchEntity.Status.NEW).count();
    }

    private Map<Long, JobSourceEntity> sourceNames() {
        return sources.findAll().stream().collect(Collectors.toMap(JobSourceEntity::getId, Function.identity()));
    }

    private static WatchView view(JobWatchEntity w, long newMatches) {
        return new WatchView(w.getId(), w.getName(), w.getEmail(), Arrays.asList(w.getKeywords()),
                Arrays.asList(w.getExcludeKeywords()), Arrays.asList(w.getLocations()), Arrays.asList(w.getKinds()),
                w.isActive(), w.isAutoPrepare(), w.getAutoResumeId(), w.getAutoGithubUsername(), w.getCreatedAt(),
                w.getLastNotifiedAt(), newMatches);
    }

    public static class WatchNotFoundException extends RuntimeException {
        public WatchNotFoundException(long id) {
            super("Alert " + id + " doesn't exist");
        }
    }
}
