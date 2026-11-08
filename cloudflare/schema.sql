-- rain collector D1 schema（wrangler d1 execute rain-collector --remote --file schema.sql）

CREATE TABLE IF NOT EXISTS visits (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  ip_hash TEXT NOT NULL,
  path TEXT NOT NULL,
  minute_bucket TEXT NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_visits_dedup ON visits (ip_hash, path, minute_bucket);
CREATE INDEX IF NOT EXISTS idx_visits_bucket ON visits (minute_bucket);

CREATE TABLE IF NOT EXISTS messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  email TEXT NOT NULL,
  body TEXT NOT NULL,
  read INTEGER NOT NULL DEFAULT 0,
  ip_hash TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
