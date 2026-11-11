-- 0002 · 留言软删/回收站（2026-11-11 管理台功能审计 F1）
-- 对齐本地 Spring 契约：deleted=0 常规列表；deleted=1 回收站；软删/恢复/彻底删语义一致。
-- 线上执行：npx wrangler d1 migrations apply rain-collector --remote

ALTER TABLE messages ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0;
ALTER TABLE messages ADD COLUMN deleted_at TEXT;

CREATE INDEX IF NOT EXISTS idx_messages_deleted ON messages (deleted, deleted_at);
