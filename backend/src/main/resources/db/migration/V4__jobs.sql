-- Job discovery: curated GitHub job-list repositories, scraped into postings.
CREATE TABLE job_source (
    id              BIGSERIAL PRIMARY KEY,
    full_name       VARCHAR(200) NOT NULL UNIQUE,
    branch          VARCHAR(100) NOT NULL,
    path            VARCHAR(300) NOT NULL DEFAULT 'README.md',
    -- What a posting from this list is, unless its title says otherwise.
    default_kind    VARCHAR(20)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_checked_at TIMESTAMPTZ,
    last_count      INT          NOT NULL DEFAULT 0,
    last_error      TEXT
);

INSERT INTO job_source (full_name, branch, default_kind) VALUES
    ('SimplifyJobs/Summer2027-Internships', 'dev', 'INTERNSHIP'),
    ('SimplifyJobs/New-Grad-Positions', 'dev', 'NEW_GRAD'),
    ('speedyapply/2026-SWE-College-Jobs', 'main', 'INTERNSHIP');

CREATE TABLE job_posting (
    id            BIGSERIAL PRIMARY KEY,
    source_id     BIGINT        NOT NULL REFERENCES job_source (id) ON DELETE CASCADE,
    company       VARCHAR(300)  NOT NULL,
    role          VARCHAR(500)  NOT NULL,
    location      VARCHAR(500),
    kind          VARCHAR(20)   NOT NULL,
    -- Tracking parameters stripped, so the same job listed twice is one row.
    apply_url     VARCHAR(2000) NOT NULL UNIQUE,
    closed        BOOLEAN       NOT NULL DEFAULT FALSE,
    age_label     VARCHAR(20),
    first_seen_at TIMESTAMPTZ   NOT NULL,
    last_seen_at  TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_job_posting_first_seen ON job_posting (first_seen_at DESC);

-- A person's saved search. There are no accounts: whoever holds the key owns it.
CREATE TABLE job_watch (
    id                BIGSERIAL PRIMARY KEY,
    owner_key_hash    VARCHAR(64)  NOT NULL,
    name              VARCHAR(120) NOT NULL,
    email             VARCHAR(320),
    keywords          TEXT[]       NOT NULL DEFAULT '{}',
    exclude_keywords  TEXT[]       NOT NULL DEFAULT '{}',
    locations         TEXT[]       NOT NULL DEFAULT '{}',
    kinds             TEXT[]       NOT NULL DEFAULT '{}',
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL,
    last_notified_at  TIMESTAMPTZ
);

CREATE INDEX idx_job_watch_owner ON job_watch (owner_key_hash);

-- A posting that matched a watch and is waiting for the person's decision.
CREATE TABLE job_match (
    id          BIGSERIAL PRIMARY KEY,
    watch_id    BIGINT      NOT NULL REFERENCES job_watch (id) ON DELETE CASCADE,
    posting_id  BIGINT      NOT NULL REFERENCES job_posting (id) ON DELETE CASCADE,
    status      VARCHAR(20) NOT NULL,
    matched_at  TIMESTAMPTZ NOT NULL,
    emailed_at  TIMESTAMPTZ,
    UNIQUE (watch_id, posting_id)
);

-- Applications the person approved, and everything that happened after.
CREATE TABLE application (
    id              BIGSERIAL PRIMARY KEY,
    owner_key_hash  VARCHAR(64)  NOT NULL,
    posting_id      BIGINT       REFERENCES job_posting (id) ON DELETE SET NULL,
    company         VARCHAR(300) NOT NULL,
    role            VARCHAR(500) NOT NULL,
    apply_url       VARCHAR(2000),
    status          VARCHAR(20)  NOT NULL,
    resume_choice   VARCHAR(20),
    next_step       VARCHAR(300),
    next_step_due   DATE,
    notes           TEXT,
    applied_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    reminded_at     TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (owner_key_hash, posting_id)
);

CREATE INDEX idx_application_owner ON application (owner_key_hash, updated_at DESC);

CREATE TABLE application_event (
    id              BIGSERIAL PRIMARY KEY,
    application_id  BIGINT      NOT NULL REFERENCES application (id) ON DELETE CASCADE,
    status          VARCHAR(20),
    note            TEXT,
    occurred_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_application_event_app ON application_event (application_id, occurred_at DESC);
