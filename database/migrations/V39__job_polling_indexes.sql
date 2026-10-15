-- Match owner pagination and queue ordering without sorting the growing history.
CREATE INDEX idx_job_owner_created ON workspace_job(owner_id, created_at DESC, id);
CREATE INDEX idx_job_queued_created ON workspace_job(status, created_at, id);
