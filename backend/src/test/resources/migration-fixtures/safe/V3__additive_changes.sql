ALTER TABLE example_rows ADD COLUMN note TEXT;
ALTER TABLE example_rows ALTER COLUMN note SET DEFAULT '';
CREATE INDEX idx_example_note ON example_rows (note);
DROP INDEX idx_example_note;
UPDATE example_rows SET note = 'backfill' WHERE note IS NULL;
GRANT SELECT, INSERT ON example_rows TO example_role;
