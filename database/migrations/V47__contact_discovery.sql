ALTER TABLE app_user
    ADD COLUMN contact_number VARCHAR(24) NULL,
    ADD COLUMN search_by_number BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN search_by_email BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN search_by_phone BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT uk_user_contact_number UNIQUE (contact_number);

CREATE TABLE phone_binding (
    owner_id BIGINT NOT NULL PRIMARY KEY,
    verified_phone VARCHAR(16) NULL,
    pending_phone VARCHAR(16) NULL,
    challenge_secret TEXT NULL,
    expires_at TIMESTAMP(6) NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_send_at TIMESTAMP(6) NULL,
    send_day VARCHAR(10) NULL,
    send_count INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_verified_phone UNIQUE (verified_phone),
    CONSTRAINT fk_phone_owner FOREIGN KEY (owner_id) REFERENCES app_user(id)
);
