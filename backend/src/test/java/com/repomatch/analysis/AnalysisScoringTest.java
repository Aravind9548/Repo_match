package com.repomatch.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.repomatch.ai.dto.ExplainResponse;
import com.repomatch.ai.dto.RankResponse;
import com.repomatch.repo.RepoEntity;

class AnalysisScoringTest {

    private static RepoEntity repo(long id) {
        RepoEntity r = new RepoEntity();
        r.setId(id);
        r.setName("repo" + id);
        r.setHtmlUrl("https://github.com/u/repo" + id);
        return r;
    }

    private final Map<Long, RepoEntity> byId = Map.of(1L, repo(1), 2L, repo(2), 3L, repo(3));
    private final List<RankResponse.Ranked> all = List.of(
            new RankResponse.Ranked(1, 0.80, null),
            new RankResponse.Ranked(2, 0.75, null),
            new RankResponse.Ranked(3, 0.60, null));

    @Test
    void poolPrefersReposWithRealReadmesOverDeceptivelySimilarEmptyOnes() {
        byId.get(1L).setReadme(null);            // highest similarity, but empty
        byId.get(2L).setReadme("x".repeat(500));
        byId.get(3L).setReadme("x".repeat(500)); // lowest similarity, but has content

        assertThat(AnalysisService.selectPool(all, byId, 2, 100))
                .extracting(RankResponse.Ranked::id).containsExactly(2L, 3L);
        // Thin repos still fill leftover slots.
        assertThat(AnalysisService.selectPool(all, byId, 5, 100))
                .extracting(RankResponse.Ranked::id).containsExactly(2L, 3L, 1L);
    }

    @Test
    void withoutLlmScoresAreRelativeSimilarity() {
        List<MatchResult> scored = AnalysisService.score(all, all, byId, Map.of());

        assertThat(scored).extracting(MatchResult::repoId).containsExactly(1L, 2L, 3L);
        assertThat(scored).extracting(MatchResult::matchScore).containsExactly(100, 75, 0);
        assertThat(scored.get(0).fitScore()).isNull();
    }

    @Test
    void llmFitScoreCanReorderCandidates() {
        var explanations = Map.of(
                1L, new ExplainResponse.Explanation(1, 30, "weak", List.of(), List.of()),
                2L, new ExplainResponse.Explanation(2, 95, "strong", List.of("Java"), List.of()));

        List<MatchResult> scored = AnalysisService.score(all.subList(0, 2), all, byId, explanations);

        // repo2: 0.2*75 + 0.8*95 = 91 beats repo1: 0.2*100 + 0.8*30 = 44
        assertThat(scored).extracting(MatchResult::repoId).containsExactly(2L, 1L);
        assertThat(scored).extracting(MatchResult::matchScore).containsExactly(91, 44);
        assertThat(scored.get(0).reason()).isEqualTo("strong");
    }
}
