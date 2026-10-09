package com.repomatch.tailor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.repomatch.jobs.JobKind;
import com.repomatch.jobs.JobMatchEntity;
import com.repomatch.jobs.JobMatchRepository;
import com.repomatch.jobs.JobWatchEntity;
import com.repomatch.jobs.JobWatchRepository;
import com.repomatch.tracker.ApplicationStatus;
import com.repomatch.tracker.ResumeChoice;
import com.repomatch.tracker.TrackerService;

/**
 * Auto-prepare spends two model calls per posting, so what it refuses to prepare matters as much as what it does:
 * only watches that asked for it, only fresh matches, never past the daily cap, and never stopping on one failure.
 */
class AutoPrepareServiceTest {

    private JobWatchRepository watches;
    private JobMatchRepository matches;
    private TrackerService tracker;
    private TailorService tailor;
    private ApplyKitService kits;

    @BeforeEach
    void setUp() {
        watches = mock(JobWatchRepository.class);
        matches = mock(JobMatchRepository.class);
        tracker = mock(TrackerService.class);
        tailor = mock(TailorService.class);
        kits = mock(ApplyKitService.class);
    }

    private AutoPrepareService service(int dailyCap) {
        return new AutoPrepareService(watches, matches, tracker, tailor, kits, dailyCap);
    }

    private JobWatchEntity watch(boolean autoPrepare, Long resumeId) {
        JobWatchEntity w = new JobWatchEntity();
        w.setOwnerKeyHash("owner");
        w.setName("India internships");
        w.setKinds(Set.of(JobKind.INTERNSHIP));
        w.setAutoPrepare(autoPrepare);
        w.setAutoResumeId(resumeId);
        w.setAutoGithubUsername("Aravind9548");
        setId(w);
        return w;
    }

    /** The id is database-assigned; the service looks matches up by it. */
    private static void setId(JobWatchEntity w) {
        try {
            var f = JobWatchEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(w, 1L);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private JobMatchEntity match(long postingId, JobMatchEntity.Status status, Instant matchedAt) {
        JobMatchEntity m = new JobMatchEntity();
        m.setPostingId(postingId);
        m.setStatus(status);
        m.setMatchedAt(matchedAt);
        return m;
    }

    /** Pretends the whole chain works, so the test can count how far it got. */
    private void happyPath() {
        when(tracker.approve(anyString(), anyLong())).thenAnswer(inv -> application(inv.getArgument(1)));
        when(tailor.start(anyString(), any(), any(), anyLong(), anyString())).thenReturn(run("A long enough job description to tailor against, with requirements."));
        when(tailor.suggest(anyString(), any(), any(), anyString())).thenReturn(run("A long enough job description to tailor against, with requirements."));
    }

    private TrackerService.ApplicationView application(long postingId) {
        return new TrackerService.ApplicationView(postingId + 500, postingId, "Acme", "SDE Intern", "https://x/apply",
                ApplicationStatus.APPROVED, ResumeChoice.TAILORED, null, null, null, null, Instant.now(),
                Instant.now(), List.of());
    }

    private TailorService.RunView run(String jobDescription) {
        return new TailorService.RunView(UUID.randomUUID(), TailorRunEntity.Step.REVIEW_EDITS, 1L, 1L, null, "Acme",
                "SDE Intern", jobDescription, null, null, List.of(1, 2), false, false, null, false, List.of(),
                "Aravind9548", "Keep it to one page.", true, "pro", null, null, null, null, null, Instant.now(),
                Instant.now());
    }

    @Test
    void preparesFreshMatchesForWatchesThatAskedForIt() {
        when(watches.findByActiveTrue()).thenReturn(List.of(watch(true, 7L)));
        when(matches.findByWatchIdAndEmailedAtIsNull(anyLong())).thenReturn(List.of(
                match(101, JobMatchEntity.Status.NEW, Instant.now()),
                match(102, JobMatchEntity.Status.NEW, Instant.now())));
        happyPath();

        AutoPrepareService.Run result = service(5).prepare("cron");

        assertThat(result.prepared()).isEqualTo(2);
        assertThat(result.errors()).isEmpty();
        verify(tailor, times(2)).build(eq("owner"), any(), eq(List.of(1, 2)), eq(false));
        verify(kits, times(2)).build(eq("owner"), anyLong(), eq("Aravind9548"), eq("pro"), anyString());
    }

    @Test
    void leavesAloneWatchesWithoutAResumeOrWithoutTheSwitchOn() {
        when(watches.findByActiveTrue()).thenReturn(List.of(watch(false, 7L), watch(true, null)));

        assertThat(service(5).prepare("cron").prepared()).isZero();
        verify(tracker, never()).approve(anyString(), anyLong());
    }

    @Test
    void skipsMatchesAlreadyDecidedOrOlderThanADay() {
        when(watches.findByActiveTrue()).thenReturn(List.of(watch(true, 7L)));
        when(matches.findByWatchIdAndEmailedAtIsNull(anyLong())).thenReturn(List.of(
                match(101, JobMatchEntity.Status.SKIPPED, Instant.now()),
                match(102, JobMatchEntity.Status.NEW, Instant.now().minus(3, ChronoUnit.DAYS))));
        happyPath();

        assertThat(service(5).prepare("cron").prepared()).isZero();
        verify(tracker, never()).approve(anyString(), anyLong());
    }

    @Test
    void stopsAtTheDailyCapCountingWhatWasAlreadyPreparedToday() {
        when(watches.findByActiveTrue()).thenReturn(List.of(watch(true, 7L)));
        when(kits.preparedToday("owner")).thenReturn(1L);
        when(matches.findByWatchIdAndEmailedAtIsNull(anyLong())).thenReturn(List.of(
                match(101, JobMatchEntity.Status.NEW, Instant.now()),
                match(102, JobMatchEntity.Status.NEW, Instant.now()),
                match(103, JobMatchEntity.Status.NEW, Instant.now())));
        happyPath();

        AutoPrepareService.Run result = service(2).prepare("cron");

        assertThat(result.prepared()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(2);
    }

    @Test
    void onePostingFailingDoesNotStopTheRest() {
        when(watches.findByActiveTrue()).thenReturn(List.of(watch(true, 7L)));
        when(matches.findByWatchIdAndEmailedAtIsNull(anyLong())).thenReturn(List.of(
                match(101, JobMatchEntity.Status.NEW, Instant.now()),
                match(102, JobMatchEntity.Status.NEW, Instant.now())));
        happyPath();
        when(tracker.approve("owner", 101L)).thenThrow(new IllegalStateException("posting closed"));

        AutoPrepareService.Run result = service(5).prepare("cron");

        assertThat(result.prepared()).isEqualTo(1);
        assertThat(result.errors()).containsExactly("posting closed");
    }

    @Test
    void withoutAJobDescriptionItTailorsNothing() {
        when(watches.findByActiveTrue()).thenReturn(List.of(watch(true, 7L)));
        when(matches.findByWatchIdAndEmailedAtIsNull(anyLong())).thenReturn(List.of(
                match(101, JobMatchEntity.Status.NEW, Instant.now())));
        when(tracker.approve(anyString(), anyLong())).thenAnswer(inv -> application(inv.getArgument(1)));
        when(tailor.start(anyString(), any(), any(), anyLong(), anyString())).thenReturn(run(""));

        service(5).prepare("cron");

        verify(tailor, never()).suggest(anyString(), any(), any(), anyString());
        verify(kits, never()).build(anyString(), anyLong(), any(), any(), anyString());
    }
}
