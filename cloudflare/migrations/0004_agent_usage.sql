-- 0004 · AI 分身用量计数（2026-11-20 审计跟进）：堵 verify- 会话隐形烧 neurons 的通道。
-- 原日上限按 agent_messages 计数，verify- 探针/伪造会话不落库即不计——实测 11-16~11-20
-- D1 零真实流量但额度再次耗尽，隐形滥用面成立。改为独立用量表：所有会话（含 verify-）
-- 每次对话 upsert +1，per-IP 日上限按本表判定。
-- 线上执行：npx wrangler d1 migrations apply rain-collector --remote

CREATE TABLE IF NOT EXISTS agent_usage (
  day TEXT NOT NULL,
  ip_hash TEXT NOT NULL,
  n INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (day, ip_hash)
);
