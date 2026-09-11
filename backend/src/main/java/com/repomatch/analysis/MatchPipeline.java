package com.repomatch.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.repomatch.ai.AiServiceClient;
import com.repomatch.ai.dto.ExplainRequest;
import com.repomatch.ai.dto.ExplainResponse;
import com.repomatch.ai.dto.RankRequest;
import com.repomatch.ai.dto.RankResponse;
import com.repomatch.config.AppProperties;
import com.repomatch.repo.RepoEntity;
import com.repomatch.repo.RepoRepository;

/**
 * The matching steps as reusable building blocks: rank (retrieve) → pick a pool → judge (LLM) → score → save.
 * {@link AnalysisService} runs them back to back; the workflow runs them one review gate at a time.
 */
@Component
public class MatchPipeline {

    private static final Logger log = LoggerFactory.getLogger(MatchPipeline.class);

    /**
     * Weight of the LLM fit score vs. relative embedding similarity in the final match score. Raw cosine
     * similarities cluster in a narrow band (~0.55-0.67), so min-max normalising them exaggerates small
     * keyword-driven gaps; the grounded LLM judgement should dominate and similarity only break near-ties.
     */
    static final double FIT_WEIGHT = 0.8;

    private final RepoRepository repos;
    private final AnalysisRepository analyses;
    private final AiServiceClient ai;
    private final AppProperties.Analysis config;

    public MatchPipeline(RepoRepository repos, AnalysisRepository analyses, AiServiceClient ai, AppProperties props) {
        this.repos = repos;
        this.analyses = analyses;
        this.ai = ai;
        this.config = props.analysis();
    }

    public record Judgement(Map<Long, ExplainResponse.Explanation> explanations, String llmModel,
                            ExplainResponse.Usage usage) {
    }

    /** Repos considered by default: forks are mostly other people's work. */
    public static List<RepoEntity> eligible(List<RepoEntity> repos, boolean includeForks) {
        return repos.stream().filter(r -> includeForks || !r.isFork()).toList();
    }

    public static Map<Long, RepoEntity> index(Collection<RepoEntity> repos) {
        return repos.stream().collect(Collectors.toMap(RepoEntity::getId, Function.identity(), (a, b) -> a));
    }

    /** Embeds (re-using cached embeddings) and ranks candidates by similarity to the JD, best first. */
    public RankResponse rank(String jd, Collection<RepoEntity> candidates) {
        RankResponse ranked = ai.rank(new RankRequest(jd, candidates.stream().map(MatchPipeline::toRankCandidate).toList()));
        cacheNewEmbeddings(ranked, index(candidates));
        List<RankResponse.Ranked> order = ranked.results().stream()
                .sorted(Comparator.comparingDouble(RankResponse.Ranked::similarity).reversed())
                .toList();
        return new RankResponse(ranked.embeddingModel(), order);
    }

    /** How many candidates the LLM should judge for a given top-k. */
    public int poolSize(int topK, int available) {
        return Math.min(available, Math.max(topK + config.rerankPoolExtra(), config.rerankPoolMin()));
    }

    public List<RankResponse.Ranked> proposePool(List<RankResponse.Ranked> order, Map<Long, RepoEntity> byId, int topK) {
        return selectPool(order, byId, poolSize(topK, order.size()), config.minReadmeChars());
    }

    /** One LLM call: grounded fit scores + reasons for every repo in the pool. */
    public Judgement judge(String jd, List<RankResponse.Ranked> pool, Map<Long, RepoEntity> byId) {
        ExplainResponse explained = ai.explain(new ExplainRequest(jd,
                pool.stream().map(r -> toExplainCandidate(byId.get(r.id()), r.similarity())).toList()));
        Map<Long, ExplainResponse.Explanation> explanations = explained.results().stream()
                .collect(Collectors.toMap(ExplainResponse.Explanation::id, Function.identity(), (a, b) -> a));
        if (explained.usage() != null) {
            log.info("LLM {} judged {} repos: {}", explained.llmModel(), pool.size(), explained.usage());
        }
        return new Judgement(explanations, explained.llmModel(), explained.usage());
    }

    public AnalysisEntity save(String username, String jd, int topK, String embeddingModel, String llmModel,
                               int reposAnalyzed, List<MatchResult> matches, List<AnalysisResults.OtherRepo> others,
                               List<String> warnings) {
        AnalysisEntity entity = new AnalysisEntity();
        entity.setId(UUID.randomUUID());
        entity.setUsername(username);
        entity.setJobDescription(jd);
        entity.setTopK(topK);
        entity.setEmbeddingModel(embeddingModel);
        entity.setLlmModel(llmModel);
        entity.setResults(new AnalysisResults(reposAnalyzed, matches, others, warnings));
        entity.setCreatedAt(Instant.now());
        return analyses.save(entity);
    }

