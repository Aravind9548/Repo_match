package com.repomatch.tailor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.repomatch.ai.AiServiceClient;
import com.repomatch.analysis.MatchPipeline;
import com.repomatch.analysis.UsageGuard;
import com.repomatch.repo.RepoEntity;
import com.repomatch.repo.RepoSyncService;
import com.repomatch.jobs.JobFeedService;
import com.repomatch.tracker.ApplicationRepository;
import com.repomatch.tracker.ResumeChoice;
import com.repomatch.tracker.TrackerService;

/**
 * Tailors a resume to one job in gates, like the Match workflow:
 *
 * <ol>
 *   <li>REVIEW_RESUME — the resume as read (sections, items, bullets) and the job description. The person checks
 *       both, fixes anything misread, then asks for suggestions.</li>
 *   <li>REVIEW_EDITS — proposed edits, each with a reason; wording that adds anything the resume never said is
 *       flagged and off by default. The person picks which to keep.</li>
 *   <li>DONE — accepted edits applied, rendered to LaTeX (their own .tex, or the RepoMatch template) and compiled
 *       to a PDF they download.</li>
 * </ol>
 *
 * <p>Paid model calls happen only on explicit clicks: reading a resume the first time, and asking for edits.
 * Building the PDF costs nothing. Any gate can be revisited.
 */
@Service
public class TailorService {

    static final int MAX_UPLOAD_BYTES = 3_000_000;
    /** Repositories whose READMEs are shown to the model: the most job-relevant few, to keep the prompt focused. */
    static final int EVIDENCE_REPOS = 6;
    static final int README_CHARS = 2_500;

    private final ResumeDocRepository resumes;
    private final TailorRunRepository runs;
    private final ApplicationRepository applications;
    private final JobFeedService feed;
    private final JobDescriptionService descriptions;
    private final TrackerService tracker;
    private final AiServiceClient ai;
    private final UsageGuard usage;
    private final ObjectMapper mapper;
    private final RepoSyncService repoSync;
    private final MatchPipeline pipeline;

    public TailorService(ResumeDocRepository resumes, TailorRunRepository runs, ApplicationRepository applications,
                         JobFeedService feed, JobDescriptionService descriptions, TrackerService tracker,
                         AiServiceClient ai, UsageGuard usage, ObjectMapper mapper, RepoSyncService repoSync,
                         MatchPipeline pipeline) {
        this.repoSync = repoSync;
        this.pipeline = pipeline;
        this.resumes = resumes;
        this.runs = runs;
        this.applications = applications;
        this.feed = feed;
        this.descriptions = descriptions;
        this.tracker = tracker;
        this.ai = ai;
        this.usage = usage;
        this.mapper = mapper;
    }

    // --- resumes -------------------------------------------------------------------------------------------------

    public record ResumeView(long id, String filename, String kind, int chars, boolean read, Instant createdAt) {
    }

    @Transactional(readOnly = true)
    public List<ResumeView> listResumes(String owner) {
        return resumes.findByOwnerKeyHashOrderByCreatedAtDesc(owner).stream().map(TailorService::view).toList();
    }

    /** Stores an uploaded file (PDF, DOCX, .tex, text) or pasted text as the person's resume. No model call. */
    @Transactional
    public ResumeView addResume(String owner, String filename, String base64, String pastedText) {
        String text;
        String kind;
        if (base64 != null && !base64.isBlank()) {
            if (base64.length() > MAX_UPLOAD_BYTES * 4 / 3 + 16) {
                throw new IllegalArgumentException("That file is over 3 MB. Upload a smaller PDF/DOCX, or paste the text.");
            }
            JsonNode extracted = ai.resume("extract", Map.of("filename", filename == null ? "resume" : filename, "data_base64", base64));
            text = extracted.path("text").asText();
            kind = "tex".equals(extracted.path("kind").asText()) ? ResumeDocEntity.TEX : ResumeDocEntity.TEXT;
        } else if (pastedText != null && pastedText.strip().length() >= 80) {
            text = pastedText.strip();
            kind = text.contains("\\begin{document}") ? ResumeDocEntity.TEX : ResumeDocEntity.TEXT;
        } else {
            throw new IllegalArgumentException("Upload a resume file or paste at least a few lines of it.");
        }
        ResumeDocEntity doc = new ResumeDocEntity();
        doc.setOwnerKeyHash(owner);
        doc.setFilename(filename == null || filename.isBlank() ? "Pasted resume" : filename.strip());
        doc.setKind(kind);
        doc.setSourceText(text);
        doc.setCreatedAt(Instant.now());
        return view(resumes.save(doc));
    }

    @Transactional
    public void deleteResume(String owner, long id) {
        resumes.delete(resumes.findByIdAndOwnerKeyHash(id, owner).orElseThrow(() -> new TailorNotFoundException("Resume " + id)));
    }

