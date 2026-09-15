-- Tailoring now also reads the person's GitHub, follows their instructions, can be held to one page,
-- and lets them pick the model.
ALTER TABLE tailor_run ADD COLUMN github_username VARCHAR(40);
ALTER TABLE tailor_run ADD COLUMN instructions TEXT;
ALTER TABLE tailor_run ADD COLUMN one_page BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE tailor_run ADD COLUMN model VARCHAR(20);
ALTER TABLE tailor_run ADD COLUMN pages INT;
