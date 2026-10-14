CREATE TABLE study_document (
 id VARCHAR(36) PRIMARY KEY, owner_id BIGINT NOT NULL, file_id CHAR(36) NOT NULL,
 title VARCHAR(255) NOT NULL, sha256 VARCHAR(64) NOT NULL, format VARCHAR(8) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_study_document_owner FOREIGN KEY(owner_id) REFERENCES app_user(id) ON DELETE CASCADE,
 CONSTRAINT fk_study_document_file FOREIGN KEY(file_id) REFERENCES file_asset(id) ON DELETE CASCADE,
 CONSTRAINT uq_study_document_hash UNIQUE(owner_id,sha256)
);
CREATE TABLE document_passage (
 id VARCHAR(36) PRIMARY KEY, document_id VARCHAR(36) NOT NULL, owner_id BIGINT NOT NULL,
 position_number INT NOT NULL, locator VARCHAR(60) NOT NULL, body TEXT NOT NULL,
 CONSTRAINT fk_document_passage_parent FOREIGN KEY(document_id) REFERENCES study_document(id) ON DELETE CASCADE,
 CONSTRAINT fk_document_passage_owner FOREIGN KEY(owner_id) REFERENCES app_user(id) ON DELETE CASCADE,
 INDEX ix_document_passage_parent(document_id,position_number)
);
