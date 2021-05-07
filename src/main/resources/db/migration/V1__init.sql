
CREATE TABLE migration_index
(
    index_name   VARCHAR(128) primary KEY,
    env   VARCHAR(128) not null,
    total_hits   BIGINT not null
);

CREATE TABLE migration_document
(
    id           VARCHAR(64) primary KEY,
    index_name   VARCHAR(128) NOT NULL,
    data         text,
    reference          TEXT,
    migrated  boolean default false
);

CREATE INDEX idx_migration_document_index_name ON migration_document(index_name);