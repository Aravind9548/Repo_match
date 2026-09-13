package com.repomatch.jobs;

/** Where a source's postings come from. */
public enum SourceType {
    /** A community-maintained README table on GitHub (e.g. SimplifyJobs). */
    GITHUB_LIST,
    /** A company's public job board API — no key needed. */
    GREENHOUSE, LEVER, ASHBY, SMARTRECRUITERS
}
