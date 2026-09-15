package com.repomatch.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.repomatch.ai.dto.ExplainResponse;
import com.repomatch.ai.dto.RankResponse;
import com.repomatch.analysis.AnalysisEntity;
import com.repomatch.analysis.AnalyzeRequest;
import com.repomatch.analysis.MatchPipeline;
import com.repomatch.analysis.UsageGuard;
import com.repomatch.analysis.UsageLimitException;
import com.repomatch.config.AppProperties;
import com.repomatch.repo.GithubAccount;
import com.repomatch.repo.GithubAccountRepository;
import com.repomatch.repo.RepoEntity;
import com.repomatch.repo.RepoRepository;
import com.repomatch.repo.RepoSyncService;

class WorkflowServiceTest {

    private final RepoSyncService sync = mock(RepoSyncService.class);
    private final RepoRepository repos = mock(RepoRepository.class);
    private final GithubAccountRepository accounts = mock(GithubAccountRepository.class);
    private final WorkflowRepository workflows = mock(WorkflowRepository.class);
    private final MatchPipeline pipeline = mock(MatchPipeline.class);
    private final UsageGuard usage = mock(UsageGuard.class);
    private final Map<UUID, WorkflowEntity> db = new HashMap<>();
    private WorkflowService service;

    private final List<RepoEntity> owned = List.of(repo(1, false), repo(2, false), repo(3, true));

    private static RepoEntity repo(long id, boolean fork) {
        RepoEntity r = new RepoEntity();
        r.setId(id);
        r.setName("repo" + id);
        r.setFullName("u/repo" + id);
        r.setHtmlUrl("https://github.com/u/repo" + id);
        r.setFork(fork);
        r.setReadme("readme " + id);
        r.setProfileText("profile " + id);
        return r;
    }

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(null, null, new AppProperties.Analysis(2, 10, 3, 12, 100),
                new AppProperties.Limits(5, 50), null, null, null);
        service = new WorkflowService(sync, repos, accounts, workflows, pipeline, usage, props);

