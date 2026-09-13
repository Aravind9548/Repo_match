package com.repomatch.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.repomatch.jobs.OwnerKeys;
import com.repomatch.tracker.ApplicationStatus;
import com.repomatch.tracker.ResumeChoice;
import com.repomatch.tracker.TrackerService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The application tracker. Every call needs the tracker key. */
@RestController
@RequestMapping("/api/tracker")
public class TrackerController {

    private final TrackerService tracker;
    private final OwnerKeys keys;

    public TrackerController(TrackerService tracker, OwnerKeys keys) {
        this.tracker = tracker;
        this.keys = keys;
    }

    @GetMapping("/applications")
    public List<TrackerService.ApplicationView> list(@RequestHeader(JobsController.KEY_HEADER) String key) {
        return tracker.list(keys.resolve(key));
    }

    @GetMapping("/applications/{id}")
    public TrackerService.ApplicationView get(@RequestHeader(JobsController.KEY_HEADER) String key, @PathVariable long id) {
        return tracker.get(keys.resolve(key), id);
    }

    /** Approve a posting from the feed or an alert: this is the human "yes, I want this one". */
    @PostMapping("/postings/{postingId}/approve")
    public TrackerService.ApplicationView approve(@RequestHeader(JobsController.KEY_HEADER) String key,
                                                  @PathVariable long postingId) {
        return tracker.approve(keys.resolve(key), postingId);
    }

    @PostMapping("/postings/{postingId}/skip")
    public ResponseEntity<Void> skip(@RequestHeader(JobsController.KEY_HEADER) String key, @PathVariable long postingId) {
        tracker.skip(keys.resolve(key), postingId);
        return ResponseEntity.noContent().build();
    }

    public record ManualBody(@NotBlank @Size(max = 300) String company, @NotBlank @Size(max = 500) String role,
                             @Size(max = 2000) String applyUrl) {
    }

    @PostMapping("/applications")
    public TrackerService.ApplicationView addManual(@RequestHeader(JobsController.KEY_HEADER) String key,
                                                    @Valid @RequestBody ManualBody body) {
        return tracker.addManual(keys.resolve(key), body.company(), body.role(), body.applyUrl());
    }

    public record UpdateBody(ApplicationStatus status, ResumeChoice resumeChoice, @Size(max = 300) String nextStep,
                             LocalDate nextStepDue, Boolean clearNextStep, @Size(max = 10_000) String notes,
                             @Size(max = 1000) String eventNote) {
    }

    @PatchMapping("/applications/{id}")
    public TrackerService.ApplicationView update(@RequestHeader(JobsController.KEY_HEADER) String key,
                                                 @PathVariable long id, @Valid @RequestBody UpdateBody b) {
        return tracker.update(keys.resolve(key), id, new TrackerService.Update(b.status(), b.resumeChoice(), b.nextStep(),
                b.nextStepDue(), b.clearNextStep(), b.notes(), b.eventNote()));
    }

    @DeleteMapping("/applications/{id}")
    public ResponseEntity<Void> delete(@RequestHeader(JobsController.KEY_HEADER) String key, @PathVariable long id) {
        tracker.delete(keys.resolve(key), id);
        return ResponseEntity.noContent().build();
    }
}
