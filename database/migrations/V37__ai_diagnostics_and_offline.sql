CREATE TABLE ai_diagnostic (
 id VARCHAR(80) NOT NULL PRIMARY KEY, owner_id BIGINT NOT NULL,
 setting_id VARCHAR(36) NOT NULL, revision BIGINT NOT NULL, mode VARCHAR(16) NOT NULL,
 success BOOLEAN NOT NULL, code VARCHAR(80) NOT NULL, checked_at TIMESTAMP(6) NOT NULL,
 INDEX idx_diagnostic_owner(owner_id,setting_id),
 FOREIGN KEY(owner_id) REFERENCES app_user(id) ON DELETE CASCADE
);
CREATE TABLE offline_study_pack (
 id VARCHAR(36) NOT NULL PRIMARY KEY, owner_id BIGINT NOT NULL,
 payload MEDIUMTEXT NOT NULL, created_at TIMESTAMP(6) NOT NULL, expires_at TIMESTAMP(6) NOT NULL,
 INDEX idx_offline_owner(owner_id,expires_at),
 FOREIGN KEY(owner_id) REFERENCES app_user(id) ON DELETE CASCADE
);
