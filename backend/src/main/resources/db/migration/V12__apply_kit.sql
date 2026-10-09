-- The answers every application asks for, kept once, plus the per-job kit built from them.

CREATE TABLE applicant_profile (
    owner_key_hash  VARCHAR(64) PRIMARY KEY,
    full_name       VARCHAR(200),
    email           VARCHAR(320),
    phone           VARCHAR(40),
    location        VARCHAR(200),
    github_url      VARCHAR(300),
    linkedin_url    VARCHAR(300),
    portfolio_url   VARCHAR(300),
    -- "Indian citizen, no sponsorship needed", "Immediate", "Graduating May 2027", etc.
    work_authorization VARCHAR(300),
    notice_period   VARCHAR(120),
    expected_salary VARCHAR(120),
    graduation      VARCHAR(120),
    -- Anything else the person wants autofilled: [{"label":"Gender","value":"..."}]
    extras          TEXT,
    updated_at      TIMESTAMPTZ NOT NULL
);

-- One kit per application: the drafted answers for that specific job.
CREATE TABLE apply_kit (
    id              BIGSERIAL PRIMARY KEY,
    owner_key_hash  VARCHAR(64) NOT NULL,
    application_id  BIGINT NOT NULL REFERENCES application (id) ON DELETE CASCADE,
    tailor_run_id   UUID REFERENCES tailor_run (id) ON DELETE SET NULL,
    -- [{"question":"Why this company?","answer":"...","grounded_on":"RepoMatch README"}]
    answers         TEXT NOT NULL,
    model           VARCHAR(60),
    created_at      TIMESTAMPTZ NOT NULL,
    UNIQUE (application_id)
);

-- Alerts can prepare everything in the background: tailored resume + kit, ready when the person looks.
ALTER TABLE job_watch ADD COLUMN auto_prepare BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE job_watch ADD COLUMN auto_resume_id BIGINT REFERENCES resume_doc (id) ON DELETE SET NULL;
ALTER TABLE job_watch ADD COLUMN auto_github_username VARCHAR(40);
