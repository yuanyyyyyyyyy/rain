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
| GET | `/stats/summary` | Bearer | PV/UV/14 天趋势/Top5/Top referrers/简历下载数（北京时间口径） |
| GET | `/messages` | Bearer | 收件箱列表（含 read / archived） |
| PUT | `/messages/read-all` | Bearer | 全部标记已读 |
| PUT | `/messages/:id`、`/messages/:id/read` | Bearer | 标记已读 |
| PUT | `/messages/:id/archive` | Bearer | 归档 / 取消归档（body `{"archived":0}` 取消） |

鉴权失败（含 ADMIN_TOKEN 未配置）一律 401。

## 成本与限额（免费层）

- Workers：100,000 请求/天（个人作品集远用不完）
- D1：5GB 存储 + 500 万行读/天
- 无信用卡要求
