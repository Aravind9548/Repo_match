package com.repomatch.tracker;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repomatch.jobs.JobMatchEntity;
import com.repomatch.jobs.JobPostingEntity;
import com.repomatch.jobs.JobPostingRepository;
import com.repomatch.jobs.JobWatchRepository;
import com.repomatch.jobs.JobWatchService;
import com.repomatch.jobs.OwnerKeys;
import com.repomatch.notify.Mailer;

/**
 * Tracks an application after the person approves it: what they chose, when they applied, what came back.
 *
 * <p>Nothing here is guessed. We can't know a company will send an assessment; the person records what actually
 * arrived (an OA, an interview invite, a deadline), and the tracker reminds them before deadlines and nudges when an
 * application has gone quiet.
 */
@Service
public class TrackerService {

    /** Remind this many days ahead of a recorded deadline. */
    static final int DUE_SOON_DAYS = 2;
    /** An application with no update for this long gets a "follow up?" nudge. */
    static final Duration QUIET_AFTER = Duration.ofDays(14);
    /** Approved but never submitted. */
    static final Duration STALE_APPROVAL = Duration.ofDays(3);
    /** Don't nag more than this often about the same application. */
    static final Duration REMIND_EVERY = Duration.ofDays(3);

    private final ApplicationRepository applications;
    private final ApplicationEventRepository events;
    private final JobPostingRepository postings;
    private final JobWatchService watchService;
    private final JobWatchRepository watches;
    private final Mailer mailer;
    private final OwnerKeys ownerKeys;

    public TrackerService(ApplicationRepository applications, ApplicationEventRepository events,
                          JobPostingRepository postings, JobWatchService watchService, JobWatchRepository watches,
                          Mailer mailer, OwnerKeys ownerKeys) {
        this.applications = applications;
        this.events = events;
        this.postings = postings;
        this.watchService = watchService;
        this.watches = watches;
        this.mailer = mailer;
        this.ownerKeys = ownerKeys;
    }

    public record EventView(long id, ApplicationStatus status, String note, Instant occurredAt) {
    }

    public record ApplicationView(long id, Long postingId, String company, String role, String applyUrl,
                                  ApplicationStatus status, ResumeChoice resumeChoice, String nextStep,
                                  LocalDate nextStepDue, String notes, Instant appliedAt, Instant createdAt,
                                  Instant updatedAt, List<EventView> events) {
    }

    public record Update(ApplicationStatus status, ResumeChoice resumeChoice, String nextStep, LocalDate nextStepDue,
                         Boolean clearNextStep, String notes, String eventNote) {
    }

    /** Approving a posting starts tracking it; approving twice returns the same application. */
    @Transactional
    public ApplicationView approve(String ownerHash, long postingId) {
        JobPostingEntity p = postings.findById(postingId)
                .orElseThrow(() -> new com.repomatch.jobs.JobFeedService.PostingNotFoundException(postingId));
        watchService.decide(ownerHash, postingId, JobMatchEntity.Status.APPROVED);
        ApplicationEntity existing = applications.findByOwnerKeyHashAndPostingId(ownerHash, postingId).orElse(null);
        if (existing != null) {
            return view(existing, true);
        }
        Instant now = Instant.now();
        ApplicationEntity a = new ApplicationEntity();
        a.setOwnerKeyHash(ownerHash);
        a.setPostingId(postingId);
        a.setCompany(p.getCompany());
        a.setRole(p.getRole());
        a.setApplyUrl(p.getApplyUrl());
        a.setStatus(ApplicationStatus.APPROVED);
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        applications.save(a);
        log(a, ApplicationStatus.APPROVED, "Approved from the job feed", now);
        return view(a, true);
    }

    /** For a job found somewhere else: track it by hand. */
    @Transactional
    public ApplicationView addManual(String ownerHash, String company, String role, String applyUrl) {
        Instant now = Instant.now();
        ApplicationEntity a = new ApplicationEntity();
        a.setOwnerKeyHash(ownerHash);
        a.setCompany(company.strip());
        a.setRole(role.strip());
        a.setApplyUrl(applyUrl == null || applyUrl.isBlank() ? null : applyUrl.strip());
        a.setStatus(ApplicationStatus.APPROVED);
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        applications.save(a);
        log(a, ApplicationStatus.APPROVED, "Added by hand", now);
        return view(a, true);
    }

