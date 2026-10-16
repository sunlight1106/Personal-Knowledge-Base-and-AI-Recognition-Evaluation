-- InnoDB maintains these indexes with each committed edit; no external content copy.
CREATE FULLTEXT INDEX ft_note_search ON note(title,body,tags) WITH PARSER ngram;
CREATE FULLTEXT INDEX ft_entry_search ON knowledge_entry(title,body,tags) WITH PARSER ngram;
CREATE FULLTEXT INDEX ft_passage_search ON document_passage(body) WITH PARSER ngram;
CREATE FULLTEXT INDEX ft_message_search ON internal_message(subject,body) WITH PARSER ngram;
CREATE FULLTEXT INDEX ft_file_search ON file_asset(original_name) WITH PARSER ngram;
CREATE FULLTEXT INDEX ft_recognition_search ON personal_recognition_result(file_name,result_text) WITH PARSER ngram;
