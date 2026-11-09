-- 0001: referrer 落库 / 简历下载计数 / 留言归档（2026-11-09 审计修复批次）
-- 应用：npx wrangler d1 migrations apply rain-collector --remote

ALTER TABLE visits ADD COLUMN referrer TEXT;
ALTER TABLE messages ADD COLUMN archived INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS downloads (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  ip_hash TEXT NOT NULL,
  minute_bucket TEXT NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_downloads_dedup ON downloads (ip_hash, minute_bucket);

-- 收件箱 60s 限频查询按 ip_hash 走索引
CREATE INDEX IF NOT EXISTS idx_messages_ip_created ON messages (ip_hash, created_at);
