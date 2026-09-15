-- Empty descriptions were cached before boards on company domains were handled; let them be fetched again.
UPDATE job_posting SET description = NULL, description_fetched_at = NULL WHERE description = '';
