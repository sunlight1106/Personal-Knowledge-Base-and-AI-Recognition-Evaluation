CREATE TABLE account_login_throttle (
    owner_id BIGINT NOT NULL PRIMARY KEY,
    failures INT NOT NULL,
    window_started_at TIMESTAMP(6) NOT NULL,
    blocked_until TIMESTAMP(6) NULL,
    CONSTRAINT fk_login_throttle_owner FOREIGN KEY (owner_id) REFERENCES app_user(id)
);
