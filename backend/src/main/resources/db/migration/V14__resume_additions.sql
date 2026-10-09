-- The person can answer a gap in their own words, and correct any line of the final resume by hand. Both are kept
-- apart from the model's plan: these are theirs, and they survive re-running the suggestions.
ALTER TABLE tailor_run ADD COLUMN additions TEXT;
ALTER TABLE tailor_run ADD COLUMN manual_edits TEXT;
