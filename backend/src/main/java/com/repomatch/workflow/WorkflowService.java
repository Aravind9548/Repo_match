package com.repomatch.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.repomatch.ai.dto.RankResponse;
import com.repomatch.analysis.AnalysisEntity;
import com.repomatch.analysis.AnalysisResults;
import com.repomatch.analysis.AnalyzeRequest;
import com.repomatch.analysis.MatchPipeline;
import com.repomatch.analysis.MatchResult;
import com.repomatch.analysis.NoRepositoriesException;
import com.repomatch.analysis.UsageGuard;
import com.repomatch.api.AccountSummary;
import com.repomatch.config.AppProperties;
import com.repomatch.repo.GithubAccountRepository;
import com.repomatch.repo.RepoEntity;
import com.repomatch.repo.RepoRepository;
import com.repomatch.repo.RepoSyncService;

/**
 * Human-in-the-loop analysis: the same pipeline as the one-shot endpoint, but every automated step
 * stops at a review gate and only continues when the human approves (and possibly edits) its output.
 * The paid LLM step can only run after the human has approved the shortlist.
 */
@Service
public class WorkflowService {

    /** The AI service accepts at most this many repos per explain call. */
    static final int MAX_JUDGED = 15;

    private final RepoSyncService sync;
    private final RepoRepository repos;
    private final GithubAccountRepository accounts;
    private final WorkflowRepository workflows;
    private final MatchPipeline pipeline;
    private final UsageGuard usage;
    private final AppProperties.Analysis config;

    public WorkflowService(RepoSyncService sync, RepoRepository repos, GithubAccountRepository accounts,
                           WorkflowRepository workflows, MatchPipeline pipeline, UsageGuard usage, AppProperties props) {
        this.sync = sync;
        this.repos = repos;
        this.accounts = accounts;
        this.workflows = workflows;
        this.pipeline = pipeline;
        this.usage = usage;
        this.config = props.analysis();
    }

    /** Step 1 (automatic): fetch repos from GitHub, then wait for the human to choose which count. */
    public WorkflowView create(AnalyzeRequest req) {
        int topK = Math.clamp(req.topK() != null ? req.topK() : config.defaultTopK(), 1, config.maxTopK());
        boolean includeForks = Boolean.TRUE.equals(req.includeForks());
        RepoSyncService.SyncResult synced = sync.sync(req.username(), Boolean.TRUE.equals(req.refresh()));

        List<Long> preselected = MatchPipeline.eligible(synced.repos(), includeForks).stream().map(RepoEntity::getId).toList();
        if (preselected.isEmpty()) {
            throw new NoRepositoriesException(synced.account().getDisplayLogin());
        }

        WorkflowState state = new WorkflowState();
        state.selectedRepoIds = new ArrayList<>(preselected);

        WorkflowEntity wf = new WorkflowEntity();
        wf.setId(UUID.randomUUID());
        wf.setUsername(synced.account().getLogin());
        wf.setJobDescription(req.jobDescription().strip());
        wf.setTopK(topK);
        wf.setIncludeForks(includeForks);
        wf.setStep(WorkflowStep.REVIEW_REPOS);
        wf.setState(state);
        wf.setCreatedAt(Instant.now());
        wf.setUpdatedAt(wf.getCreatedAt());
        return view(workflows.save(wf));
    }

    public WorkflowView get(UUID id) {
        return view(load(id));
    }

    /** Gate 1 approved → rank the chosen repos and propose a shortlist for the LLM. */
    public WorkflowView approveRepos(UUID id, List<Long> repoIds) {
        WorkflowEntity wf = load(id);
        requireStep(wf, WorkflowStep.REVIEW_REPOS, WorkflowStep.REVIEW_SHORTLIST);

        Map<Long, RepoEntity> owned = ownedRepos(wf);
        List<RepoEntity> chosen = distinct(repoIds).stream().map(owned::get).filter(Objects::nonNull).toList();
        if (chosen.isEmpty()) {
            throw new InvalidSelectionException("Select at least one repository to rank.");
        }

        RankResponse ranked = pipeline.rank(wf.getJobDescription(), chosen);
        List<RankResponse.Ranked> proposal = pipeline.proposePool(ranked.results(), MatchPipeline.index(chosen), wf.getTopK());

        WorkflowState st = wf.getState();
        st.selectedRepoIds = chosen.stream().map(RepoEntity::getId).toList();
        st.embeddingModel = ranked.embeddingModel();
        st.ranking = ranked.results().stream().map(r -> new WorkflowState.Ranked(r.id(), r.similarity())).toList();
        st.shortlistIds = proposal.stream().map(RankResponse.Ranked::id).toList();
        resetJudgement(st);
        return advance(wf, WorkflowStep.REVIEW_SHORTLIST);
    }

