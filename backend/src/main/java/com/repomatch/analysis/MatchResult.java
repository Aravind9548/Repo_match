package com.repomatch.analysis;

import java.util.List;

/**
 * One recommended repo.
 *
 * @param similarity  raw cosine similarity between the JD and repo embeddings
 * @param fitScore    0-100 LLM judgement of fit, grounded in repo content (null if the LLM was unavailable)
 * @param matchScore  0-100 final score used for ordering (blend of relative similarity and fit score)
 * @param evidence    short verbatim snippets from the repo that back the reason (verified to exist)
 */
public record MatchResult(
        int rank,
        long repoId,
        String name,
        String url,
        String description,
        List<String> languages,
        List<String> topics,
        int stars,
        boolean hasReadme,
        double similarity,
        Integer fitScore,
        int matchScore,
        String reason,
        List<String> matchedRequirements,
        List<String> evidence) {
}
