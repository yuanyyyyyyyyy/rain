-- 0003 · AI 分身（2026-11-15 P0）：会话/消息/访客画像三张表。
-- L1 会话内记忆 = agent_messages（每轮 user+assistant 两条，端点按 session_id 取最近 12 条拼上下文）；
-- agent_visitor_notes 为 L2 跨会话记忆（P0 只建表只读；P1 启用蒸馏写入后注入 system prompt）。
-- 命名/索引沿用既有约定：AUTOINCREMENT id、ip_hash 脱敏、datetime('now') 文本时间、idx_ 前缀。
-- 线上执行：npx wrangler d1 migrations apply rain-collector --remote

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
