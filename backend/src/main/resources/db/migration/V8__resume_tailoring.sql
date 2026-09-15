-- Resume tailoring: the person's resumes, and one step-by-step tailoring run per job.

-- Full job description, fetched on demand from the posting's source and cached.
ALTER TABLE job_posting ADD COLUMN description TEXT;
ALTER TABLE job_posting ADD COLUMN description_fetched_at TIMESTAMPTZ;

CREATE TABLE resume_doc (
    id              BIGSERIAL PRIMARY KEY,
    owner_key_hash  VARCHAR(64)  NOT NULL,
    filename        VARCHAR(300),
    -- TEX when uploaded as LaTeX source (edits are applied to it, keeping the person's design); TEXT otherwise.
    kind            VARCHAR(10)  NOT NULL,
    source_text     TEXT         NOT NULL,
    -- Sections/items/bullets read from source_text once, reused by every later run (no second model call).
    structured      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_resume_doc_owner ON resume_doc (owner_key_hash, created_at DESC);

CREATE TABLE tailor_run (
    id                UUID PRIMARY KEY,
    owner_key_hash    VARCHAR(64)  NOT NULL,
    application_id    BIGINT       REFERENCES application (id) ON DELETE SET NULL,
    posting_id        BIGINT       REFERENCES job_posting (id) ON DELETE SET NULL,
    resume_id         BIGINT       REFERENCES resume_doc (id) ON DELETE SET NULL,
    -- REVIEW_RESUME → REVIEW_EDITS → DONE
    step              VARCHAR(20)  NOT NULL,
    company           VARCHAR(300),
    job_title         VARCHAR(500),
    job_description   TEXT,
    structured        TEXT,
    plan              TEXT,
    accepted_ids      TEXT,
    latex             TEXT,
    pdf               BYTEA,
    compiled          BOOLEAN      NOT NULL DEFAULT FALSE,
    compile_log       TEXT,
    used_own_template BOOLEAN      NOT NULL DEFAULT FALSE,
    missed_edits      TEXT,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_tailor_run_owner ON tailor_run (owner_key_hash, updated_at DESC);
CREATE INDEX idx_tailor_run_application ON tailor_run (application_id);
