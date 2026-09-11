-- Human-in-the-loop runs: each row is one analysis moving through review gates.
CREATE TABLE workflow (
    id              UUID PRIMARY KEY,
    username        VARCHAR(100) NOT NULL,
    job_description TEXT         NOT NULL,
    top_k           INT          NOT NULL,
    include_forks   BOOLEAN      NOT NULL,
    step            VARCHAR(30)  NOT NULL,
    state           JSONB        NOT NULL,
    analysis_id     UUID REFERENCES analysis (id),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_workflow_username_created ON workflow (username, created_at DESC);
