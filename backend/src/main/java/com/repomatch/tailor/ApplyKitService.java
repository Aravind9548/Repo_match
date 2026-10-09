package com.repomatch.tailor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomatch.ai.AiServiceClient;
import com.repomatch.analysis.UsageGuard;
import com.repomatch.tracker.ApplicationEntity;
import com.repomatch.tracker.ApplicationRepository;

/**
 * The apply kit: everything needed to fill one application in a minute — the person's standing answers, and the
 * job-specific ones drafted from their resume and repositories.
 *
 * <p>Nothing is submitted anywhere. The person opens the employer's form, fills it (by hand or with the autofill
 * helper), reads it, and presses submit themselves.
 */
@Service
public class ApplyKitService {

    private final ApplicantProfileRepository profiles;
    private final ApplyKitRepository kits;
    private final ApplicationRepository applications;
    private final TailorRunRepository runs;
    private final ResumeDocRepository resumes;
    private final JobDescriptionService descriptions;
    private final TailorService tailor;
    private final AiServiceClient ai;
    private final UsageGuard usage;
    private final ObjectMapper mapper;

    public ApplyKitService(ApplicantProfileRepository profiles, ApplyKitRepository kits,
                           ApplicationRepository applications, TailorRunRepository runs, ResumeDocRepository resumes,
                           JobDescriptionService descriptions, TailorService tailor, AiServiceClient ai,
                           UsageGuard usage, ObjectMapper mapper) {
        this.profiles = profiles;
        this.kits = kits;
        this.applications = applications;
        this.runs = runs;
        this.resumes = resumes;
        this.descriptions = descriptions;
        this.tailor = tailor;
        this.ai = ai;
        this.usage = usage;
        this.mapper = mapper;
    }

    public record Profile(String fullName, String email, String phone, String location, String githubUrl,
                          String linkedinUrl, String portfolioUrl, String workAuthorization, String noticePeriod,
                          String expectedSalary, String graduation, List<Extra> extras, Instant updatedAt) {

        public record Extra(String label, String value) {
        }
    }

    public record Kit(long applicationId, String company, String role, String applyUrl, Profile profile,
                      JsonNode answers, String model, UUID tailorRunId, boolean hasPdf, Instant createdAt) {
    }

