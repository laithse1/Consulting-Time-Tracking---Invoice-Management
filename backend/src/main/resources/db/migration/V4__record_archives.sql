CREATE TABLE record_archives (
    record_type TEXT NOT NULL CHECK(record_type IN ('clients','contracts','projects')),
    record_id INTEGER NOT NULL,
    archived_at TEXT NOT NULL,
    PRIMARY KEY(record_type, record_id)
);