    /**
     * Picks which ranked repos the LLM judges. Embedding similarity alone over-rewards near-empty repos
     * (a short profile that happens to contain "Java" looks very similar to a keyword-list JD), so repos
     * with a real README fill the pool first, in similarity order; thin repos only take leftover slots.
     */
    static List<RankResponse.Ranked> selectPool(List<RankResponse.Ranked> order, Map<Long, RepoEntity> byId,
                                                int poolSize, int minReadmeChars) {
        List<RankResponse.Ranked> rich = new ArrayList<>();
        List<RankResponse.Ranked> thin = new ArrayList<>();
        for (RankResponse.Ranked r : order) {
            String readme = byId.get(r.id()).getReadme();
            (readme != null && readme.length() >= minReadmeChars ? rich : thin).add(r);
        }
        List<RankResponse.Ranked> pool = new ArrayList<>(rich);
        pool.addAll(thin);
        return pool.subList(0, Math.min(poolSize, pool.size()));
    }

    /**
     * Final score = blend of the LLM's grounded fit score and the embedding similarity, where similarity
     * is min-max normalised across all ranked repos (raw cosine values cluster in a narrow band).
     * Returned best first, with rank 0; use {@link #ranked} to number them.
     */
    public static List<MatchResult> score(List<RankResponse.Ranked> pool, List<RankResponse.Ranked> all,
                                   Map<Long, RepoEntity> byId, Map<Long, ExplainResponse.Explanation> explanations) {
        double min = all.stream().mapToDouble(RankResponse.Ranked::similarity).min().orElse(0);
        double max = all.stream().mapToDouble(RankResponse.Ranked::similarity).max().orElse(1);
        double span = max - min;

        return pool.stream().map(r -> {
            RepoEntity repo = byId.get(r.id());
            double relative = span < 1e-9 ? 1.0 : (r.similarity() - min) / span;
            ExplainResponse.Explanation ex = explanations.get(r.id());
            double score = ex == null
                    ? relative * 100
                    : (1 - FIT_WEIGHT) * relative * 100 + FIT_WEIGHT * ex.fitScore();
            return new MatchResult(0, repo.getId(), repo.getName(), repo.getHtmlUrl(), repo.getDescription(),
                    repo.languageNames(), repo.getTopics(), repo.getStars(), repo.getReadme() != null,
                    round(r.similarity(), 4),
                    ex == null ? null : ex.fitScore(),
                    (int) Math.round(score),
                    ex == null ? null : ex.reason(),
                    ex == null ? List.of() : ex.matchedRequirements(),
                    ex == null ? List.of() : ex.evidence());
        }).sorted(Comparator.comparingInt(MatchResult::matchScore).reversed()
                .thenComparing(Comparator.comparingDouble(MatchResult::similarity).reversed())).toList();
    }

    /** Numbers results 1..n in their current order. */
    public static List<MatchResult> ranked(List<MatchResult> results) {
        List<MatchResult> out = new ArrayList<>(results.size());
        for (int i = 0; i < results.size(); i++) {
            MatchResult m = results.get(i);
            out.add(new MatchResult(i + 1, m.repoId(), m.name(), m.url(), m.description(), m.languages(), m.topics(),
                    m.stars(), m.hasReadme(), m.similarity(), m.fitScore(), m.matchScore(), m.reason(),
                    m.matchedRequirements(), m.evidence()));
        }
        return out;
    }

    /** Everything not shown as a match: judged leftovers first (by score), then never-judged repos (by similarity). */
    public static List<AnalysisResults.OtherRepo> others(List<MatchResult> judgedLeftovers, List<RankResponse.Ranked> order,
                                                  Set<Long> judgedIds, Map<Long, RepoEntity> byId, int shownCount) {
        List<AnalysisResults.OtherRepo> others = new ArrayList<>();
        int rank = shownCount;
        for (MatchResult m : judgedLeftovers) {
            others.add(new AnalysisResults.OtherRepo(++rank, m.repoId(), m.name(), m.url(), m.similarity()));
        }
        for (RankResponse.Ranked r : order) {
            RepoEntity repo = byId.get(r.id());
            if (!judgedIds.contains(r.id()) && repo != null) {
                others.add(new AnalysisResults.OtherRepo(++rank, r.id(), repo.getName(), repo.getHtmlUrl(), round(r.similarity(), 4)));
            }
        }
        return others;
    }

    static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }

    private void cacheNewEmbeddings(RankResponse ranked, Map<Long, RepoEntity> byId) {
        List<RepoEntity> changed = new ArrayList<>();
        for (RankResponse.Ranked r : ranked.results()) {
            RepoEntity repo = byId.get(r.id());
            if (r.embedding() != null && repo != null) {
                repo.setEmbedding(r.embedding());
                repo.setEmbeddingModel(ranked.embeddingModel());
                repo.setEmbeddingProfileHash(repo.getProfileHash());
                changed.add(repo);
            }
        }
        if (!changed.isEmpty()) {
            repos.saveAll(changed);
            log.info("Cached {} new embeddings", changed.size());
        }
    }

    private static RankRequest.Candidate toRankCandidate(RepoEntity r) {
        return r.hasFreshEmbedding()
                ? new RankRequest.Candidate(r.getId(), r.getProfileText(), r.getEmbedding(), r.getEmbeddingModel())
                : new RankRequest.Candidate(r.getId(), r.getProfileText(), null, null);
    }

    private static ExplainRequest.Candidate toExplainCandidate(RepoEntity r, double similarity) {
        return new ExplainRequest.Candidate(r.getId(), r.getName(), r.getDescription(), r.languageNames(),
                r.getTopics(), r.getReadme(), similarity);
    }
}
