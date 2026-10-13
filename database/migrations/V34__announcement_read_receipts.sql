ALTER TABLE workspace_notice ADD COLUMN announcement_revision BIGINT NOT NULL DEFAULT 0;
UPDATE workspace_notice SET announcement_revision=revision WHERE announcement<>'';
ALTER TABLE workspace_preference ADD COLUMN announcement_read_revision BIGINT NOT NULL DEFAULT 0;
