-- Resolve a learner's cross-book progress without scanning every catalog word.
CREATE INDEX idx_vocabulary_term_book ON vocabulary_word(term_key,book_id);
