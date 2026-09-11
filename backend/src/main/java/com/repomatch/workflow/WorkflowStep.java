package com.repomatch.workflow;

/**
 * Review gates of a human-in-the-loop run. Automated work happens on the transitions between them:
 * REVIEW_REPOS --(rank)--> REVIEW_SHORTLIST --(LLM judges)--> REVIEW_RESULTS --(save)--> DONE.
 */
public enum WorkflowStep {
    /** Repos fetched from GitHub; the human chooses which ones count. */
    REVIEW_REPOS,
    /** Repos ranked by similarity; the human edits the shortlist the (paid) LLM will judge. */
    REVIEW_SHORTLIST,
    /** LLM verdicts are in; the human accepts or rejects each pick. */
    REVIEW_RESULTS,
    /** Accepted picks saved as an analysis. */
    DONE
}
