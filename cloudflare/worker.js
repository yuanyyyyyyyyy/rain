/**
 * rain collector — Cloudflare Worker（免费层）
 * 前台埋点 + 联系表单 + 简历下载计数的公网接收端；管理台经 Bearer ADMIN_TOKEN 读统计与收件箱。
 * 存储：D1（绑定名 DB）。密钥：secret ADMIN_TOKEN（wrangler secret put ADMIN_TOKEN）。
 * 留言通知：secret BREVO_API_KEY + var OWNER_EMAIL（未配置则只入库不发信，任务 D）。
 * 部署见同目录 README.md。
 */

/**
 * CORS 白名单（2026-11-10 安全审计：原 ACAO:* 连管理接口一起放开）。
 * 浏览器调用方只有：GitHub Pages 前台 + 本机前台/管理台 dev；如换域名/新增端口在此追加。
 * 非浏览器调用（curl、CI 探针）不携带 Origin，不受影响。
 */
const ALLOWED_ORIGINS = new Set([
  "https://yuanyyyyyyyyy.github.io",
  "http://localhost:5173", "http://localhost:5174", "http://localhost:5175", "http://localhost:5176",
  "http://127.0.0.1:5173", "http://127.0.0.1:5174", "http://127.0.0.1:5175", "http://127.0.0.1:5176",
])

/** 基础安全响应头；CORS 头仅在 Origin 命中白名单时回显 */
function corsHeaders(request) {
  const origin = request.headers.get("origin") || ""
  const h = {
    "content-type": "application/json; charset=utf-8",
    "x-content-type-options": "nosniff",
    "referrer-policy": "no-referrer",
  }
  if (ALLOWED_ORIGINS.has(origin)) {
    h["access-control-allow-origin"] = origin
    h["access-control-allow-methods"] = "GET,POST,PUT,OPTIONS"
    h["access-control-allow-headers"] = "content-type,authorization"
    h.vary = "origin"
  }
  return h
}

/** 每日转发上限：防止刷表单烧穿 Brevo 免费配额（300 封/天，留余量） */
const NOTIFY_DAILY_CAP = 20

function json(request, body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders(request) })
}

function corsPreflight(request) {
  return new Response(null, { status: 204, headers: corsHeaders(request) })
}

async function sha256hex(s) {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s))
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("")
}

/**
 * 管理接口鉴权失败限流（isolate 内存表，单 Worker 足够）：同 IP 1 分钟内 ≥10 次失败 → 429。
 * 原 401 无任何限流，ADMIN_TOKEN 可被高频爆破。
 */
const AUTH_FAIL_LIMIT = 10
const authFails = new Map() // ipHash -> 失败时间戳数组

function recordAuthFail(ipHash) {
  const now = Date.now()
  const list = (authFails.get(ipHash) || []).filter((t) => now - t < 60_000)
  list.push(now)
  authFails.set(ipHash, list)
  if (authFails.size > 10_000) authFails.clear() // 兜底防内存增长
  return list.length
}

/** 管理台读接口鉴权；未配置 ADMIN_TOKEN 时一律拒绝（防止字面量 "Bearer undefined" 命中）。
 *  双方哈希后常数时间比较（2026-11-10 审计：原 === 直比，理论可时序侧信道）。 */
async function authorized(request, env) {
  if (!env.ADMIN_TOKEN) return false
  const h = request.headers.get("authorization") || ""
  if (!h.startsWith("Bearer ")) return false
  const [got, want] = await Promise.all([
    sha256hex(h),
    sha256hex(`Bearer ${env.ADMIN_TOKEN}`),
  ])
  let diff = 0
  for (let i = 0; i < want.length; i++) diff |= got.charCodeAt(i) ^ want.charCodeAt(i)
  return diff === 0
}

/** 北京时间日期串（统计口径与国内直觉一致） */
function bjDay(offsetDays = 0) {
  const d = new Date(Date.now() + 8 * 3600 * 1000 + offsetDays * 86400 * 1000)
  return d.toISOString().slice(0, 10)
}

function bjMinute() {
  const d = new Date(Date.now() + 8 * 3600 * 1000)
  return d.toISOString().slice(0, 16).replace(/[-T:]/g, "")
}

/**
 * 留言邮件通知（Brevo，免费 300 封/天；发件人需在 Brevo 后台验证站长邮箱一次）。
 * env.BREVO_API_KEY / env.OWNER_EMAIL 未配置 → 静默跳过（留言只入库）。
 * 任何失败只 console.error，绝不影响表单响应。
 */
