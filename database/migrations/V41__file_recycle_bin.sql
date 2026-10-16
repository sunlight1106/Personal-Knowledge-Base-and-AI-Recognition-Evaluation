ALTER TABLE file_asset ADD COLUMN deleted_at TIMESTAMP(6) NULL;
CREATE INDEX idx_file_owner_deleted ON file_asset(owner_id,deleted_at,created_at,id);
