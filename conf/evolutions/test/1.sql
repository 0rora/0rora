# --- !Ups

CREATE TABLE payments(
    id                   SERIAL PRIMARY KEY,
    source               VARCHAR(256) NOT NULL,
    destination          VARCHAR(256) NOT NULL,
    code                 VARCHAR(12) NOT NULL,
    issuer               CHAR(56),
    units                BIGINT NOT NULL,
    received             TIMESTAMP NOT NULL,
    scheduled            TIMESTAMP NOT NULL,
    submitted            TIMESTAMP,
    status               VARCHAR(9),
    op_result            VARCHAR(64),
    source_resolved      CHAR(56),
    destination_resolved CHAR(56)
);

# --- !Downs

DROP TABLE payments;
