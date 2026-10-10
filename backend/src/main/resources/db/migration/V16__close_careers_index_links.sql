-- Some postings point at a company's careers index rather than at the role they name. Opening one lands on a list
-- of every job, which is exactly the wasted click the feed exists to prevent.
UPDATE job_posting
SET closed = TRUE
WHERE NOT closed
  AND apply_url ~* '/(careers?|jobs|openings|positions|vacancies|opportunities|join-us|work-with-us|current-openings)/?$';
