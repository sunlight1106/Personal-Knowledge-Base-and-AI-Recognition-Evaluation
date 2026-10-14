CREATE TABLE learning_event (
 id VARCHAR(120) PRIMARY KEY, owner_id BIGINT NOT NULL, kind VARCHAR(20) NOT NULL,
 source_id VARCHAR(100) NOT NULL, study_date DATE NOT NULL, correct BOOLEAN NULL,
 created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_learning_event_owner FOREIGN KEY(owner_id) REFERENCES app_user(id) ON DELETE CASCADE,
 INDEX ix_learning_event_owner_date(owner_id,study_date,kind)
);
CREATE TABLE learning_day (
 id VARCHAR(40) PRIMARY KEY, owner_id BIGINT NOT NULL, study_date DATE NOT NULL,
 active_seconds BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_learning_day_owner FOREIGN KEY(owner_id) REFERENCES app_user(id) ON DELETE CASCADE,
 CONSTRAINT uq_learning_day UNIQUE(owner_id,study_date)
);
CREATE TABLE learning_clock (
 owner_id BIGINT PRIMARY KEY, last_ping TIMESTAMP(6) NULL,
 CONSTRAINT fk_learning_clock_owner FOREIGN KEY(owner_id) REFERENCES app_user(id) ON DELETE CASCADE
);
INSERT INTO learning_event(id,owner_id,kind,source_id,study_date,correct,created_at)
 SELECT CONCAT('word:',q.id),q.owner_id,'WORD',w.term_key,q.study_date,q.answer_correct,q.answered_at
 FROM vocabulary_question q JOIN vocabulary_word w ON w.id=q.word_id
 WHERE q.answered_at IS NOT NULL AND q.study_date IS NOT NULL AND q.answer_correct IS NOT NULL;
ALTER TABLE vocabulary_progress ADD COLUMN scheduler_json TEXT NULL;
ALTER TABLE vocabulary_profile ADD COLUMN retention DOUBLE NOT NULL DEFAULT 0.9;