        GithubAccount account = new GithubAccount();
        account.setLogin("u");
        account.setDisplayLogin("U");
        account.setSyncedAt(Instant.now());
        when(sync.sync(eq("U"), eq(false))).thenReturn(new RepoSyncService.SyncResult(account, owned, false));
        when(repos.findByOwnerLoginOrderByPushedAtDesc("u")).thenReturn(owned);
        when(accounts.findById("u")).thenReturn(Optional.of(account));
        when(workflows.save(any())).thenAnswer(inv -> {
            WorkflowEntity wf = inv.getArgument(0);
            db.put(wf.getId(), wf);
            return wf;
        });
        when(workflows.findById(any())).thenAnswer(inv -> Optional.ofNullable(db.get(inv.<UUID>getArgument(0))));
    }

    private WorkflowView create() {
        return service.create(new AnalyzeRequest("U", "Java Spring Boot developer ".repeat(3), 2, false, false));
    }

    @Test
    void createStopsAtRepoReviewWithForksDeselected() {
        WorkflowView view = create();

        assertThat(view.step()).isEqualTo(WorkflowStep.REVIEW_REPOS);
        assertThat(view.repos()).extracting(WorkflowView.RepoOption::selected).containsExactly(true, true, false);
        verify(pipeline, never()).rank(anyString(), anyList());
    }

    @Test
    void fullRunOnlyJudgesTheApprovedShortlistAndSavesAcceptedPicks() {
        UUID id = create().id();
        var ranking = List.of(new RankResponse.Ranked(2, 0.7, null), new RankResponse.Ranked(1, 0.6, null));
        when(pipeline.rank(anyString(), anyList())).thenReturn(new RankResponse("embed", ranking));
        when(pipeline.proposePool(anyList(), anyMap(), anyInt())).thenReturn(ranking);

        WorkflowView shortlist = service.approveRepos(id, List.of(1L, 2L));
        assertThat(shortlist.step()).isEqualTo(WorkflowStep.REVIEW_SHORTLIST);
        assertThat(shortlist.ranking()).extracting(WorkflowView.RankedOption::shortlisted).containsExactly(true, true);

        // Human removes repo1 from the shortlist: only repo2 may reach the LLM.
        when(pipeline.judge(anyString(), anyList(), anyMap())).thenReturn(new MatchPipeline.Judgement(
                Map.of(2L, new ExplainResponse.Explanation(2, 90, "fits", List.of("Java"), List.of())), "llm", null));
        WorkflowView results = service.approveShortlist(id, List.of(2L), "ip");
        verify(pipeline).judge(anyString(), eq(List.of(ranking.get(0))), anyMap());
        verify(usage).acquireLlmCall("ip");
        assertThat(results.step()).isEqualTo(WorkflowStep.REVIEW_RESULTS);
        assertThat(results.judgements()).extracting(m -> m.repoId()).containsExactly(2L);

        AnalysisEntity saved = new AnalysisEntity();
        saved.setId(UUID.randomUUID());
        when(pipeline.save(anyString(), anyString(), anyInt(), any(), any(), anyInt(), anyList(), anyList(), anyList()))
                .thenReturn(saved);
        WorkflowView done = service.finalizeRun(id, List.of(2L));
        assertThat(done.step()).isEqualTo(WorkflowStep.DONE);
        assertThat(done.analysisId()).isEqualTo(saved.getId());
    }

    @Test
    void cannotSkipGatesOrReuseFinishedOnes() {
        UUID id = create().id();

        assertThatThrownBy(() -> service.approveShortlist(id, List.of(1L), "ip")).isInstanceOf(WorkflowStepException.class);
        assertThatThrownBy(() -> service.finalizeRun(id, List.of(1L))).isInstanceOf(WorkflowStepException.class);
        verify(pipeline, never()).judge(anyString(), anyList(), anyMap());
    }

    @Test
    void rewindReturnsToAnEarlierGateAndKeepsTheLastDecision() {
        UUID id = create().id();
        var ranking = List.of(new RankResponse.Ranked(1, 0.6, null));
        when(pipeline.rank(anyString(), anyList())).thenReturn(new RankResponse("embed", ranking));
        when(pipeline.proposePool(anyList(), anyMap(), anyInt())).thenReturn(ranking);
        service.approveRepos(id, List.of(1L));

        WorkflowView back = service.rewind(id, WorkflowStep.REVIEW_REPOS);

        assertThat(back.step()).isEqualTo(WorkflowStep.REVIEW_REPOS);
        assertThat(back.repos()).filteredOn(WorkflowView.RepoOption::selected)
                .extracting(WorkflowView.RepoOption::id).containsExactly(1L);
        // Re-approving the gate works again after going back.
        assertThat(service.approveRepos(id, List.of(1L, 2L)).step()).isEqualTo(WorkflowStep.REVIEW_SHORTLIST);
    }

    @Test
    void rewindCannotGoForwardOrStayPut() {
        UUID id = create().id();
        assertThatThrownBy(() -> service.rewind(id, WorkflowStep.REVIEW_REPOS)).isInstanceOf(WorkflowStepException.class);
        assertThatThrownBy(() -> service.rewind(id, WorkflowStep.REVIEW_SHORTLIST)).isInstanceOf(WorkflowStepException.class);
    }

    @Test
    void rejectsEmptyOrForeignSelections() {
        UUID id = create().id();
        assertThatThrownBy(() -> service.approveRepos(id, List.of(999L))).isInstanceOf(InvalidSelectionException.class);
    }

    @Test
    void usageLimitBlocksTheLlmCall() {
        UUID id = create().id();
        var ranking = List.of(new RankResponse.Ranked(1, 0.6, null));
        when(pipeline.rank(anyString(), anyList())).thenReturn(new RankResponse("embed", ranking));
        when(pipeline.proposePool(anyList(), anyMap(), anyInt())).thenReturn(ranking);
        service.approveRepos(id, List.of(1L));
        doThrow(new UsageLimitException("limit", Duration.ofMinutes(5))).when(usage).acquireLlmCall("ip");

        assertThatThrownBy(() -> service.approveShortlist(id, List.of(1L), "ip")).isInstanceOf(UsageLimitException.class);
        verify(pipeline, never()).judge(anyString(), anyList(), anyMap());
        assertThat(db.get(id).getStep()).isEqualTo(WorkflowStep.REVIEW_SHORTLIST);
    }
}