    // --- runs ----------------------------------------------------------------------------------------------------

    public record RunView(UUID id, TailorRunEntity.Step step, Long applicationId, Long postingId, ResumeView resume,
                          String company, String jobTitle, String jobDescription, JsonNode resumeData, JsonNode plan,
                          List<Integer> acceptedIds, boolean compiled, boolean hasPdf, String compileLog,
                          boolean usedOwnTemplate, List<Integer> missedEdits, String githubUsername,
                          String instructions, boolean onePage, String model, Integer pages, Instant createdAt,
                          Instant updatedAt) {
    }

    /** What the person asks for at the check step, alongside the (possibly corrected) resume and job description. */
    public record SuggestInput(JsonNode resume, String jobDescription, String githubUsername, String instructions,
                               boolean onePage, String model) {
    }

    /**
     * Starts a run for an application (or a posting) with a chosen resume. Reading the resume into sections is the
     * one model call here, and only the first time a resume is used.
     */
    @Transactional
    public RunView start(String owner, Long applicationId, Long postingId, long resumeId, String clientKey) {
        ResumeDocEntity doc = resumes.findByIdAndOwnerKeyHash(resumeId, owner)
                .orElseThrow(() -> new TailorNotFoundException("Resume " + resumeId));

        TailorRunEntity run = new TailorRunEntity();
        run.setId(UUID.randomUUID());
        run.setOwnerKeyHash(owner);
        run.setResumeId(doc.getId());
        if (applicationId != null) {
            var app = applications.findByIdAndOwnerKeyHash(applicationId, owner)
                    .orElseThrow(() -> new TailorNotFoundException("Application " + applicationId));
            run.setApplicationId(app.getId());
            run.setCompany(app.getCompany());
            run.setJobTitle(app.getRole());
            postingId = postingId != null ? postingId : app.getPostingId();
        }
        if (postingId != null) {
            var posting = feed.get(postingId);
            run.setPostingId(posting.id());
            if (run.getCompany() == null) {
                run.setCompany(posting.company());
                run.setJobTitle(posting.role());
            }
            run.setJobDescription(descriptions.forPosting(posting.id()));
        }

        if (doc.getStructured() == null) {
            usage.acquireLlmCall(clientKey);
            JsonNode res = ai.resume("structure", Map.of("text", doc.getSourceText()));
            doc.setStructured(res.path("resume").toString());
            resumes.save(doc);
        }
        run.setStructured(doc.getStructured());
        run.setStep(TailorRunEntity.Step.REVIEW_RESUME);
        run.setCreatedAt(Instant.now());
        run.setUpdatedAt(run.getCreatedAt());
        return view(runs.save(run), doc);
    }