async function notifyOwnerByEmail(env, name, email, text) {
  try {
    const key = env.BREVO_API_KEY
    const owner = env.OWNER_EMAIL
    if (!key || !owner) return
    // 每日帽只统计未读（2026-11-11 F1）：被刷爆后站长读完即恢复通知，不必等 24h
    const recent = await env.DB.prepare(
      "SELECT COUNT(*) n FROM messages WHERE read = 0 AND created_at > datetime('now', '-1 day')"
    ).first()
    if ((recent?.n ?? 0) > NOTIFY_DAILY_CAP) {
      console.error("notify skipped: daily cap exceeded")
      return
    }
    const res = await fetch("https://api.brevo.com/v3/smtp/email", {
      method: "POST",
      headers: { "api-key": key, "content-type": "application/json", accept: "application/json" },
      body: JSON.stringify({
        // 发件人 = 站长邮箱（Brevo 已验证的发件人）；回复直接送达访客邮箱
        sender: { email: owner, name: "rain collector" },
        to: [{ email: owner, name: "rain 站长" }],
        replyTo: { email, name },
        subject: `[rain 留言] ${name}`,
        textContent:
          `来自：${name} <${email}>\n时间：${bjDay()}（北京时间）\n\n${text}\n\n` +
          `— rain collector 自动通知；留言已存 D1，管理台「05 收件箱」可见`,
      }),
    })
    if (!res.ok) console.error("brevo notify failed:", res.status, await res.text().catch(() => ""))
  } catch (e) {
    console.error("notify error:", e)
  }
}

