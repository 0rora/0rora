# --- !Ups
CREATE TABLE users(
  id varchar(255) PRIMARY KEY,
  username varchar(255),
  password varchar(255),
  linkedid varchar(255),
  serializedprofile varchar(10000)
);

CREATE INDEX username_idx on users (username);
CREATE INDEX linkedid_idx on users (linkedid);


# --- !Downs

DROP TABLE users CASCADE;
