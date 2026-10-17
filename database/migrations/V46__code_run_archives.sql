ALTER TABLE code_run
 ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN title VARCHAR(120) NOT NULL DEFAULT '',
 ADD COLUMN memo VARCHAR(2000) NOT NULL DEFAULT '';
CREATE INDEX idx_code_run_owner_archive ON code_run(owner_id,archived,created_at,id);
