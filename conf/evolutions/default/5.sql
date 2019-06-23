# --- !Ups
CREATE TABLE accounts(
  id varchar(56) PRIMARY KEY,
  seed bytea
);


# --- !Downs

DROP TABLE accounts;
