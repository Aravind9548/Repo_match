package com.repomatch.analysis;

import java.util.List;

/** The JSON document persisted in {@code analysis.results}. */
public record AnalysisResults(int reposAnalyzed, List<MatchResult> matches, List<OtherRepo> otherRepos,
                              List<String> warnings) {

    /** A repo that was ranked but didn't make the top-k. */
    public record OtherRepo(int rank, long repoId, String name, String url, double similarity) {
    }
}
