# --- !Ups

CREATE TABLE payments(
    id                  SERIAL PRIMARY KEY,
    source              CHAR(56) NOT NULL,
    destination         CHAR(56) NOT NULL,
    code                VARCHAR(12) NOT NULL,
    issuer              CHAR(56),
    units               NUMERIC NOT NULL,
    received            TIMESTAMP NOT NULL,
    scheduled           TIMESTAMP NOT NULL,
    status              VARCHAR(9) NOT NULL
    CHECK (status IN ('pending', 'submitted', 'failed', 'succeeded'))
);

# --- !Downs

DROP TABLE payments;
DROP TYPE payment_status;
