package com.repomatch.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.repomatch.jobs.OwnerKeys;
import com.repomatch.tailor.JobDescriptionService;
import com.repomatch.tailor.TailorRunEntity;
import com.repomatch.tailor.TailorService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Resume tailoring. Every call needs the tracker key; paid steps are separate, explicit calls. */
@RestController
@RequestMapping("/api/tailor")
public class TailorController {

    private final TailorService tailor;
    private final JobDescriptionService descriptions;
    private final OwnerKeys keys;

    public TailorController(TailorService tailor, JobDescriptionService descriptions, OwnerKeys keys) {
        this.tailor = tailor;
        this.descriptions = descriptions;
        this.keys = keys;
    }

    @GetMapping("/resumes")
    public List<TailorService.ResumeView> resumes(@RequestHeader(JobsController.KEY_HEADER) String key) {
        return tailor.listResumes(keys.resolve(key));
    }

    public record ResumeBody(@Size(max = 300) String filename, @Size(max = 4_100_000) String dataBase64,
                             @Size(max = 60_000) String text) {
    }

    @PostMapping("/resumes")
    public TailorService.ResumeView addResume(@RequestHeader(JobsController.KEY_HEADER) String key,
                                              @Valid @RequestBody ResumeBody body) {
        return tailor.addResume(keys.resolve(key), body.filename(), body.dataBase64(), body.text());
    }

    @DeleteMapping("/resumes/{id}")
    public ResponseEntity<Void> deleteResume(@RequestHeader(JobsController.KEY_HEADER) String key, @PathVariable long id) {
        tailor.deleteResume(keys.resolve(key), id);
        return ResponseEntity.noContent().build();
    }

    public record DescriptionView(long postingId, String text) {
    }

    /** The posting's full description, fetched from its source once and cached. Free. */
    @GetMapping("/postings/{postingId}/description")
    public DescriptionView description(@PathVariable long postingId) {
        return new DescriptionView(postingId, descriptions.forPosting(postingId));
    }

    public record StartBody(Long applicationId, Long postingId, @NotNull Long resumeId) {
    }

    @PostMapping("/runs")
    public TailorService.RunView start(@RequestHeader(JobsController.KEY_HEADER) String key, @Valid @RequestBody StartBody body,
                                       HttpServletRequest http) {
        return tailor.start(keys.resolve(key), body.applicationId(), body.postingId(), body.resumeId(), http.getRemoteAddr());
    }

    @GetMapping("/runs/{id}")
    public TailorService.RunView get(@RequestHeader(JobsController.KEY_HEADER) String key, @PathVariable UUID id) {
        return tailor.get(keys.resolve(key), id);
    }

    @GetMapping("/applications/{applicationId}/runs")
    public List<TailorService.RunView> forApplication(@RequestHeader(JobsController.KEY_HEADER) String key,
                                                      @PathVariable long applicationId) {
        return tailor.forApplication(keys.resolve(key), applicationId);
    }

    public record SuggestBody(JsonNode resume, @Size(max = 40_000) String jobDescription,
                              @jakarta.validation.constraints.Pattern(regexp = "^$|^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$",
                                      message = "must be a valid GitHub username") String githubUsername,
                              @Size(max = 2000) String instructions, Boolean onePage,
                              @jakarta.validation.constraints.Pattern(regexp = "^(pro|flash)?$") String model) {
    }

    @PostMapping("/runs/{id}/suggest")
    public TailorService.RunView suggest(@RequestHeader(JobsController.KEY_HEADER) String key, @PathVariable UUID id,
                                         @Valid @RequestBody SuggestBody body, HttpServletRequest http) {
        return tailor.suggest(keys.resolve(key), id, new TailorService.SuggestInput(body.resume(), body.jobDescription(),
                body.githubUsername(), body.instructions(), body.onePage() == null || body.onePage(), body.model()),
                http.getRemoteAddr());
    }

    public record BuildBody(List<Integer> acceptedIds, boolean keepOrder) {
    }

    @PostMapping("/runs/{id}/build")
    public TailorService.RunView build(@RequestHeader(JobsController.KEY_HEADER) String key, @PathVariable UUID id,
                                       @RequestBody BuildBody body) {
        return tailor.build(keys.resolve(key), id, body.acceptedIds(), body.keepOrder());
    }

    public record AddLineBody(@Size(max = 80) String section, @Size(max = 200) String itemTitle,
                              @NotBlank @Size(max = 400) String text, @Size(max = 80) String gap) {
    }

