-- rain collector D1 schema（新装参考；线上库用 migrations/ 增量演进：
--   npx wrangler d1 migrations apply rain-collector --remote）

CREATE TABLE IF NOT EXISTS visits (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  ip_hash TEXT NOT NULL,
  path TEXT NOT NULL,
  minute_bucket TEXT NOT NULL,
  referrer TEXT
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_visits_dedup ON visits (ip_hash, path, minute_bucket);
CREATE INDEX IF NOT EXISTS idx_visits_bucket ON visits (minute_bucket);

CREATE TABLE IF NOT EXISTS messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  email TEXT NOT NULL,
  body TEXT NOT NULL,
  read INTEGER NOT NULL DEFAULT 0,
  archived INTEGER NOT NULL DEFAULT 0,
  deleted INTEGER NOT NULL DEFAULT 0,
  deleted_at TEXT,
  ip_hash TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_messages_ip_created ON messages (ip_hash, created_at);
CREATE INDEX IF NOT EXISTS idx_messages_deleted ON messages (deleted, deleted_at);

CREATE TABLE IF NOT EXISTS downloads (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  ip_hash TEXT NOT NULL,
  minute_bucket TEXT NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_downloads_dedup ON downloads (ip_hash, minute_bucket);
