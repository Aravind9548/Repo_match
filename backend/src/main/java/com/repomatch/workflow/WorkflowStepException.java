package com.repomatch.workflow;

/** An approval arrived for a gate the workflow isn't at (already passed, or not reached yet). */
public class WorkflowStepException extends RuntimeException {
    public WorkflowStepException(WorkflowStep current) {
        super("That step isn't available: this workflow is at " + current + ". Reload to see its current state.");
    }
}