export default {
  async fetch(request, env, ctx) {
    if (request.method === "OPTIONS") return corsPreflight(request)
    const url = new URL(request.url)
    try {
      // 前台沿用 Spring 契约的 /api/ 前缀（src/main.js、ContactSection），
      // 管理台用根路径；入口统一剥前缀，两种形态完全等价。
      if (url.pathname.startsWith("/api/")) url.pathname = url.pathname.slice(4)

      // IP 哈希盐走 secret（wrangler secret put IP_SALT）；未配置时退回旧值保持历史数据可去重。
      // 2026-11-10 审计：原盐 "rain-salt" 公开在源码里，D1 数据一旦外泄，IPv4 全空间可离线爆破反推访客 IP。
      const salt = env.IP_SALT || "rain-salt"
      const ip = request.headers.get("cf-connecting-ip") || "0.0.0.0"
      const ipHash = await sha256hex(ip + "|" + salt)

      // ---- 前台埋点（公开）----
      if (request.method === "POST" && url.pathname === "/t") {
        const body = await request.json().catch(() => ({}))
        const path = String(body.path || "/").split("?")[0].slice(0, 200)
        const referrer = String(body.referrer || "").slice(0, 200) || null
        // 同 ip+path+分钟 由唯一索引去重；再按 ip+分钟 限 20 个不同 path，防伪造 header 刷库
        const recent = await env.DB.prepare(
          "SELECT COUNT(*) n FROM visits WHERE ip_hash = ? AND minute_bucket = ?"
        ).bind(ipHash, bjMinute()).first()
        if ((recent?.n ?? 0) >= 20) return json(request, { error: "too many requests" }, 429)
        await env.DB.prepare(
          "INSERT OR IGNORE INTO visits (ip_hash, path, minute_bucket, referrer) VALUES (?, ?, ?, ?)"
        ).bind(ipHash, path, bjMinute(), referrer).run()
        return new Response(null, { status: 204, headers: corsHeaders(request) })
      }

      // ---- 联系表单（公开，60s 限频）----
      if (request.method === "POST" && url.pathname === "/messages") {
        const body = await request.json().catch(() => ({}))
        const name = String(body.name || "").trim().slice(0, 80)
        const email = String(body.email || "").trim().slice(0, 200)
        const text = String(body.body || "").trim().slice(0, 4000)
        if (!name || !email || !text) return json(request, { error: "请填写完整" }, 400)
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return json(request, { error: "邮箱格式不正确" }, 400)
        const recent = await env.DB.prepare(
          "SELECT id FROM messages WHERE ip_hash = ? AND created_at > datetime('now', '-60 seconds') LIMIT 1"
        ).bind(ipHash).first()
        if (recent) return json(request, { error: "发送过于频繁，请稍后再试" }, 429)
        // 同 IP 每日上限（2026-11-10 审计补充）：IP 轮换每分钟 1 条仍可日刷 1440 条灌爆收件箱；10 条/天对真实访客绰绰有余
        const todayCount = await env.DB.prepare(
          "SELECT COUNT(*) n FROM messages WHERE ip_hash = ? AND created_at > datetime('now', '-1 day')"
        ).bind(ipHash).first()
        if ((todayCount?.n ?? 0) >= 10) {
          return json(request, { error: "今日提交已达上限，请明天再来或直接发邮件" }, 429)
        }
        // Turnstile 人机验证（灰度，2026-11-10 审计建议 #2）：配置 TURNSTILE_SECRET_KEY 才启用；
        // 未配置时此分支整段跳过，行为与原来完全一致。token 一次性，由 siteverify 服务端核销。
        if (env.TURNSTILE_SECRET_KEY) {
          const tsToken = String(body.turnstileToken || "")
          if (!tsToken) return json(request, { error: "人机验证未完成，请刷新后重试" }, 403)
          const verifyRes = await fetch("https://challenges.cloudflare.com/turnstile/v0/siteverify", {
            method: "POST",
            headers: { "content-type": "application/x-www-form-urlencoded" },
            body: new URLSearchParams({
              secret: env.TURNSTILE_SECRET_KEY,
              response: tsToken,
              remoteip: ip,
            }).toString(),
          })
          const verdict = await verifyRes.json().catch(() => ({}))
          if (!verdict.success) return json(request, { error: "人机验证未通过，请重试" }, 403)
        }
        await env.DB.prepare(
          "INSERT INTO messages (name, email, body, ip_hash) VALUES (?, ?, ?, ?)"
        ).bind(name, email, text, ipHash).run()
        // 邮件通知（异步，不阻塞响应；失败不影响入库与 200）
        ctx.waitUntil(notifyOwnerByEmail(env, name, email, text))
        return json(request, { ok: true })
      }

      // ---- 简历下载计数（公开 beacon）：前台点击下载时上报，同 ip+分钟去重 ----
      if (request.method === "POST" && url.pathname === "/resume/hit") {
        await env.DB.prepare(
          "INSERT OR IGNORE INTO downloads (ip_hash, minute_bucket) VALUES (?, ?)"
        ).bind(ipHash, bjMinute()).run()
        return new Response(null, { status: 204, headers: corsHeaders(request) })
      }

      // ---- 以下为管理台读接口（Bearer ADMIN_TOKEN + 失败限流）----
      if (url.pathname.startsWith("/stats") || url.pathname.startsWith("/messages")) {
        if (!(await authorized(request, env))) {
          const fails = recordAuthFail(ipHash)
          if (fails >= AUTH_FAIL_LIMIT) return json(request, { error: "too many attempts" }, 429)
          return json(request, { error: "unauthorized" }, 401)
        }
        authFails.delete(ipHash)
      }

      if (request.method === "GET" && url.pathname === "/stats/summary") {
        // verify-collector.yml 的端到端探针（/verify-actions 前缀）不计入真实统计
        const NOT_VERIFY = "path NOT LIKE '/verify-actions%'"
        // ?days=N 趋势范围（2026-11-11 F4，默认 14，钳 1..60）；响应带 daily+range，daily14 兼容旧 UI
        const daysParam = Number(url.searchParams.get("days"))
        const range = Number.isFinite(daysParam) && daysParam > 0 ? Math.min(60, Math.floor(daysParam)) : 14
        const today = bjDay()
        const pvToday = await env.DB.prepare(
          `SELECT COUNT(*) n FROM visits WHERE ${NOT_VERIFY} AND substr(minute_bucket, 1, 8) = ?`
        ).bind(today.replaceAll("-", "")).first()
        const pvTotal = await env.DB.prepare(`SELECT COUNT(*) n FROM visits WHERE ${NOT_VERIFY}`).first()
        const uvTotal = await env.DB.prepare(`SELECT COUNT(DISTINCT ip_hash) n FROM visits WHERE ${NOT_VERIFY}`).first()
        const daily = await env.DB.prepare(
          "SELECT substr(minute_bucket, 1, 8) d, COUNT(*) n FROM visits " +
          `WHERE ${NOT_VERIFY} AND minute_bucket >= ? GROUP BY d`
        ).bind(bjDay(-(range - 1)).replaceAll("-", "")).all()
        const top = await env.DB.prepare(
          `SELECT path, COUNT(*) n FROM visits WHERE ${NOT_VERIFY} GROUP BY path ORDER BY n DESC LIMIT 5`
        ).all()
        const refs = await env.DB.prepare(
          "SELECT referrer r, COUNT(*) n FROM visits " +
          "WHERE referrer IS NOT NULL AND referrer != '' GROUP BY referrer ORDER BY n DESC LIMIT 5"
        ).all()
        const resume = await env.DB.prepare("SELECT COUNT(*) n FROM downloads").first()
        const dailyMap = {}
        for (let i = -(range - 1); i <= 0; i++) dailyMap[bjDay(i)] = 0
        for (const row of daily.results || []) {
          const key = `${String(row.d).slice(0, 4)}-${String(row.d).slice(4, 6)}-${String(row.d).slice(6, 8)}`
          if (key in dailyMap) dailyMap[key] = row.n
        }
        // daily14 = 请求范围内的近 14 天子集（旧管理台只读这个键）
        const cutoff14 = bjDay(-13)
        const daily14 = Object.fromEntries(Object.entries(dailyMap).filter(([k]) => k >= cutoff14))
        return json(request, {
          pvToday: pvToday?.n ?? 0,
          pvTotal: pvTotal?.n ?? 0,
          uvTotal: uvTotal?.n ?? 0,
          resumeDownloads: resume?.n ?? 0,
          daily: dailyMap,
          range,
          daily14,
          topPaths: Object.fromEntries((top.results || []).map((r) => [r.path, r.n])),
          topReferrers: Object.fromEntries((refs.results || []).map((r) => [r.r, r.n])),
        })
      }

      if (request.method === "GET" && url.pathname === "/messages") {
        const r = await env.DB.prepare(
          // 收件箱不显示 CI 探针留言（verify-collector 每次运行都会插入一条）
          "SELECT id, name, email, body, read, archived, created_at FROM messages " +
          "WHERE deleted = 0 AND email != 'verify@actions.dev' ORDER BY id DESC LIMIT 100"
        ).all()
        return json(request, r.results || [])
      }

      // ---- 回收站（2026-11-11 F1：对齐本地 Spring 软删契约）----
      if (request.method === "GET" && url.pathname === "/messages/trash") {
        const r = await env.DB.prepare(
          "SELECT id, name, email, body, read, archived, created_at, deleted_at FROM messages " +
          "WHERE deleted = 1 AND email != 'verify@actions.dev' ORDER BY deleted_at DESC LIMIT 100"
        ).all()
        return json(request, r.results || [])
      }

      // DELETE /messages/trash 要在 :id 之前精确匹配（"trash" 非数字，正则本身也不会命中，先写更清晰）
      if (request.method === "DELETE" && url.pathname === "/messages/trash") {
        await env.DB.prepare("DELETE FROM messages WHERE deleted = 1").run()
        return json(request, { ok: true })
      }

      if (request.method === "DELETE" && url.pathname === "/messages/read-all") {
        return json(request, { error: "not found" }, 404) // 防御性 404（PUT 才是 read-all）
      }

      if (request.method === "DELETE" && url.pathname.startsWith("/messages/")) {
        // /messages/:id（软删）与 /messages/:id/purge（彻底删，仅回收站内）
        const m = url.pathname.match(/^\/messages\/(\d+)(?:\/(purge))?$/)
        if (!m) return json(request, { error: "not found" }, 404)
        if (m[2] === "purge") {
          const r = await env.DB.prepare("DELETE FROM messages WHERE id = ? AND deleted = 1")
            .bind(Number(m[1])).run()
          if (!r.meta.changes) return json(request, { error: "仅回收站内的留言可彻底删除" }, 400)
          return json(request, { ok: true })
        }
        const r = await env.DB.prepare(
          "UPDATE messages SET deleted = 1, deleted_at = datetime('now') WHERE id = ? AND deleted = 0"
        ).bind(Number(m[1])).run()
        if (!r.meta.changes) return json(request, { error: "留言不存在" }, 400)
        return json(request, { ok: true })
      }

      if (request.method === "POST" && url.pathname.startsWith("/messages/")) {
        // /messages/:id/restore（从回收站取回）
        const m = url.pathname.match(/^\/messages\/(\d+)\/restore$/)
        if (!m) return json(request, { error: "not found" }, 404)
        const r = await env.DB.prepare(
          "UPDATE messages SET deleted = 0, deleted_at = NULL WHERE id = ? AND deleted = 1"
        ).bind(Number(m[1])).run()
        if (!r.meta.changes) return json(request, { error: "留言不在回收站" }, 400)
        return json(request, { ok: true })
      }

      if (request.method === "PUT" && url.pathname === "/messages/read-all") {
        await env.DB.prepare("UPDATE messages SET read = 1 WHERE read = 0").run()
        return json(request, { ok: true })
      }

      if (request.method === "PUT" && url.pathname.startsWith("/messages/")) {
        // 兼容 /messages/:id、/messages/:id/read、/messages/:id/archive 三种形态
        const m = url.pathname.match(/^\/messages\/(\d+)(?:\/(read|archive))?$/)
        if (!m) return json(request, { error: "not found" }, 404)
        if (m[2] === "archive") {
          const body = await request.json().catch(() => ({}))
          const v = body.archived === 0 ? 0 : 1
          await env.DB.prepare("UPDATE messages SET archived = ? WHERE id = ?").bind(v, Number(m[1])).run()
        } else {
          await env.DB.prepare("UPDATE messages SET read = 1 WHERE id = ?").bind(Number(m[1])).run()
        }
        return json(request, { ok: true })
      }

      return json(request, { error: "not found" }, 404)
    } catch (e) {
      console.error("worker error:", e)
      return json(request, { error: "internal error" }, 500)
    }
  },
}
