-- Indexing reads files from the repository's default branch.
ALTER TABLE repo ADD COLUMN default_branch VARCHAR(255);

-- "Ask your codebase": indexed source of a repository, chunked and embedded.
CREATE TABLE repo_index (
    repo_id      BIGINT PRIMARY KEY REFERENCES repo (id) ON DELETE CASCADE,
    commit_sha   VARCHAR(64),
    file_count   INT         NOT NULL DEFAULT 0,
    chunk_count  INT         NOT NULL DEFAULT 0,
    skipped_files INT        NOT NULL DEFAULT 0,
    model        VARCHAR(100),
    indexed_at   TIMESTAMPTZ NOT NULL
);

CREATE TABLE code_chunk (
    id         BIGSERIAL PRIMARY KEY,
    repo_id    BIGINT       NOT NULL REFERENCES repo (id) ON DELETE CASCADE,
    path       VARCHAR(500) NOT NULL,
    start_line INT          NOT NULL,
    end_line   INT          NOT NULL,
    content    TEXT         NOT NULL,
    embedding  REAL[]       NOT NULL
);

CREATE INDEX idx_code_chunk_repo ON code_chunk (repo_id);
