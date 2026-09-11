package com.repomatch.api;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.repomatch.analysis.AnalyzeRequest;
import com.repomatch.workflow.WorkflowService;
import com.repomatch.workflow.WorkflowStep;
import com.repomatch.workflow.WorkflowView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Human-in-the-loop analysis: create a run, then approve each review gate in turn. */
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService workflows;

    public WorkflowController(WorkflowService workflows) {
        this.workflows = workflows;
    }

    /** The human's decision at a gate: which repos to keep. */
    public record Selection(@NotNull @Size(max = 500) List<Long> repoIds) {
    }

    /** Which earlier gate to return to. */
    public record Rewind(@NotNull WorkflowStep step) {
    }

    @PostMapping("/{id}/rewind")
    public WorkflowView rewind(@PathVariable UUID id, @Valid @RequestBody Rewind body) {
        return workflows.rewind(id, body.step());
    }

    @PostMapping
    public WorkflowView create(@Valid @RequestBody AnalyzeRequest request) {
        return workflows.create(request);
    }

    @GetMapping("/{id}")
    public WorkflowView get(@PathVariable UUID id) {
        return workflows.get(id);
    }

    @PostMapping("/{id}/repos")
    public WorkflowView approveRepos(@PathVariable UUID id, @Valid @RequestBody Selection selection) {
        return workflows.approveRepos(id, selection.repoIds());
    }

    @PostMapping("/{id}/shortlist")
    public WorkflowView approveShortlist(@PathVariable UUID id, @Valid @RequestBody Selection selection,
                                         HttpServletRequest http) {
        return workflows.approveShortlist(id, selection.repoIds(), http.getRemoteAddr());
    }

    @PostMapping("/{id}/finalize")
    public WorkflowView finalizeRun(@PathVariable UUID id, @Valid @RequestBody Selection selection) {
        return workflows.finalizeRun(id, selection.repoIds());
    }
}
