package com.repomatch.workflow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.repomatch.ai.dto.ExplainResponse;
import com.repomatch.analysis.MatchResult;
import com.repomatch.api.AccountSummary;
import com.repomatch.repo.RepoEntity;

/** What the workflow UI renders: the current gate plus every step's data and decisions so far. */
public record WorkflowView(
        UUID id,
        AccountSummary account,
        String jobDescription,
        int topK,
        WorkflowStep step,
        List<RepoOption> repos,
        List<RankedOption> ranking,
        List<MatchResult> judgements,
        List<Long> acceptedIds,
        String embeddingModel,
        String llmModel,
        Usage usage,
        List<String> warnings,
        UUID analysisId,
        Instant createdAt,
        Instant updatedAt) {

    public record RepoOption(long id, String name, String fullName, String url, String description,
                             List<String> languages, int stars, boolean fork, boolean hasReadme, Instant pushedAt,
                             boolean selected) {

        static RepoOption of(RepoEntity r, boolean selected) {
            return new RepoOption(r.getId(), r.getName(), r.getFullName(), r.getHtmlUrl(), r.getDescription(),
                    r.languageNames(), r.getStars(), r.isFork(), r.getReadme() != null, r.getPushedAt(), selected);
        }
    }

    public record RankedOption(long id, String name, String url, double similarity, boolean hasReadme,
                               boolean shortlisted) {
    }

    /** Gemini token counts for the judge step — what it cost. */
    public record Usage(int promptTokens, int outputTokens, int thinkingTokens) {

        static Usage of(ExplainResponse.Usage u) {
            return u == null ? null : new Usage(u.promptTokens(), u.outputTokens(), u.thinkingTokens());
        }
    }
}
