-- The resume is read back out of the built PDF the way an applicant tracking system would read it, and the job's
-- requirements checked against what came out. The report is kept with the run so it survives a reload.
ALTER TABLE tailor_run ADD COLUMN ats_report TEXT;
ALTER TABLE tailor_run ADD COLUMN ats_checked_at TIMESTAMPTZ;
