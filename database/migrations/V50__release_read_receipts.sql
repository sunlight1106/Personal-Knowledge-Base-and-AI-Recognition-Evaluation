CREATE TABLE release_read_receipt (
    owner_id BIGINT NOT NULL,
    release_version VARCHAR(20) NOT NULL,
    read_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (owner_id, release_version),
    CONSTRAINT fk_release_read_owner FOREIGN KEY (owner_id) REFERENCES app_user(id) ON DELETE CASCADE
);
