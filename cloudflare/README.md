# rain collector — Cloudflare Worker（埋点 + 收件箱公网接收端）

免费方案：Cloudflare Workers 免费层（10 万请求/天）+ D1 免费层（5GB）。**只需三步，全部在终端完成**。

## 上线三步

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

## 接入前台（让公网访客的埋点/表单生效）

编辑 `src/data/site.js`：

```js
export const apiBase = 'https://rain-collector.<你的子域>.workers.dev'
```

commit + push（或 `python tools/sync-via-api.py`）→ Actions 自动重新部署 → 公网访客的埋点与表单开始写入 D1。

## 接入管理台（让概览/收件箱读公网数据）

管理台侧栏底部「数据源」按钮，填入：

- Worker 地址：`https://rain-collector.<你的子域>.workers.dev`
- ADMIN_TOKEN：与第 2 步相同的密钥

配置存浏览器 localStorage；未配置或请求失败时自动回退本地 Spring 后端（本地联调模式）。

## 本地调试

```bash
npx wrangler dev            # 本地起 Worker（D1 用本地模拟）
curl -X POST http://localhost:8787/t -H "content-type: application/json" -d '{"path":"/test"}'
```

## 接口一览

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/t` | 公开 | 前台埋点 {path}（同 ip+path+分钟去重） |
| POST | `/messages` | 公开 | 联系表单（同 IP 60s 限频 429） |
| GET | `/stats/summary` | Bearer | PV/UV/14 天趋势/Top5（北京时间口径） |
| GET | `/messages` | Bearer | 收件箱列表 |
| PUT | `/messages/:id/read` | Bearer | 标记已读 |

## 成本与限额（免费层）

- Workers：100,000 请求/天（个人作品集远用不完）
- D1：5GB 存储 + 500 万行读/天
- 无信用卡要求
