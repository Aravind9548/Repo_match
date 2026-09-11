package com.repomatch.analysis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.repomatch.api.AccountSummary;

public record AnalyzeResponse(
        UUID id,
        AccountSummary account,
        String jobDescription,
        int reposAnalyzed,
        String embeddingModel,
        String llmModel,
        List<MatchResult> matches,
        List<AnalysisResults.OtherRepo> otherRepos,
        List<String> warnings,
        Instant createdAt) {

    static AnalyzeResponse of(AnalysisEntity e, AccountSummary account) {
        AnalysisResults r = e.getResults();
        return new AnalyzeResponse(e.getId(), account, e.getJobDescription(), r.reposAnalyzed(),
                e.getEmbeddingModel(), e.getLlmModel(),
                r.matches(), r.otherRepos(), r.warnings(), e.getCreatedAt());
    }
}