    // --- profile -------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Profile profile(String owner) {
        return profiles.findById(owner).map(this::view).orElse(null);
    }

    @Transactional
    public Profile saveProfile(String owner, Profile in) {
        ApplicantProfileEntity p = profiles.findById(owner).orElseGet(ApplicantProfileEntity::new);
        p.setOwnerKeyHash(owner);
        p.setFullName(in.fullName());
        p.setEmail(in.email());
        p.setPhone(in.phone());
        p.setLocation(in.location());
        p.setGithubUrl(in.githubUrl());
        p.setLinkedinUrl(in.linkedinUrl());
        p.setPortfolioUrl(in.portfolioUrl());
        p.setWorkAuthorization(in.workAuthorization());
        p.setNoticePeriod(in.noticePeriod());
        p.setExpectedSalary(in.expectedSalary());
        p.setGraduation(in.graduation());
        p.setExtras(write(in.extras() == null ? List.of() : in.extras()));
        p.setUpdatedAt(Instant.now());
        return view(profiles.save(p));
    }

    // --- kit -----------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Kit kit(String owner, long applicationId) {
        ApplicationEntity app = owned(owner, applicationId);
        return kits.findByApplicationIdAndOwnerKeyHash(applicationId, owner)
                .map(k -> view(app, k))
                .orElseGet(() -> new Kit(app.getId(), app.getCompany(), app.getRole(), app.getApplyUrl(),
                        profile(owner), null, null, null, false, null));
    }

    /** Drafts this job's answers. One paid call; resume and repos are reused from the tailoring run when there is one. */
    @Transactional
    public Kit build(String owner, long applicationId, String githubUsername, String model, String clientKey) {
        ApplicationEntity app = owned(owner, applicationId);
        TailorRunEntity run = runs.findByOwnerKeyHashAndApplicationIdOrderByUpdatedAtDesc(owner, applicationId)
                .stream().findFirst().orElse(null);

        JsonNode resume = run != null && run.getStructured() != null ? json(run.getStructured()) : latestResume(owner);
        if (resume == null) {
            throw new IllegalArgumentException("Add a resume first — the answers are drafted from it.");
        }
        String jd = run != null && run.getJobDescription() != null ? run.getJobDescription()
                : app.getPostingId() != null ? descriptions.forPosting(app.getPostingId()) : "";

        String username = githubUsername != null && !githubUsername.isBlank() ? githubUsername.strip()
                : run != null ? run.getGithubUsername() : null;
        List<Map<String, Object>> repos = List.of();
        if (username != null && !username.isBlank() && !jd.isBlank()) {
            repos = tailor.githubEvidence(username, jd).evidence();
        }

        usage.acquireLlmCall(clientKey);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("job_title", app.getRole() == null ? "" : app.getRole());
        body.put("company", app.getCompany() == null ? "" : app.getCompany());
        body.put("job_description", jd);
        body.put("profile", profileText(owner));
        body.put("resume", resume);
        body.put("repos", repos);
        body.put("model", "flash".equals(model) ? "flash" : "pro");
        JsonNode res = ai.applyAnswers(body);

        ApplyKitEntity kit = kits.findByApplicationIdAndOwnerKeyHash(applicationId, owner).orElseGet(ApplyKitEntity::new);
        kit.setOwnerKeyHash(owner);
        kit.setApplicationId(applicationId);
        kit.setTailorRunId(run == null ? null : run.getId());
        kit.setAnswers(res.path("answers").toString());
        kit.setModel(res.path("model").asText(""));
        kit.setCreatedAt(Instant.now());
        return view(app, kits.save(kit));
    }

    /** The person's own details as plain text for the prompt — only what they typed themselves. */
    String profileText(String owner) {
        ApplicantProfileEntity p = profiles.findById(owner).orElse(null);
        if (p == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        add(lines, "Name", p.getFullName());
        add(lines, "Based in", p.getLocation());
        add(lines, "Work authorisation", p.getWorkAuthorization());
        add(lines, "Availability / notice", p.getNoticePeriod());
        add(lines, "Graduation", p.getGraduation());
        add(lines, "Expected pay", p.getExpectedSalary());
        add(lines, "GitHub", p.getGithubUrl());
        add(lines, "LinkedIn", p.getLinkedinUrl());
        add(lines, "Portfolio", p.getPortfolioUrl());
        for (Profile.Extra e : extras(p.getExtras())) {
            add(lines, e.label(), e.value());
        }
        return String.join("\n", lines);
    }

    private static void add(List<String> lines, String label, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(label + ": " + value.strip());
        }
    }

    private JsonNode latestResume(String owner) {
        return resumes.findByOwnerKeyHashOrderByCreatedAtDesc(owner).stream()
                .filter(d -> d.getStructured() != null)
                .findFirst()
                .map(d -> json(d.getStructured()))
                .orElse(null);
    }

    /** How many tailoring runs this person produced today — what the auto-prepare cap counts. */
    @Transactional(readOnly = true)
    public long preparedToday(String owner) {
        Instant since = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        return runs.findByOwnerKeyHash(owner).stream().filter(r -> r.getCreatedAt().isAfter(since)).count();
    }

    private ApplicationEntity owned(String owner, long id) {
        return applications.findByIdAndOwnerKeyHash(id, owner)
                .orElseThrow(() -> new TailorService.TailorNotFoundException("Application " + id));
    }

    private Kit view(ApplicationEntity app, ApplyKitEntity k) {
        boolean hasPdf = k.getTailorRunId() != null
                && runs.findById(k.getTailorRunId()).map(r -> r.getPdf() != null).orElse(false);
        return new Kit(app.getId(), app.getCompany(), app.getRole(), app.getApplyUrl(), profile(k.getOwnerKeyHash()),
                json(k.getAnswers()), k.getModel(), k.getTailorRunId(), hasPdf, k.getCreatedAt());
    }

    private Profile view(ApplicantProfileEntity p) {
        return new Profile(p.getFullName(), p.getEmail(), p.getPhone(), p.getLocation(), p.getGithubUrl(),
                p.getLinkedinUrl(), p.getPortfolioUrl(), p.getWorkAuthorization(), p.getNoticePeriod(),
                p.getExpectedSalary(), p.getGraduation(), extras(p.getExtras()), p.getUpdatedAt());
    }

    private List<Profile.Extra> extras(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(mapper.readValue(json, Profile.Extra[].class));
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private JsonNode json(String s) {
        try {
            return s == null ? null : mapper.readTree(s);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String write(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
