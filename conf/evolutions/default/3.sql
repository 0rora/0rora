# -- Ups

ALTER TABLE payments ALTER COLUMN source type VARCHAR(256);
ALTER TABLE payments ALTER COLUMN destination type VARCHAR(256);

# -- Downs

ALTER TABLE payments ALTER COLUMN source type CHAR(56);
ALTER TABLE payments ALTER COLUMN destination type CHAR(56);
