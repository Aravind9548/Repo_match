-- The first Google discovery run kept links that only "opened", which let search-results pages through with a
-- company the page never named (an Indeed search page labelled "Microsoft"). Close everything that run found;
-- the next run re-adds only postings whose page names the company and title.
UPDATE job_posting
SET closed = TRUE
WHERE source_id = (SELECT id FROM job_source WHERE full_name = 'google:daily-discovery');

UPDATE job_source SET last_checked_at = NULL, last_count = 0 WHERE full_name = 'google:daily-discovery';
