CREATE TABLE study_plan (
 owner_id BIGINT PRIMARY KEY, minutes INT NOT NULL DEFAULT 20,
 spread_days INT NOT NULL DEFAULT 7, revision BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(owner_id) REFERENCES app_user(id) ON DELETE CASCADE
);
CREATE TABLE practice_followup (
 id VARCHAR(96) PRIMARY KEY, owner_id BIGINT NOT NULL, term_key VARCHAR(100) NOT NULL,
 kind VARCHAR(20) NOT NULL, detail VARCHAR(500) NOT NULL, due_date DATE NOT NULL,
 independent_passes INT NOT NULL DEFAULT 0, last_pass DATE,
 INDEX idx_followup_due(owner_id,due_date),
 FOREIGN KEY(owner_id) REFERENCES app_user(id) ON DELETE CASCADE
);
CREATE TABLE workspace_job (
 id VARCHAR(36) PRIMARY KEY, owner_id BIGINT NOT NULL, kind VARCHAR(32) NOT NULL,
 title VARCHAR(200) NOT NULL, status VARCHAR(20) NOT NULL, resource_id VARCHAR(36),
 result_id VARCHAR(36), message VARCHAR(500), attempts INT NOT NULL DEFAULT 0,
 progress INT NOT NULL DEFAULT 0, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 lease_until TIMESTAMP(6), request_key VARCHAR(100) NOT NULL,
 UNIQUE KEY uq_job_request(owner_id,request_key), INDEX idx_job_queue(status,lease_until,created_at),
 FOREIGN KEY(owner_id) REFERENCES app_user(id) ON DELETE CASCADE
);
CREATE TABLE reading_clip (
 id VARCHAR(36) PRIMARY KEY, owner_id BIGINT NOT NULL, source_kind VARCHAR(20) NOT NULL,
 source_id VARCHAR(36) NOT NULL, quote_text VARCHAR(4000) NOT NULL, term VARCHAR(100),
 source_title VARCHAR(200) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 UNIQUE KEY uq_reading_clip(owner_id,id), INDEX idx_clip_owner(owner_id,created_at),
 FOREIGN KEY(owner_id) REFERENCES app_user(id) ON DELETE CASCADE
);
ALTER TABLE study_document ADD COLUMN ocr BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE document_passage ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