    @Transactional
    public void skip(String ownerHash, long postingId) {
        watchService.decide(ownerHash, postingId, JobMatchEntity.Status.SKIPPED);
    }

    @Transactional(readOnly = true)
    public List<ApplicationView> list(String ownerHash) {
        return applications.findByOwnerKeyHashOrderByUpdatedAtDesc(ownerHash).stream().map(a -> view(a, false)).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationView get(String ownerHash, long id) {
        return view(owned(ownerHash, id), true);
    }

    @Transactional(readOnly = true)
    public ApplicationView forPosting(String ownerHash, long postingId) {
        return applications.findByOwnerKeyHashAndPostingId(ownerHash, postingId).map(a -> view(a, true)).orElse(null);
    }

    @Transactional
    public ApplicationView update(String ownerHash, long id, Update u) {
        ApplicationEntity a = owned(ownerHash, id);
        Instant now = Instant.now();
        List<String> changes = new ArrayList<>();

        if (u.resumeChoice() != null && u.resumeChoice() != a.getResumeChoice()) {
            a.setResumeChoice(u.resumeChoice());
            changes.add(u.resumeChoice() == ResumeChoice.TAILORED ? "Chose a tailored resume" : "Chose the current resume");
        }
        if (Boolean.TRUE.equals(u.clearNextStep())) {
            if (a.getNextStep() != null) {
                changes.add("Done: " + a.getNextStep());
            }
            a.setNextStep(null);
            a.setNextStepDue(null);
        } else if (u.nextStep() != null && !u.nextStep().isBlank()) {
            a.setNextStep(u.nextStep().strip());
            a.setNextStepDue(u.nextStepDue());
            a.setRemindedAt(null);
            changes.add("Next: " + u.nextStep().strip() + (u.nextStepDue() == null ? "" : " (due " + u.nextStepDue() + ")"));
        }
        if (u.notes() != null) {
            a.setNotes(u.notes().isBlank() ? null : u.notes());
        }

        ApplicationStatus logged = null;
        if (u.status() != null && u.status() != a.getStatus()) {
            a.setStatus(u.status());
            logged = u.status();
            if (u.status() == ApplicationStatus.APPLIED && a.getAppliedAt() == null) {
                a.setAppliedAt(now);
            }
            a.setRemindedAt(null);
        }
        if (u.eventNote() != null && !u.eventNote().isBlank()) {
            changes.add(u.eventNote().strip());
        }
        if (logged != null || !changes.isEmpty()) {
            a.setUpdatedAt(now);
            log(a, logged, changes.isEmpty() ? null : String.join(" · ", changes), now);
        }
        return view(applications.save(a), true);
    }

    @Transactional
    public void delete(String ownerHash, long id) {
        applications.delete(owned(ownerHash, id));
    }

    public record ReminderRun(int applications, int emailsSent) {
    }

    /** Daily: deadlines within two days, approvals never submitted, and applications quiet for two weeks. */
    @Transactional
    public ReminderRun remind() {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<ApplicationEntity> due = applications.findNeedingReminder(now.minus(REMIND_EVERY),
                today.plusDays(DUE_SOON_DAYS), now.minus(QUIET_AFTER), now.minus(STALE_APPROVAL));

        Map<String, List<ApplicationEntity>> byOwner = due.stream()
                .collect(Collectors.groupingBy(ApplicationEntity::getOwnerKeyHash, LinkedHashMap::new, Collectors.toList()));
        int sent = 0;
        for (Map.Entry<String, List<ApplicationEntity>> e : byOwner.entrySet()) {
            String email = watches.findByOwnerKeyHashOrderByCreatedAtDesc(e.getKey()).stream()
                    .map(w -> w.getEmail()).filter(m -> m != null && !m.isBlank()).findFirst().orElse(null);
            if (email != null && sendReminder(email, e.getKey(), e.getValue(), today)) {
                e.getValue().forEach(a -> a.setRemindedAt(now));
                sent++;
            }
        }
        return new ReminderRun(due.size(), sent);
    }

    /** The one-line reason an application is in a reminder. */
    static String reason(ApplicationEntity a, LocalDate today) {
        if (a.getNextStepDue() != null && !a.getNextStepDue().isAfter(today.plusDays(DUE_SOON_DAYS))) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(today, a.getNextStepDue());
            String when = days < 0 ? "was due " + (-days) + " day" + (days == -1 ? "" : "s") + " ago"
                    : days == 0 ? "is due today" : "is due in " + days + " day" + (days == 1 ? "" : "s");
            return a.getNextStep() + " " + when;
        }
        if (a.getStatus() == ApplicationStatus.APPROVED) {
            return "Approved but not submitted yet";
        }
        return "No update since you applied — a short follow-up note often gets a reply";
    }

