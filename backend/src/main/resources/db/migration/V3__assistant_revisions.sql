CREATE TABLE assistant_revisions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_id TEXT NOT NULL REFERENCES assistant_plans(id),
    version INTEGER NOT NULL,
    instruction TEXT NOT NULL,
    engine TEXT NOT NULL,
    reply TEXT NOT NULL,
    before_json TEXT NOT NULL,
    after_json TEXT NOT NULL,
    created TEXT NOT NULL,
    UNIQUE(plan_id, version)
);
CREATE INDEX assistant_revision_history ON assistant_revisions(plan_id, version);
