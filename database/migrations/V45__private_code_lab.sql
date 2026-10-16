CREATE TABLE code_run (
 id VARCHAR(36) PRIMARY KEY,
 owner_id BIGINT NOT NULL,
 language VARCHAR(16) NOT NULL,
 entry_name VARCHAR(60) NOT NULL,
 source MEDIUMTEXT NOT NULL,
 arguments_json TEXT NOT NULL,
 result_json MEDIUMTEXT NOT NULL,
 status VARCHAR(16) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_code_run_owner FOREIGN KEY(owner_id) REFERENCES app_user(id) ON DELETE CASCADE,
 INDEX idx_code_run_owner_time(owner_id,created_at,id)
);
-- New capability only for standard learning roles; custom role grants remain explicit.
INSERT INTO role_permission(role_id,permission_code)
SELECT id,'code-lab:use' FROM app_role r WHERE code IN ('RESEARCHER','VIEWER')
AND NOT EXISTS (SELECT 1 FROM role_permission p WHERE p.role_id=r.id AND p.permission_code='code-lab:use');
