CREATE TABLE runtime_operation_lock (
 name VARCHAR(60) PRIMARY KEY, token VARCHAR(36), expires_at TIMESTAMP(6) NOT NULL
);
INSERT INTO runtime_operation_lock(name,expires_at) VALUES ('object-snapshot','2000-01-01 00:00:00');
