package com.repomatch.analysis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Compact history entry: which JD, when, and the top picks. */
public record AnalysisSummary(UUID id, String jobDescriptionPreview, List<String> topRepos, Instant createdAt) {

    private static final int PREVIEW_CHARS = 160;

    static AnalysisSummary of(AnalysisEntity e) {
        String jd = e.getJobDescription().replaceAll("\\s+", " ").strip();
        String preview = jd.length() > PREVIEW_CHARS ? jd.substring(0, PREVIEW_CHARS) + "…" : jd;
        List<String> top = e.getResults().matches().stream().map(MatchResult::name).toList();
        return new AnalysisSummary(e.getId(), preview, top, e.getCreatedAt());
    }
}
