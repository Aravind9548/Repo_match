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
                          String instructions, boolean onePage, String model, Integer pages, JsonNode additions,
                          JsonNode manualEdits, JsonNode ats, Instant atsCheckedAt, Instant createdAt,
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

        // The person's own corrections go last, so a line they typed wins over a line the model proposed.
        array(run.getManualEdits()).forEach(edits::add);

        ResumeDocEntity doc = run.getResumeId() == null ? null : resumes.findById(run.getResumeId()).orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resume", json(run.getStructured()));
        body.put("edits", edits);
        body.put("additions", array(run.getAdditions()));
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
        clearAts(run);

        if (run.getApplicationId() != null && run.isCompiled()) {
            tracker.update(owner, run.getApplicationId(), new TrackerService.Update(null, ResumeChoice.TAILORED, null, null,
                    null, null, "Tailored resume built (" + keep.size() + " edit" + (keep.size() == 1 ? "" : "s") + ")"));
        }
        return save(run);
    }

    /** A line the person writes themselves to answer something the job asks for and the resume didn't show. */
    public record AdditionInput(String section, String itemTitle, String text, String gap) {
    }

    /** A correction typed straight over the final resume. */
    public record ManualEditInput(String section, String before, String after) {
    }

    /**
     * Adds a line in the person's own words. No model call: the text is theirs, stored as they typed it.
     *
     * <p>This is the only way a gap the job asks for reaches the resume, and it goes in as a description of
     * something they did — never as the job ad's word pasted in.
     */
    @Transactional
    public RunView addLine(String owner, UUID id, AdditionInput in) {
        TailorRunEntity run = owned(owner, id);
        String text = in.text() == null ? "" : in.text().strip();
        if (text.length() < 3) {
            throw new IllegalArgumentException("Write the line first — a few words about what you did.");
        }
        ArrayNode additions = array(run.getAdditions());
        additions.add(mapper.createObjectNode()
                .put("section", in.section() == null || in.section().isBlank() ? "Projects" : in.section().strip())
                .put("item_title", in.itemTitle() == null ? "" : in.itemTitle().strip())
                .put("text", text)
                .put("gap", in.gap() == null ? "" : in.gap().strip()));
        run.setAdditions(additions.toString());
        clearAts(run);
        return save(run);
    }

    @Transactional
    public RunView removeLine(String owner, UUID id, int index) {
        TailorRunEntity run = owned(owner, id);
        ArrayNode additions = array(run.getAdditions());
        if (index < 0 || index >= additions.size()) {
            throw new IllegalArgumentException("That line is already gone.");
        }
        additions.remove(index);
        run.setAdditions(additions.toString());
        clearAts(run);
        return save(run);
    }

    /**
     * Records a correction the person typed over the final resume. One per line: typing over the same line again
     * replaces the previous correction rather than stacking.
     */
    @Transactional
    public RunView editLine(String owner, UUID id, ManualEditInput in) {
        TailorRunEntity run = owned(owner, id);
        String before = in.before() == null ? "" : in.before().strip();
        String after = in.after() == null ? "" : in.after().strip();
        if (before.isBlank()) {
            throw new IllegalArgumentException("Nothing to correct — the original line is missing.");
        }
        ArrayNode kept = mapper.createArrayNode();
        array(run.getManualEdits()).forEach(e -> {
            if (!before.equalsIgnoreCase(e.path("before").asText())) {
                kept.add(e);
            }
        });
        if (!after.isBlank() && !after.equals(before)) {
            kept.add(mapper.createObjectNode()
                    .put("section", in.section() == null ? "" : in.section())
                    .put("before", before)
                    .put("after", after)
                    .put("reason", "You wrote this one."));
        }
        run.setManualEdits(kept.toString());
        clearAts(run);
        return save(run);
    }

    /**
     * Help with a gap: questions that jog the memory, and drafts that restate the person's own resume or
     * repositories. One cheap model call. Nothing is added to the resume here — the person edits a draft and adds
     * it themselves, or writes their own line.
     */
    public JsonNode gapIdeas(String owner, UUID id, String gap, String clientKey) {
        TailorRunEntity run = owned(owner, id);
        if (gap == null || gap.isBlank()) {
            throw new IllegalArgumentException("Which gap?");
        }
        String jd = run.getJobDescription() == null ? "" : run.getJobDescription();
        List<Map<String, Object>> repos = List.of();
        if (run.getGithubUsername() != null && !run.getGithubUsername().isBlank() && !jd.isBlank()) {
            repos = githubEvidence(run.getGithubUsername(), jd).evidence();
        }

        usage.acquireLlmCall(clientKey);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("gap", gap.strip());
        body.put("job_title", run.getJobTitle() == null ? "" : run.getJobTitle());
        body.put("job_description", jd);
        body.put("resume", json(run.getStructured()));
        body.put("repos", repos);
        body.put("model", "flash");
        return ai.resume("gap-ideas", body);
    }

    /** Tightens the person's own sentence into a bullet. One cheap model call; it may add nothing they didn't say. */
    public JsonNode phrase(String owner, UUID id, String sentence, String clientKey) {
        TailorRunEntity run = owned(owner, id);
        if (sentence == null || sentence.strip().length() < 3) {
            throw new IllegalArgumentException("Write a sentence first.");
        }
        usage.acquireLlmCall(clientKey);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sentence", sentence.strip());
        body.put("resume", json(run.getStructured()));
        body.put("model", "flash");
        return ai.resume("phrase", body);
    }

    /**
     * The resume exactly as it would be built, with the given edits kept plus the person's own lines and
     * corrections. No model call — the same function the build uses.
     */
    public JsonNode preview(String owner, UUID id, List<Integer> acceptedIds) {
        TailorRunEntity run = owned(owner, id);
        List<Integer> keep = acceptedIds != null ? acceptedIds
                : run.getAcceptedIds() == null ? List.of() : readInts(run.getAcceptedIds());
        JsonNode plan = json(run.getPlan());
        Set<Integer> kept = new HashSet<>(keep);

        ArrayNode edits = mapper.createArrayNode();
        if (plan != null) {
            plan.path("edits").forEach(e -> {
                if (kept.contains(e.path("id").asInt())) {
                    edits.add(e);
                }
            });
        }
        array(run.getManualEdits()).forEach(edits::add);

        ArrayNode swaps = mapper.createArrayNode();
        ArrayNode links = mapper.createArrayNode();
        if (plan != null) {
            plan.path("swaps").forEach(x -> {
                if (kept.contains(x.path("id").asInt())) {
                    swaps.add(x);
                }
            });
            plan.path("link_additions").forEach(x -> {
                if (kept.contains(x.path("id").asInt())) {
                    links.add(x);
                }
            });
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resume", json(run.getStructured()));
        body.put("edits", edits);
        body.put("reorder", plan == null ? mapper.createArrayNode() : plan.path("reorder"));
        body.put("swaps", swaps);
        body.put("link_additions", links);
        body.put("additions", array(run.getAdditions()));
        return ai.resume("apply", body);
    }

    /**
     * Reads the built PDF back the way an applicant tracking system would, and checks the job's requirements against
     * what came out of the file — not against the resume's source, which is not what gets sent.
     *
     * <p>The parse half is deterministic. The coverage half costs one model call, to list what the ad asks for; the
     * matching is plain string work. It is a readability check and a coverage count, not a prediction of any
     * employer's own score.
     */
    @Transactional
    public RunView atsCheck(String owner, UUID id, String model, String clientKey) {
        TailorRunEntity run = owned(owner, id);
        if (run.getPdf() == null) {
            throw new TailorStepException("Build the PDF first — the check reads the file that would be sent.");
        }
        String jd = run.getJobDescription() == null ? "" : run.getJobDescription();
        if (jd.strip().length() >= 40) {
            usage.acquireLlmCall(clientKey); // the requirements call; the parse half is free
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pdf_base64", Base64.getEncoder().encodeToString(run.getPdf()));
        body.put("job_title", run.getJobTitle() == null ? "" : run.getJobTitle());
        body.put("job_description", jd);
        body.put("model", "pro".equals(model) ? "pro" : "flash");
        JsonNode report = ai.resume("ats", body);

        run.setAtsReport(report.toString());
        run.setAtsCheckedAt(Instant.now());
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

    record Repos(List<Map<String, Object>> evidence, List<Map<String, Object>> all) {
    }

    /**
     * The person's repositories as evidence: all of them (names and links, so missing project links can be added) and
     * the few most similar to this job with their READMEs. Similarity reuses Match's cached embeddings, so it's cheap.
     */
    Repos githubEvidence(String username, String jobDescription) {
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
        clearAts(run);
    }

    /** The report describes one PDF; the moment that PDF changes it stops being true. */
    private void clearAts(TailorRunEntity run) {
        run.setAtsReport(null);
        run.setAtsCheckedAt(null);
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
                array(r.getAdditions()), array(r.getManualEdits()), json(r.getAtsReport()), r.getAtsCheckedAt(),
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

    /** A stored JSON array, or an empty one — never null, so callers can just append. */
    private ArrayNode array(String s) {
        JsonNode node = json(s);
        return node != null && node.isArray() ? (ArrayNode) node : mapper.createArrayNode();
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
