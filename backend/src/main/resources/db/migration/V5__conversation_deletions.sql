-- Keep linked billing evidence intact when a conversation is removed from history.
CREATE TABLE assistant_conversation_deletions (
    plan_id TEXT PRIMARY KEY REFERENCES assistant_plans(id),
    deleted_at TEXT NOT NULL
);
