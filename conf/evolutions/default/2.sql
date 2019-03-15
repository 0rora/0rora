# --- !Ups

ALTER TABLE payments ALTER COLUMN units type BIGINT;



# --- !Downs

ALTER TABLE payments ALTER COLUMN units type NUMERIC;
