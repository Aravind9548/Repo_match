CREATE TABLE github_account (
    login         VARCHAR(100) PRIMARY KEY,   -- lower-cased GitHub login
    display_login VARCHAR(100) NOT NULL,
    name          VARCHAR(255),
    avatar_url    TEXT,
    html_url      TEXT,
    public_repos  INT,
    synced_at     TIMESTAMPTZ  NOT NULL
);

CREATE TABLE repo (
    id                     BIGINT PRIMARY KEY,  -- GitHub repository id
    owner_login            VARCHAR(100) NOT NULL REFERENCES github_account (login) ON DELETE CASCADE,
    name                   VARCHAR(255) NOT NULL,
    full_name              VARCHAR(255) NOT NULL,
    description            TEXT,
    html_url               TEXT         NOT NULL,
    homepage               TEXT,
    primary_language       VARCHAR(100),
    languages              JSONB        NOT NULL DEFAULT '{}'::jsonb,
    topics                 JSONB        NOT NULL DEFAULT '[]'::jsonb,
    stars                  INT          NOT NULL DEFAULT 0,
    fork                   BOOLEAN      NOT NULL,
    archived               BOOLEAN      NOT NULL,
    pushed_at              TIMESTAMPTZ,
    readme                 TEXT,
    profile_text           TEXT         NOT NULL,
    profile_hash           VARCHAR(64)  NOT NULL,
    embedding              REAL[],
    embedding_model        VARCHAR(100),
    embedding_profile_hash VARCHAR(64),
    fetched_at             TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_repo_owner ON repo (owner_login);

CREATE TABLE analysis (
    id              UUID PRIMARY KEY,
    username        VARCHAR(100) NOT NULL,
    job_description TEXT         NOT NULL,
    top_k           INT          NOT NULL,
    embedding_model VARCHAR(100),
    llm_model       VARCHAR(100),
    results         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_analysis_username_created ON analysis (username, created_at DESC);
