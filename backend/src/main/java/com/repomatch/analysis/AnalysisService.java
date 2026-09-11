package com.repomatch.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.repomatch.ai.AiServiceClient;
import com.repomatch.ai.AiServiceException;
import com.repomatch.ai.dto.ExplainRequest;
import com.repomatch.ai.dto.ExplainResponse;
import com.repomatch.ai.dto.RankRequest;
import com.repomatch.ai.dto.RankResponse;
import com.repomatch.api.AccountSummary;
import com.repomatch.config.AppProperties;
import com.repomatch.repo.GithubAccountRepository;
import com.repomatch.repo.RepoEntity;
import com.repomatch.repo.RepoRepository;
import com.repomatch.repo.RepoSyncService;

/**
 * The core pipeline: sync repos from GitHub → embed + rank by cosine similarity (retrieve) →
 * LLM judges the top candidates against the JD (re-rank + grounded reasons) → persist.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    /**
     * Weight of the LLM fit score vs. relative embedding similarity in the final match score. Raw cosine
     * similarities cluster in a narrow band (~0.55-0.67), so min-max normalising them exaggerates small
     * keyword-driven gaps; the grounded LLM judgement should dominate and similarity only break near-ties.
     */
    static final double FIT_WEIGHT = 0.8;

    private final RepoSyncService sync;
    private final RepoRepository repos;
    private final GithubAccountRepository accounts;
    private final AnalysisRepository analyses;
    private final AiServiceClient ai;
    private final AppProperties.Analysis config;

    public AnalysisService(RepoSyncService sync, RepoRepository repos, GithubAccountRepository accounts,
                           AnalysisRepository analyses, AiServiceClient ai, AppProperties props) {
        this.sync = sync;
        this.repos = repos;
        this.accounts = accounts;
        this.analyses = analyses;
        this.ai = ai;
        this.config = props.analysis();
    }

    public AnalyzeResponse analyze(AnalyzeRequest req) {
        int topK = Math.clamp(req.topK() != null ? req.topK() : config.defaultTopK(), 1, config.maxTopK());
        boolean includeForks = Boolean.TRUE.equals(req.includeForks());
        String jd = req.jobDescription().strip();

        RepoSyncService.SyncResult synced = sync.sync(req.username(), Boolean.TRUE.equals(req.refresh()));
        List<RepoEntity> candidates = synced.repos().stream().filter(r -> includeForks || !r.isFork()).toList();
        if (candidates.isEmpty()) {
            throw new NoRepositoriesException(synced.account().getDisplayLogin());
        }
        Map<Long, RepoEntity> byId = candidates.stream().collect(Collectors.toMap(RepoEntity::getId, Function.identity()));

        // 1. Retrieve: cosine similarity over embeddings (cached ones are re-used).
        RankResponse ranked = ai.rank(new RankRequest(jd, candidates.stream().map(AnalysisService::toRankCandidate).toList()));
        cacheNewEmbeddings(ranked, byId);
        List<RankResponse.Ranked> order = ranked.results().stream()
                .sorted(Comparator.comparingDouble(RankResponse.Ranked::similarity).reversed())
                .toList();

        // 2. Re-rank + explain: the LLM judges a larger pool than we return.
        int poolSize = Math.min(order.size(), Math.max(topK + config.rerankPoolExtra(), config.rerankPoolMin()));
        List<RankResponse.Ranked> pool = selectPool(order, byId, poolSize, config.minReadmeChars());
        List<String> warnings = new ArrayList<>();
        Map<Long, ExplainResponse.Explanation> explanations = Map.of();
        String llmModel = null;
        try {
            ExplainResponse explained = ai.explain(new ExplainRequest(jd,
                    pool.stream().map(r -> toExplainCandidate(byId.get(r.id()), r.similarity())).toList()));
            explanations = explained.results().stream()
                    .collect(Collectors.toMap(ExplainResponse.Explanation::id, Function.identity(), (a, b) -> a));
            llmModel = explained.llmModel();
            if (explained.usage() != null) {
                log.info("LLM {} judged {} repos: {}", llmModel, pool.size(), explained.usage());
            }
        } catch (AiServiceException e) {
            log.warn("Explain step failed, falling back to similarity-only ranking: {}", e.getMessage());
            warnings.add("Match reasons are unavailable right now, so ranking uses embedding similarity only.");
        }

        List<MatchResult> scored = score(pool, order, byId, explanations);
        List<MatchResult> matches = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            matches.add(withRank(scored.get(i), i + 1));
        }

        // Everything else: judged-but-not-top-k first (by score), then never-judged repos (by similarity).
        List<AnalysisResults.OtherRepo> others = new ArrayList<>();
        Set<Long> judged = pool.stream().map(RankResponse.Ranked::id).collect(Collectors.toSet());
        int rank = matches.size();
        for (MatchResult m : scored.subList(matches.size(), scored.size())) {
            others.add(new AnalysisResults.OtherRepo(++rank, m.repoId(), m.name(), m.url(), m.similarity()));
        }
        for (RankResponse.Ranked r : order) {
            if (!judged.contains(r.id())) {
                RepoEntity repo = byId.get(r.id());
                others.add(new AnalysisResults.OtherRepo(++rank, r.id(), repo.getName(), repo.getHtmlUrl(), round(r.similarity(), 4)));
            }
        }

        AnalysisEntity entity = new AnalysisEntity();
        entity.setId(UUID.randomUUID());
        entity.setUsername(synced.account().getLogin());
        entity.setJobDescription(jd);
        entity.setTopK(topK);
        entity.setEmbeddingModel(ranked.embeddingModel());
        entity.setLlmModel(llmModel);
        entity.setResults(new AnalysisResults(candidates.size(), matches, others, warnings));
        entity.setCreatedAt(Instant.now());
        analyses.save(entity);

        return AnalyzeResponse.of(entity, AccountSummary.of(synced.account()));
    }

    public AnalyzeResponse get(UUID id) {
        AnalysisEntity e = analyses.findById(id).orElseThrow(() -> new AnalysisNotFoundException(id));
        AccountSummary account = accounts.findById(e.getUsername()).map(AccountSummary::of).orElse(null);
        return AnalyzeResponse.of(e, account);
    }

    public List<AnalysisSummary> history(String username) {
        return analyses.findTop20ByUsernameOrderByCreatedAtDesc(username.trim().toLowerCase(Locale.ROOT)).stream()
                .map(AnalysisSummary::of)
                .toList();
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
     * is min-max normalised across all of the user's repos (raw cosine values cluster in a narrow band).
     */
    static List<MatchResult> score(List<RankResponse.Ranked> pool, List<RankResponse.Ranked> all,
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

    private static MatchResult withRank(MatchResult m, int rank) {
        return new MatchResult(rank, m.repoId(), m.name(), m.url(), m.description(), m.languages(), m.topics(),
                m.stars(), m.hasReadme(), m.similarity(), m.fitScore(), m.matchScore(), m.reason(),
                m.matchedRequirements(), m.evidence());
    }

    private static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }
}
