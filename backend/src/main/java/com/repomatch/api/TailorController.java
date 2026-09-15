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

    public record SuggestBody(JsonNode resume, @Size(max = 40_000) String jobDescription) {
    }

    @PostMapping("/runs/{id}/suggest")
    public TailorService.RunView suggest(@RequestHeader(JobsController.KEY_HEADER) String key, @PathVariable UUID id,
                                         @Valid @RequestBody SuggestBody body, HttpServletRequest http) {
        return tailor.suggest(keys.resolve(key), id, body.resume(), body.jobDescription(), http.getRemoteAddr());
    }

    public record BuildBody(List<Integer> acceptedIds, boolean keepOrder) {
    }

    @PostMapping("/runs/{id}/build")
    public TailorService.RunView build(@RequestHeader(JobsController.KEY_HEADER) String key, @PathVariable UUID id,
                                       @RequestBody BuildBody body) {
        return tailor.build(keys.resolve(key), id, body.acceptedIds(), body.keepOrder());
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