    /** Gate 2 approved → the (paid) LLM judges exactly the shortlist the human approved. */
    public WorkflowView approveShortlist(UUID id, List<Long> repoIds, String clientKey) {
        WorkflowEntity wf = load(id);
        requireStep(wf, WorkflowStep.REVIEW_SHORTLIST, WorkflowStep.REVIEW_RESULTS);
        WorkflowState st = wf.getState();

        Map<Long, RepoEntity> owned = ownedRepos(wf);
        Set<Long> requested = new HashSet<>(repoIds);
        List<RankResponse.Ranked> order = rankingOf(st, owned);
        List<RankResponse.Ranked> pool = order.stream().filter(r -> requested.contains(r.id())).toList();
        if (pool.isEmpty()) {
            throw new InvalidSelectionException("Shortlist at least one ranked repository.");
        }
        if (pool.size() > MAX_JUDGED) {
            throw new InvalidSelectionException("Gemini can judge up to " + MAX_JUDGED + " repositories at a time.");
        }

        usage.acquireLlmCall(clientKey);
        MatchPipeline.Judgement judgement = pipeline.judge(wf.getJobDescription(), pool, owned);
        List<MatchResult> scored = MatchPipeline.ranked(MatchPipeline.score(pool, order, owned, judgement.explanations()));

        st.shortlistIds = pool.stream().map(RankResponse.Ranked::id).toList();
        st.judgements = scored;
        st.llmModel = judgement.llmModel();
        st.usage = judgement.usage();
        st.acceptedIds = scored.stream().limit(wf.getTopK()).map(MatchResult::repoId).toList();
        st.warnings = new ArrayList<>();
        return advance(wf, WorkflowStep.REVIEW_RESULTS);
    }

    /** Gate 3 approved → save the accepted picks as a regular (shareable) analysis. */
    public WorkflowView finalizeRun(UUID id, List<Long> acceptedIds) {
        WorkflowEntity wf = load(id);
        requireStep(wf, WorkflowStep.REVIEW_RESULTS);
        WorkflowState st = wf.getState();

        Set<Long> accepted = new LinkedHashSet<>(acceptedIds);
        List<MatchResult> picks = MatchPipeline.ranked(
                st.judgements.stream().filter(m -> accepted.contains(m.repoId())).toList());
        if (picks.isEmpty()) {
            throw new InvalidSelectionException("Accept at least one project to save the results.");
        }
        List<MatchResult> rejected = st.judgements.stream().filter(m -> !accepted.contains(m.repoId())).toList();

        Map<Long, RepoEntity> owned = ownedRepos(wf);
        Set<Long> judgedIds = new HashSet<>(st.shortlistIds);
        List<AnalysisResults.OtherRepo> others = MatchPipeline.others(rejected, rankingOf(st, owned), judgedIds, owned, picks.size());

        AnalysisEntity analysis = pipeline.save(wf.getUsername(), wf.getJobDescription(), picks.size(),
                st.embeddingModel, st.llmModel, st.selectedRepoIds.size(), picks, others, st.warnings);

        st.acceptedIds = picks.stream().map(MatchResult::repoId).toList();
        wf.setAnalysisId(analysis.getId());
        return advance(wf, WorkflowStep.DONE);
    }

    private static void resetJudgement(WorkflowState st) {
        st.judgements = new ArrayList<>();
        st.acceptedIds = new ArrayList<>();
        st.llmModel = null;
        st.usage = null;
        st.warnings = new ArrayList<>();
    }

    private WorkflowView advance(WorkflowEntity wf, WorkflowStep next) {
        wf.setStep(next);
        wf.setUpdatedAt(Instant.now());
        return view(workflows.save(wf));
    }

    private WorkflowEntity load(UUID id) {
        return workflows.findById(id).orElseThrow(() -> new WorkflowNotFoundException(id));
    }

    private static void requireStep(WorkflowEntity wf, WorkflowStep... allowed) {
        if (!Arrays.asList(allowed).contains(wf.getStep())) {
            throw new WorkflowStepException(wf.getStep());
        }
    }

    private Map<Long, RepoEntity> ownedRepos(WorkflowEntity wf) {
        return MatchPipeline.index(repos.findByOwnerLoginOrderByPushedAtDesc(wf.getUsername()));
    }

    /** The stored ranking, minus repos that disappeared from GitHub since. */
    private static List<RankResponse.Ranked> rankingOf(WorkflowState st, Map<Long, RepoEntity> owned) {
        return st.ranking.stream()
                .filter(r -> owned.containsKey(r.id()))
                .map(r -> new RankResponse.Ranked(r.id(), r.similarity(), null))
                .toList();
    }

    private static List<Long> distinct(List<Long> ids) {
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    private WorkflowView view(WorkflowEntity wf) {
        WorkflowState st = wf.getState();
        List<RepoEntity> owned = repos.findByOwnerLoginOrderByPushedAtDesc(wf.getUsername());
        Map<Long, RepoEntity> byId = MatchPipeline.index(owned);

        Set<Long> selected = new HashSet<>(st.selectedRepoIds);
        List<WorkflowView.RepoOption> repoOptions = owned.stream()
                .map(r -> WorkflowView.RepoOption.of(r, selected.contains(r.getId())))
                .toList();

        Set<Long> shortlisted = new HashSet<>(st.shortlistIds);
        List<WorkflowView.RankedOption> ranking = st.ranking.stream()
                .filter(r -> byId.containsKey(r.id()))
                .map(r -> {
                    RepoEntity repo = byId.get(r.id());
                    return new WorkflowView.RankedOption(r.id(), repo.getName(), repo.getHtmlUrl(), r.similarity(),
                            repo.getReadme() != null, shortlisted.contains(r.id()));
                })
                .toList();

        AccountSummary account = accounts.findById(wf.getUsername()).map(AccountSummary::of).orElse(null);
        return new WorkflowView(wf.getId(), account, wf.getJobDescription(), wf.getTopK(), wf.getStep(), repoOptions,
                ranking, st.judgements, st.acceptedIds, st.embeddingModel, st.llmModel, WorkflowView.Usage.of(st.usage), st.warnings,
                wf.getAnalysisId(), wf.getCreatedAt(), wf.getUpdatedAt());
    }
}
