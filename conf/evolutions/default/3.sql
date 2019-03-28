# --- !Ups

ALTER TABLE payments ALTER COLUMN source TYPE VARCHAR(256);
ALTER TABLE payments ALTER COLUMN destination TYPE VARCHAR(256);
ALTER TABLE payments ALTER COLUMN status TYPE VARCHAR(10);
ALTER TABLE payments ADD COLUMN source_resolved CHAR(56);
ALTER TABLE payments ADD COLUMN destination_resolved CHAR(56);
ALTER TABLE payments DROP CONSTRAINT payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check CHECK (status IN ('pending', 'validating', 'valid', 'invalid', 'submitted', 'failed', 'succeeded'));

# --- !Downs

ALTER TABLE payments ALTER COLUMN source TYPE CHAR(56);
ALTER TABLE payments ALTER COLUMN destination TYPE CHAR(56);
ALTER TABLE payments ALTER COLUMN status TYPE VARCHAR(9);
ALTER TABLE payments DROP COLUMN source_resolved;
ALTER TABLE payments DROP COLUMN destination_resolved;
ALTER TABLE payments DROP CONSTRAINT payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check CHECK (status IN ('pending', 'submitted', 'failed', 'succeeded'));
