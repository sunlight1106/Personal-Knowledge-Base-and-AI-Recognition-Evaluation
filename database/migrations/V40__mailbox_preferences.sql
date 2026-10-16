CREATE TABLE mailbox_preference (
 id VARCHAR(36) PRIMARY KEY,
 user_id BIGINT NOT NULL,
 message_id CHAR(36) NOT NULL,
 state VARCHAR(12) NOT NULL,
 CONSTRAINT uk_mailbox_owner_message UNIQUE(user_id,message_id),
 CONSTRAINT fk_mailbox_user FOREIGN KEY(user_id) REFERENCES app_user(id) ON DELETE CASCADE,
 CONSTRAINT fk_mailbox_message FOREIGN KEY(message_id) REFERENCES internal_message(id) ON DELETE CASCADE
);
CREATE INDEX idx_mailbox_state ON mailbox_preference(user_id,state,message_id);
CREATE INDEX idx_group_invitation_actor ON group_invitation(actor_id,created_at,id);
