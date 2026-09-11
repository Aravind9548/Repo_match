package com.repomatch.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.repomatch.ai.AiServiceException;
import com.repomatch.ai.dto.ExplainResponse;
import com.repomatch.ai.dto.RankResponse;
import com.repomatch.api.AccountSummary;
import com.repomatch.config.AppProperties;
import com.repomatch.repo.GithubAccountRepository;
import com.repomatch.repo.RepoEntity;
import com.repomatch.repo.RepoSyncService;

/**
 * One-shot analysis (no review gates): sync → rank → LLM judges the pool → score → persist.
 * The human-in-the-loop version of the same steps lives in {@code WorkflowService}.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final RepoSyncService sync;
    private final GithubAccountRepository accounts;
    private final AnalysisRepository analyses;
    private final MatchPipeline pipeline;
    private final UsageGuard usage;
    private final AppProperties.Analysis config;

    public AnalysisService(RepoSyncService sync, GithubAccountRepository accounts, AnalysisRepository analyses,
                           MatchPipeline pipeline, UsageGuard usage, AppProperties props) {
        this.sync = sync;
        this.accounts = accounts;
        this.analyses = analyses;
        this.pipeline = pipeline;
        this.usage = usage;
        this.config = props.analysis();
    }

    public AnalyzeResponse analyze(AnalyzeRequest req, String clientKey) {
        int topK = Math.clamp(req.topK() != null ? req.topK() : config.defaultTopK(), 1, config.maxTopK());
        String jd = req.jobDescription().strip();

        RepoSyncService.SyncResult synced = sync.sync(req.username(), Boolean.TRUE.equals(req.refresh()));
        List<RepoEntity> candidates = MatchPipeline.eligible(synced.repos(), Boolean.TRUE.equals(req.includeForks()));
        if (candidates.isEmpty()) {
            throw new NoRepositoriesException(synced.account().getDisplayLogin());
        }
        Map<Long, RepoEntity> byId = MatchPipeline.index(candidates);

        RankResponse ranked = pipeline.rank(jd, candidates);
        List<RankResponse.Ranked> order = ranked.results();
        List<RankResponse.Ranked> pool = pipeline.proposePool(order, byId, topK);

        usage.acquireLlmCall(clientKey);
        List<String> warnings = new ArrayList<>();
        Map<Long, ExplainResponse.Explanation> explanations = Map.of();
        String llmModel = null;
        try {
            MatchPipeline.Judgement judgement = pipeline.judge(jd, pool, byId);
            explanations = judgement.explanations();
            llmModel = judgement.llmModel();
        } catch (AiServiceException e) {
            log.warn("Explain step failed, falling back to similarity-only ranking: {}", e.getMessage());
            warnings.add("Match reasons are unavailable right now, so ranking uses embedding similarity only.");
        }

        List<MatchResult> scored = MatchPipeline.score(pool, order, byId, explanations);
        List<MatchResult> matches = MatchPipeline.ranked(scored.subList(0, Math.min(topK, scored.size())));
        Set<Long> judged = pool.stream().map(RankResponse.Ranked::id).collect(Collectors.toSet());
        List<AnalysisResults.OtherRepo> others = MatchPipeline.others(
                scored.subList(matches.size(), scored.size()), order, judged, byId, matches.size());

        AnalysisEntity entity = pipeline.save(synced.account().getLogin(), jd, topK, ranked.embeddingModel(), llmModel,
                candidates.size(), matches, others, warnings);
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
}