    /** No AI call: the line is the person's own words, stored as typed. */
    @PostMapping("/runs/{id}/lines")
    public TailorService.RunView addLine(@RequestHeader(JobsController.KEY_HEADER) String key, @PathVariable UUID id,
                                         @Valid @RequestBody AddLineBody body) {
        return tailor.addLine(keys.resolve(key), id, new TailorService.AdditionInput(body.section(), body.itemTitle(),
                body.text(), body.gap()));
    }

    @DeleteMapping("/runs/{id}/lines/{index}")
    public TailorService.RunView removeLine(@RequestHeader(JobsController.KEY_HEADER) String key,
                                            @PathVariable UUID id, @PathVariable int index) {
        return tailor.removeLine(keys.resolve(key), id, index);
    }

    public record EditLineBody(@Size(max = 80) String section, @NotBlank @Size(max = 1000) String before,
                               @Size(max = 1000) String after) {
    }

    /** A correction typed over the final resume. No AI call. */
    @PostMapping("/runs/{id}/line-edit")
    public TailorService.RunView editLine(@RequestHeader(JobsController.KEY_HEADER) String key, @PathVariable UUID id,
                                          @Valid @RequestBody EditLineBody body) {
        return tailor.editLine(keys.resolve(key), id, new TailorService.ManualEditInput(body.section(), body.before(),
                body.after()));
    }

    public record GapBody(@NotBlank @Size(max = 120) String gap) {
    }

    /** Paid: one cheap call. Returns questions and drafts grounded in the person's own resume and repositories. */
    @PostMapping("/runs/{id}/gap-ideas")
    public JsonNode gapIdeas(@RequestHeader(JobsController.KEY_HEADER) String key, @PathVariable UUID id,
                             @Valid @RequestBody GapBody body, HttpServletRequest http) {
        return tailor.gapIdeas(keys.resolve(key), id, body.gap(), http.getRemoteAddr());
    }

    public record PhraseBody(@NotBlank @Size(max = 1500) String sentence) {
    }

    /** Paid: one cheap call tightening the person's own sentence. It may add nothing they didn't write. */
    @PostMapping("/runs/{id}/phrase")
    public JsonNode phrase(@RequestHeader(JobsController.KEY_HEADER) String key,
                                                          @PathVariable UUID id, @Valid @RequestBody PhraseBody body,
                                                          HttpServletRequest http) {
        return tailor.phrase(keys.resolve(key), id, body.sentence(), http.getRemoteAddr());
    }

    public record PreviewBody(List<Integer> acceptedIds) {
    }

    /** The resume as it would be built, so it can be read and corrected before compiling. No AI call. */
    @PostMapping("/runs/{id}/preview")
    public JsonNode preview(@RequestHeader(JobsController.KEY_HEADER) String key,
                                                           @PathVariable UUID id,
                                                           @RequestBody(required = false) PreviewBody body) {
        return tailor.preview(keys.resolve(key), id, body == null ? null : body.acceptedIds());
    }

    public record AtsBody(@jakarta.validation.constraints.Size(max = 10) String model) {
    }

    /** Paid when there's a job description: one model call lists what the ad asks for. The parse report is free. */
    @PostMapping("/runs/{id}/ats")
    public TailorService.RunView ats(@RequestHeader(JobsController.KEY_HEADER) String key, @PathVariable UUID id,
                                     @RequestBody(required = false) AtsBody body, HttpServletRequest http) {
        return tailor.atsCheck(keys.resolve(key), id, body == null ? null : body.model(), http.getRemoteAddr());
    }

    public record RewindBody(@NotNull TailorRunEntity.Step step) {
    }

    @PostMapping("/runs/{id}/rewind")
    public TailorService.RunView rewind(@RequestHeader(JobsController.KEY_HEADER) String key, @PathVariable UUID id,
                                        @Valid @RequestBody RewindBody body) {
        return tailor.rewind(keys.resolve(key), id, body.step());
    }

    /** The key comes as a query parameter too, so a plain download link works. */
    @GetMapping("/runs/{id}/download")
    public ResponseEntity<byte[]> download(@RequestHeader(value = JobsController.KEY_HEADER, required = false) String header,
                                           @RequestParam(value = "key", required = false) String param,
                                           @PathVariable UUID id, @RequestParam(defaultValue = "pdf") String format) {
        var file = tailor.download(keys.resolve(header != null ? header : param), id, format);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString())
                .body(file.bytes());
    }
}
