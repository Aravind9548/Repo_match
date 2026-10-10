-- A posting on a form or a company page is never de-listed; it just stops accepting applications while still
-- answering 200 OK. Each one is opened again on a rota, and this records when it was last looked at.
ALTER TABLE job_posting ADD COLUMN last_checked_at TIMESTAMPTZ;
CREATE INDEX idx_job_posting_recheck ON job_posting (last_checked_at NULLS FIRST) WHERE NOT closed;
