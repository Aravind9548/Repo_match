package com.repomatch.workflow;

import java.util.ArrayList;
import java.util.List;

import com.repomatch.ai.dto.ExplainResponse;
import com.repomatch.analysis.MatchResult;

/** Everything a workflow has decided and computed so far; stored as JSON on the workflow row. */
public class WorkflowState {

    /** Step 1 decision: repos the human wants considered. */
    public List<Long> selectedRepoIds = new ArrayList<>();

    /** Step 2 output: similarity ranking of the selected repos, best first. */
    public List<Ranked> ranking = new ArrayList<>();
    public String embeddingModel;

    /** Step 2 decision: repos the LLM should judge (pre-filled with a proposal). */
    public List<Long> shortlistIds = new ArrayList<>();

    /** Step 3 output: scored LLM verdicts, best first. */
    public List<MatchResult> judgements = new ArrayList<>();
    public String llmModel;
    public ExplainResponse.Usage usage;

    /** Step 3 decision: picks the human kept (pre-filled with the top-k). */
    public List<Long> acceptedIds = new ArrayList<>();

    public List<String> warnings = new ArrayList<>();

    public record Ranked(long id, double similarity) {
    }
}
