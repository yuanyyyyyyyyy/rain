# rain collector — Cloudflare Worker（埋点 + 收件箱公网接收端）

免费方案：Cloudflare Workers 免费层（10 万请求/天）+ D1 免费层（5GB）。**只需三步，全部在终端完成**。

> **部署状态（2026-11-09）**：已上线 `https://rain-collector.yuanyyyyyyyyy.workers.dev`，前台 `src/data/site.js` 已接线，D1 migration 0001 已应用。

## 上线三步（新装参考）

```bash
# 0. 登录（浏览器弹窗授权一次；需要你有 Cloudflare 账号，免费注册 dash.cloudflare.com）
npx wrangler login

# 1. 创建 D1 数据库并建表（把输出的 database_id 填进 wrangler.toml）
npx wrangler d1 create rain-collector
npx wrangler d1 execute rain-collector --remote --file schema.sql

# 2. 设置管理台读接口密钥（自己生成一个长随机串）
npx wrangler secret put ADMIN_TOKEN     # 例如 openssl rand -hex 24 的输出

# 3. 部署
npx wrangler deploy
```

部署完成后记下 Worker 地址：`https://rain-collector.<你的子域>.workers.dev`

已有库的 schema 演进走 migrations（不在本目录时用 `--remote`）：

```bash
npx wrangler d1 migrations apply rain-collector --remote
```

## AI 分身（2026-11-15 P0 / P1 同日）

前台右下角悬浮对话（`src/components/agent/AgentDock.vue`）→ Worker 端点 `POST /agent/chat`（实现在 `agent.js` + `vendor/runWithTools.js`）。要点：

- **模型**：Cloudflare Workers AI（`wrangler.toml` 的 `[ai]` binding = "AI"），无需任何 secret/密钥；免费层按 neurons 计量（**10,000/天，实测单次短答 ~8 neurons、工具轮 ~3 倍**，打满后端点会友好提示明日再来）
- **配置**（`[vars]`）：`AGENT_ON = "1"`（总开关，被刷时置 `"0"` 后 `wrangler deploy` 一键下线，对外 404）、`AGENT_MODEL`（默认 `@cf/meta/llama-3.3-70b-instruct-fp8-fast`）、可选 `AGENT_KNOWLEDGE_BASE`（本地联调覆盖知识源）
- **工具循环（P1）**：`get_project`（按需拉项目全文，6000 字符帽防 24k 上下文溢出）/ `search_site` / `submit_message`（服务端不执行，发 confirm 帧 → 前端确认卡 → 走既有 `/messages`）。**工具由代码层意图门控决定是否挂载**（`PROJECT_DETAIL_RE`/`MESSAGE_INTENT_RE`）——llama-3.3-70b 的工具自决纪律不可靠，实测无门控时寒暄/越狱/offer 类问题也误调工具。循环器是 vendor 化的 runWithTools（去 zod/yaml 依赖），处理 llama-3.3 流式 tool_calls 双份上报（官方 issue #2482）
- **知识源**：Worker 运行时 fetch `yuan-archive.pages.dev/content/*.json`（deploy-pages.yml 构建时拷入 dist；管理台发布后自动同步，isolate 内存缓存 10 分钟，取不到时续用旧缓存/降级）
- **记忆**：L1 会话内（D1 `agent_messages` 最近 12 条拼上下文，sessionStorage 承载会话）；L2 跨会话访客画像（`agent_visitor_notes`，每 2 轮蒸馏一次），`DELETE /agent/memory?visitor=…` 清除（前端「记忆」按钮）
- **防滥用**：全局断路器 30 次/分/isolate → per-IP 8 次/分（内存）→ per-IP 50 条/日（D1）→ 单会话 30 轮；四层全超才伤体验，正常访客无感
- **评测**：`node tools/agent-eval.mjs [代理地址]`——10 问回归人设/档案锚定/边界/排版（verify- 会话不落库）
- **上线步骤**：`npx wrangler d1 migrations apply rain-collector --remote`（0003 三张表）→ `npx wrangler deploy`；前端随 Pages 自动发布