    private boolean sendReminder(String email, String ownerHash, List<ApplicationEntity> items, LocalDate today) {
        String signed = ownerKeys.signedLink(ownerHash);
        String keyParam = signed == null ? "" : "?key=" + signed;
        StringBuilder html = new StringBuilder("""
                <div style="font-family:Arial,sans-serif;max-width:620px;color:#0b1220">
                <p style="font-size:12px;letter-spacing:.12em;text-transform:uppercase;color:#2563ff;margin:0 0 6px">RepoMatch tracker</p>
                <h2 style="margin:0 0 18px">%d application%s need you</h2>
                """.formatted(items.size(), items.size() == 1 ? "" : "s"));
        StringBuilder text = new StringBuilder();
        for (ApplicationEntity a : items) {
            String link = mailer.link("/tracker/" + a.getId() + keyParam);
            html.append("""
                    <div style="border:1px solid #dfe3ec;padding:14px 16px;margin-bottom:10px">
                      <div style="font-weight:bold">%s · %s</div>
                      <div style="margin:4px 0 10px;color:#46506a">%s</div>
                      <a href="%s" style="color:#2563ff">Open in tracker</a>
                    </div>""".formatted(Mailer.escape(a.getCompany()), Mailer.escape(a.getRole()),
                    Mailer.escape(reason(a, today)), link));
            text.append("- ").append(a.getCompany()).append(" · ").append(a.getRole()).append(": ")
                    .append(reason(a, today)).append("\n  ").append(link).append("\n");
        }
        html.append("</div>");
        return mailer.send(email, items.size() == 1 ? a0(items) : items.size() + " applications need you",
                html.toString(), text.toString());
    }

    private static String a0(List<ApplicationEntity> items) {
        return items.getFirst().getCompany() + ": " + reason(items.getFirst(), LocalDate.now(ZoneOffset.UTC));
    }

    private void log(ApplicationEntity a, ApplicationStatus status, String note, Instant at) {
        ApplicationEventEntity e = new ApplicationEventEntity();
        e.setApplicationId(a.getId());
        e.setStatus(status);
        e.setNote(note);
        e.setOccurredAt(at);
        events.save(e);
    }

    private ApplicationEntity owned(String ownerHash, long id) {
        return applications.findByIdAndOwnerKeyHash(id, ownerHash).orElseThrow(() -> new ApplicationNotFoundException(id));
    }

    private ApplicationView view(ApplicationEntity a, boolean withEvents) {
        List<EventView> timeline = withEvents
                ? events.findByApplicationIdOrderByOccurredAtDescIdDesc(a.getId()).stream()
                        .map(e -> new EventView(e.getId(), e.getStatus(), e.getNote(), e.getOccurredAt())).toList()
                : List.of();
        return new ApplicationView(a.getId(), a.getPostingId(), a.getCompany(), a.getRole(), a.getApplyUrl(),
                a.getStatus(), a.getResumeChoice(), a.getNextStep(), a.getNextStepDue(), a.getNotes(), a.getAppliedAt(),
                a.getCreatedAt(), a.getUpdatedAt(), timeline);
    }

    public static class ApplicationNotFoundException extends RuntimeException {
        public ApplicationNotFoundException(long id) {
            super("Application " + id + " doesn't exist");
        }
    }
}