    @Transactional(readOnly = true)
    public RunView get(String owner, UUID id) {
        TailorRunEntity run = owned(owner, id);
        return view(run, run.getResumeId() == null ? null : resumes.findById(run.getResumeId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<RunView> forApplication(String owner, long applicationId) {
        return runs.findByOwnerKeyHashAndApplicationIdOrderByUpdatedAtDesc(owner, applicationId).stream()
                .map(r -> view(r, null)).toList();
    }

    /** Gate 1 → 2: the person has checked the resume and job description (and may have corrected either). */
    @Transactional
    public RunView suggest(String owner, UUID id, SuggestInput in, String clientKey) {
        TailorRunEntity run = owned(owner, id);
        requireStep(run, TailorRunEntity.Step.REVIEW_RESUME);
        if (in.jobDescription() == null || in.jobDescription().strip().length() < 40) {
            throw new IllegalArgumentException("Add the job description (at least a few lines) so the edits fit this job.");
        }
        if (in.resume() != null && in.resume().has("sections")) {
            run.setStructured(in.resume().toString());
        }
        run.setJobDescription(in.jobDescription().strip());
        run.setGithubUsername(in.githubUsername() == null || in.githubUsername().isBlank() ? null : in.githubUsername().strip());
        run.setInstructions(in.instructions() == null || in.instructions().isBlank() ? null : in.instructions().strip());
        run.setOnePage(in.onePage());
        run.setModel("flash".equals(in.model()) ? "flash" : "pro");

        usage.acquireLlmCall(clientKey);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resume", json(run.getStructured()));
        body.put("job_title", run.getJobTitle() == null ? "" : run.getJobTitle());
        body.put("job_description", run.getJobDescription());
        body.put("instructions", run.getInstructions() == null ? "" : run.getInstructions());
        body.put("one_page", run.isOnePage());
        body.put("model", run.getModel());
        if (run.getGithubUsername() != null) {
            Repos repos = githubEvidence(run.getGithubUsername(), run.getJobDescription());
            body.put("repos", repos.evidence());
            body.put("link_repos", repos.all());
        }
        JsonNode plan = ai.resume("tailor", body);

        run.setPlan(plan.toString());
        // Honest edits and missing GitHub links start accepted; anything flagged, and every project swap, waits for
        // the person to opt in.
        List<Integer> accepted = new ArrayList<>();
        plan.path("edits").forEach(e -> {
            if (!e.path("unsupported").asBoolean(false)) {
                accepted.add(e.path("id").asInt());
            }
        });
        plan.path("link_additions").forEach(l -> accepted.add(l.path("id").asInt()));
        run.setAcceptedIds(write(accepted));
        clearBuild(run);
        run.setStep(TailorRunEntity.Step.REVIEW_EDITS);
        return save(run);
    }

    /** Gate 2 → done: apply the kept edits, render LaTeX and compile the PDF. No model call. */
    @Transactional
    public RunView build(String owner, UUID id, List<Integer> acceptedIds, boolean keepOrder) {
        TailorRunEntity run = owned(owner, id);
        if (run.getStep() == TailorRunEntity.Step.REVIEW_RESUME) {
            throw new TailorStepException("Get suggestions first — there's nothing to build yet.");
        }
        JsonNode plan = json(run.getPlan());
        Set<Integer> keep = new HashSet<>(acceptedIds == null ? List.of() : acceptedIds);
        ArrayNode edits = mapper.createArrayNode();
        plan.path("edits").forEach(e -> {
            if (keep.contains(e.path("id").asInt())) {
                edits.add(mapper.createObjectNode().put("section", e.path("section").asText())
                        .put("before", e.path("before").asText()).put("after", e.path("after").asText())
                        .put("reason", e.path("reason").asText()));
            }
        });
        ArrayNode swaps = mapper.createArrayNode();
        plan.path("swaps").forEach(s -> {
            if (keep.contains(s.path("id").asInt())) {
                swaps.add(s);
            }
        });
        ArrayNode links = mapper.createArrayNode();
        plan.path("link_additions").forEach(l -> {
            if (keep.contains(l.path("id").asInt())) {
                links.add(l);
            }
        });

        ResumeDocEntity doc = run.getResumeId() == null ? null : resumes.findById(run.getResumeId()).orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resume", json(run.getStructured()));
        body.put("edits", edits);
        body.put("reorder", keepOrder ? mapper.createArrayNode() : plan.path("reorder"));
        body.put("swaps", swaps);
        body.put("link_additions", links);
        body.put("one_page", run.isOnePage());
        body.put("source_tex", doc != null && ResumeDocEntity.TEX.equals(doc.getKind()) ? doc.getSourceText() : null);
        JsonNode built = ai.resume("build", body);

        run.setAcceptedIds(write(new ArrayList<>(keep)));
        run.setLatex(built.path("latex").asText());
        run.setCompiled(built.path("compiled").asBoolean(false));
        run.setCompileLog(built.path("compile_log").asText(""));
        run.setUsedOwnTemplate(built.path("used_own_template").asBoolean(false));
        run.setMissedEdits(built.path("missed_edits").toString());
        run.setPages(built.path("pages").asInt(0));
        String pdf = built.path("pdf_base64").asText("");
        run.setPdf(pdf.isBlank() ? null : Base64.getDecoder().decode(pdf));
        run.setStep(TailorRunEntity.Step.DONE);

        if (run.getApplicationId() != null && run.isCompiled()) {
            tracker.update(owner, run.getApplicationId(), new TrackerService.Update(null, ResumeChoice.TAILORED, null, null,
                    null, null, "Tailored resume built (" + keep.size() + " edit" + (keep.size() == 1 ? "" : "s") + ")"));
        }
        return save(run);
    }

    /** Go back to an earlier gate, keeping what was decided there. */
    @Transactional
    public RunView rewind(String owner, UUID id, TailorRunEntity.Step target) {
        TailorRunEntity run = owned(owner, id);
        if (target == TailorRunEntity.Step.DONE || target.ordinal() >= run.getStep().ordinal()) {
            throw new TailorStepException("You can only go back to an earlier step.");
        }
        run.setStep(target);
        if (target == TailorRunEntity.Step.REVIEW_RESUME) {
            clearBuild(run);
        }
        return save(run);
    }

    private record Repos(List<Map<String, Object>> evidence, List<Map<String, Object>> all) {
    }

    /**
     * The person's repositories as evidence: all of them (names and links, so missing project links can be added) and
     * the few most similar to this job with their READMEs. Similarity reuses Match's cached embeddings, so it's cheap.
     */
    private Repos githubEvidence(String username, String jobDescription) {
        List<RepoEntity> owned;
        try {
            owned = MatchPipeline.eligible(repoSync.sync(username, false).repos(), false);
        } catch (com.repomatch.github.GitHubUserNotFoundException e) {
            throw new IllegalArgumentException("GitHub user \"" + username + "\" doesn't exist.");
        }
        List<Map<String, Object>> all = owned.stream().map(r -> repoMap(r, false)).toList();
        if (owned.isEmpty()) {
            return new Repos(List.of(), List.of());
        }
        Map<Long, RepoEntity> byId = MatchPipeline.index(owned);
        List<Map<String, Object>> evidence = pipeline.rank(jobDescription, owned).results().stream()
                .map(ranked -> byId.get(ranked.id()))
                .filter(r -> r != null && ((r.getReadme() != null && r.getReadme().length() > 80) || r.getDescription() != null))
                .limit(EVIDENCE_REPOS)
                .map(r -> repoMap(r, true))
                .toList();
        return new Repos(evidence, all);
    }

    private static Map<String, Object> repoMap(RepoEntity r, boolean withReadme) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", r.getName());
        m.put("url", r.getHtmlUrl());
        m.put("description", r.getDescription() == null ? "" : r.getDescription());
        m.put("languages", r.getLanguages() == null ? List.of() : r.getLanguages().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).map(Map.Entry::getKey).limit(5).toList());
        String readme = withReadme && r.getReadme() != null ? r.getReadme() : "";
        m.put("readme", readme.length() > README_CHARS ? readme.substring(0, README_CHARS) : readme);
        return m;
    }

    public record Download(String filename, byte[] bytes, String contentType) {
    }

    @Transactional(readOnly = true)
    public Download download(String owner, UUID id, String format) {
        TailorRunEntity run = owned(owner, id);
        String base = (run.getCompany() == null ? "resume" : run.getCompany()).replaceAll("[^A-Za-z0-9]+", "_") + "_resume";
        if ("tex".equals(format)) {
            if (run.getLatex() == null) {
                throw new TailorStepException("Build the resume first.");
            }
            return new Download(base + ".tex", run.getLatex().getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/x-tex");
        }
        if (run.getPdf() == null) {
            throw new TailorStepException("There's no PDF for this run" + (run.getLatex() != null ? " — the LaTeX didn't compile; download the .tex instead." : "."));
        }
        return new Download(base + ".pdf", run.getPdf(), "application/pdf");
    }

    // --- helpers -------------------------------------------------------------------------------------------------

    private void clearBuild(TailorRunEntity run) {
        run.setLatex(null);
        run.setPdf(null);
        run.setCompiled(false);
        run.setCompileLog(null);
        run.setMissedEdits(null);
    }

    private RunView save(TailorRunEntity run) {
        run.setUpdatedAt(Instant.now());
        TailorRunEntity saved = runs.save(run);
        return view(saved, saved.getResumeId() == null ? null : resumes.findById(saved.getResumeId()).orElse(null));
    }

    private TailorRunEntity owned(String owner, UUID id) {
        return runs.findByIdAndOwnerKeyHash(id, owner).orElseThrow(() -> new TailorNotFoundException("Tailoring run " + id));
    }

    private static void requireStep(TailorRunEntity run, TailorRunEntity.Step step) {
        if (run.getStep() != step) {
            throw new TailorStepException("This run has moved on. Go back to that step to change it.");
        }
    }

    private RunView view(TailorRunEntity r, ResumeDocEntity doc) {
        List<Integer> accepted = r.getAcceptedIds() == null ? List.of() : readInts(r.getAcceptedIds());
        List<Integer> missed = r.getMissedEdits() == null ? List.of() : readInts(r.getMissedEdits());
        return new RunView(r.getId(), r.getStep(), r.getApplicationId(), r.getPostingId(), doc == null ? null : view(doc),
                r.getCompany(), r.getJobTitle(), r.getJobDescription(), json(r.getStructured()), json(r.getPlan()),
                accepted, r.isCompiled(), r.getPdf() != null, r.getCompileLog(), r.isUsedOwnTemplate(), missed,
                r.getGithubUsername(), r.getInstructions(), r.isOnePage(), r.getModel(), r.getPages(),
                r.getCreatedAt(), r.getUpdatedAt());
    }

    private static ResumeView view(ResumeDocEntity d) {
        return new ResumeView(d.getId(), d.getFilename(), d.getKind(), d.getSourceText().length(), d.getStructured() != null,
                d.getCreatedAt());
    }

    private JsonNode json(String s) {
        if (s == null) {
            return null;
        }
        try {
            return mapper.readTree(s);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String write(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Integer> readInts(String s) {
        try {
            return List.of(mapper.readValue(s, Integer[].class));
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    public static class TailorNotFoundException extends RuntimeException {
        public TailorNotFoundException(String what) {
            super(what + " doesn't exist");
        }
    }

    public static class TailorStepException extends RuntimeException {
        public TailorStepException(String message) {
            super(message);
        }
    }
}