## 接入前台（让公网访客的埋点/表单生效）

编辑 `src/data/site.js`：

```js
export const apiBase = 'https://rain-collector.<你的子域>.workers.dev'
```

commit + push（或 `python tools/sync-via-api.py`）→ Actions 自动重新部署 → 公网访客的埋点与表单开始写入 D1。

## 接入管理台（让概览/收件箱读公网数据）

管理台「06 / 设置 → 数据源」卡片（或侧栏底部「数据源」按钮），填入：

- Worker 地址：`https://rain-collector.<你的子域>.workers.dev`
- ADMIN_TOKEN：与第 2 步相同的密钥

配置存浏览器 localStorage；未配置或请求失败时自动回退本地 Spring 后端（本地联调模式）。

## 留言邮件通知（可选，任务 D）

新留言入库 D1 的同时转发一封通知邮件到站长邮箱。配置两步：

```bash
# 1. 注册 Brevo（brevo.com，免费 300 封/天）→ 发件人与安全性 → 验证发件人：
#    填你的站长邮箱（wrangler.toml [vars] OWNER_EMAIL，需点验证邮件确认一次）
# 2. 生成 SMTP & API 密钥，配置到 Worker secret：
npx wrangler secret put BREVO_API_KEY
npx wrangler deploy   # secret 更新后需重新部署生效
```

- 未配置 `BREVO_API_KEY` 时：留言照常入库，只是不发邮件（worker 静默跳过）。
- 防刷保护：24 小时内留言数超过 20 条后自动停止转发（仍入库），防表单刷爆免费配额。
- 通知邮件的「回复」直接送达访客邮箱（reply-to = 访客）。

## 本地调试

```bash
npx wrangler dev            # 本地起 Worker（D1 用本地模拟；本地库首次需 d1 execute --local --file schema.sql）
curl -X POST http://localhost:8787/api/t -H "content-type: application/json" -d '{"path":"/test"}'
```

## 接口一览

**`/api/` 前缀可剥离**：前台（Spring 契约）用 `/api/t`、`/api/messages`，管理台用根路径，两者等价。

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/t` | 公开 | 前台埋点 {path, referrer}（同 ip+path+分钟去重；同 ip 每分钟限 20 个不同 path） |
| POST | `/messages` | 公开 | 联系表单（同 IP 60s 限频 429；邮箱格式校验） |
| POST | `/resume/hit` | 公开 | 简历下载计数 beacon（同 ip+分钟去重；前台 Hero 按钮 click 时上报） |
| POST | `/agent/chat` | 公开 | AI 分身对话，SSE 流式 {sessionId, visitorId?, message}（意图门控工具循环 + 两层记忆；断路器/per-IP/会话轮数四层限流；实现在 agent.js + vendor/runWithTools.js） |
| DELETE | `/agent/memory?visitor=…` | 公开 | 清除该访客的 L2 跨会话画像（前端「记忆」按钮；visitor 为浏览器本地 UUID） |
| GET | `/stats/summary` | Bearer | PV/UV/14 天趋势/Top5/Top referrers/简历下载数（北京时间口径） |
| GET | `/messages` | Bearer | 收件箱列表（含 read / archived） |
| PUT | `/messages/read-all` | Bearer | 全部标记已读 |
| PUT | `/messages/:id`、`/messages/:id/read` | Bearer | 标记已读 |
| PUT | `/messages/:id/archive` | Bearer | 归档 / 取消归档（body `{"archived":0}` 取消） |

鉴权失败（含 ADMIN_TOKEN 未配置）一律 401。

## 成本与限额（免费层）

- Workers：100,000 请求/天（个人作品集远用不完）
- Workers AI：免费层每日 neurons 额度（用 AI 分身才消耗；全局断路器 30 次/分/isolate 兜底，实际用量见 CF 控制台 Workers AI 页）
- D1：5GB 存储 + 500 万行读/天
- 无信用卡要求
