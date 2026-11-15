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

-- AI 分身（2026-11-15 P0，migration 0003）：会话/消息/访客画像三张表。
-- agent_visitor_notes 为 L2 跨会话记忆（P1 启用蒸馏写入，P0 只读注入）。
CREATE TABLE IF NOT EXISTS agent_sessions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id TEXT NOT NULL UNIQUE,
  visitor_id TEXT,
  ip_hash TEXT NOT NULL,
  turns INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_agent_sessions_visitor ON agent_sessions (visitor_id);

CREATE TABLE IF NOT EXISTS agent_messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id TEXT NOT NULL,
  role TEXT NOT NULL, -- 'user' | 'assistant'
  content TEXT NOT NULL,
  ip_hash TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_agent_messages_session ON agent_messages (session_id, id);
CREATE INDEX IF NOT EXISTS idx_agent_messages_ip_created ON agent_messages (ip_hash, created_at);

CREATE TABLE IF NOT EXISTS agent_visitor_notes (
  visitor_id TEXT PRIMARY KEY,
  notes TEXT NOT NULL DEFAULT '',
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);
