-- Postman's Greenhouse board stopped answering (404 on every known slug); keep it out of the sync.
UPDATE job_source SET enabled = FALSE, last_error = 'board returns 404' WHERE full_name = 'greenhouse:postman';
