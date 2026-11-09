/**
 * rain collector — Cloudflare Worker（免费层）
 * 前台埋点 + 联系表单 + 简历下载计数的公网接收端；管理台经 Bearer ADMIN_TOKEN 读统计与收件箱。
 * 存储：D1（绑定名 DB）。密钥：secret ADMIN_TOKEN（wrangler secret put ADMIN_TOKEN）。
 * 留言通知：secret BREVO_API_KEY + var OWNER_EMAIL（未配置则只入库不发信，任务 D）。
 * 部署见同目录 README.md。
 */

const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET,POST,PUT,OPTIONS",
  "access-control-allow-headers": "content-type,authorization",
}

/** 每日转发上限：防止刷表单烧穿 Brevo 免费配额（300 封/天，留余量） */
const NOTIFY_DAILY_CAP = 20

function json(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS })
}

function corsPreflight() {
  return new Response(null, { status: 204, headers: JSON_HEADERS })
}

async function sha256hex(s) {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s))
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("")
}

/** 管理台读接口鉴权；未配置 ADMIN_TOKEN 时一律拒绝（防止字面量 "Bearer undefined" 命中） */
function authorized(request, env) {
  if (!env.ADMIN_TOKEN) return false
  const h = request.headers.get("authorization") || ""
  return h === `Bearer ${env.ADMIN_TOKEN}`
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
    const recent = await env.DB.prepare(
      "SELECT COUNT(*) n FROM messages WHERE created_at > datetime('now', '-1 day')"
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
    if (request.method === "OPTIONS") return corsPreflight()
    const url = new URL(request.url)
    try {
      // 前台沿用 Spring 契约的 /api/ 前缀（src/main.js、ContactSection），
      // 管理台用根路径；入口统一剥前缀，两种形态完全等价。
      if (url.pathname.startsWith("/api/")) url.pathname = url.pathname.slice(4)

      const ip = request.headers.get("cf-connecting-ip") || "0.0.0.0"
      const ipHash = await sha256hex(ip + "|rain-salt")

      // ---- 前台埋点（公开）----
      if (request.method === "POST" && url.pathname === "/t") {
        const body = await request.json().catch(() => ({}))
        const path = String(body.path || "/").split("?")[0].slice(0, 200)
        const referrer = String(body.referrer || "").slice(0, 200) || null
        // 同 ip+path+分钟 由唯一索引去重；再按 ip+分钟 限 20 个不同 path，防伪造 header 刷库
        const recent = await env.DB.prepare(
          "SELECT COUNT(*) n FROM visits WHERE ip_hash = ? AND minute_bucket = ?"
        ).bind(ipHash, bjMinute()).first()
        if ((recent?.n ?? 0) >= 20) return json({ error: "too many requests" }, 429)
        await env.DB.prepare(
          "INSERT OR IGNORE INTO visits (ip_hash, path, minute_bucket, referrer) VALUES (?, ?, ?, ?)"
        ).bind(ipHash, path, bjMinute(), referrer).run()
        return new Response(null, { status: 204, headers: JSON_HEADERS })
      }

      // ---- 联系表单（公开，60s 限频）----
      if (request.method === "POST" && url.pathname === "/messages") {
        const body = await request.json().catch(() => ({}))
        const name = String(body.name || "").trim().slice(0, 80)
        const email = String(body.email || "").trim().slice(0, 200)
        const text = String(body.body || "").trim().slice(0, 4000)
        if (!name || !email || !text) return json({ error: "请填写完整" }, 400)
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return json({ error: "邮箱格式不正确" }, 400)
        const recent = await env.DB.prepare(
          "SELECT id FROM messages WHERE ip_hash = ? AND created_at > datetime('now', '-60 seconds') LIMIT 1"
        ).bind(ipHash).first()
        if (recent) return json({ error: "发送过于频繁，请稍后再试" }, 429)
        await env.DB.prepare(
          "INSERT INTO messages (name, email, body, ip_hash) VALUES (?, ?, ?, ?)"
        ).bind(name, email, text, ipHash).run()
        // 邮件通知（异步，不阻塞响应；失败不影响入库与 200）
        ctx.waitUntil(notifyOwnerByEmail(env, name, email, text))
        return json({ ok: true })
      }

      // ---- 简历下载计数（公开 beacon）：前台点击下载时上报，同 ip+分钟去重 ----
      if (request.method === "POST" && url.pathname === "/resume/hit") {
        await env.DB.prepare(
          "INSERT OR IGNORE INTO downloads (ip_hash, minute_bucket) VALUES (?, ?)"
        ).bind(ipHash, bjMinute()).run()
        return new Response(null, { status: 204, headers: JSON_HEADERS })
      }

      // ---- 以下为管理台读接口（Bearer ADMIN_TOKEN）----
      if (url.pathname.startsWith("/stats") || url.pathname.startsWith("/messages")) {
        if (!authorized(request, env)) return json({ error: "unauthorized" }, 401)
      }

      if (request.method === "GET" && url.pathname === "/stats/summary") {
        const today = bjDay()
        const pvToday = await env.DB.prepare(
          "SELECT COUNT(*) n FROM visits WHERE substr(minute_bucket, 1, 8) = ?"
        ).bind(today.replaceAll("-", "")).first()
        const pvTotal = await env.DB.prepare("SELECT COUNT(*) n FROM visits").first()
        const uvTotal = await env.DB.prepare("SELECT COUNT(DISTINCT ip_hash) n FROM visits").first()
        const daily = await env.DB.prepare(
          "SELECT substr(minute_bucket, 1, 8) d, COUNT(*) n FROM visits " +
          "WHERE minute_bucket >= ? GROUP BY d"
        ).bind(bjDay(-13).replaceAll("-", "")).all()
        const top = await env.DB.prepare(
          "SELECT path, COUNT(*) n FROM visits GROUP BY path ORDER BY n DESC LIMIT 5"
        ).all()
        const refs = await env.DB.prepare(
          "SELECT referrer r, COUNT(*) n FROM visits " +
          "WHERE referrer IS NOT NULL AND referrer != '' GROUP BY referrer ORDER BY n DESC LIMIT 5"
        ).all()
        const resume = await env.DB.prepare("SELECT COUNT(*) n FROM downloads").first()
        const dailyMap = {}
        for (let i = -13; i <= 0; i++) dailyMap[bjDay(i)] = 0
        for (const row of daily.results || []) {
          const key = `${String(row.d).slice(0, 4)}-${String(row.d).slice(4, 6)}-${String(row.d).slice(6, 8)}`
          if (key in dailyMap) dailyMap[key] = row.n
        }
        return json({
          pvToday: pvToday?.n ?? 0,
          pvTotal: pvTotal?.n ?? 0,
          uvTotal: uvTotal?.n ?? 0,
          resumeDownloads: resume?.n ?? 0,
          daily14: dailyMap,
          topPaths: Object.fromEntries((top.results || []).map((r) => [r.path, r.n])),
          topReferrers: Object.fromEntries((refs.results || []).map((r) => [r.r, r.n])),
        })
      }

      if (request.method === "GET" && url.pathname === "/messages") {
        const r = await env.DB.prepare(
          "SELECT id, name, email, body, read, archived, created_at FROM messages ORDER BY id DESC LIMIT 100"
        ).all()
        return json(r.results || [])
      }

      if (request.method === "PUT" && url.pathname === "/messages/read-all") {
        await env.DB.prepare("UPDATE messages SET read = 1 WHERE read = 0").run()
        return json({ ok: true })
      }

      if (request.method === "PUT" && url.pathname.startsWith("/messages/")) {
        // 兼容 /messages/:id、/messages/:id/read、/messages/:id/archive 三种形态
        const m = url.pathname.match(/^\/messages\/(\d+)(?:\/(read|archive))?$/)
        if (!m) return json({ error: "not found" }, 404)
        if (m[2] === "archive") {
          const body = await request.json().catch(() => ({}))
          const v = body.archived === 0 ? 0 : 1
          await env.DB.prepare("UPDATE messages SET archived = ? WHERE id = ?").bind(v, Number(m[1])).run()
        } else {
          await env.DB.prepare("UPDATE messages SET read = 1 WHERE id = ?").bind(Number(m[1])).run()
        }
        return json({ ok: true })
      }

      return json({ error: "not found" }, 404)
    } catch (e) {
      console.error("worker error:", e)
      return json({ error: "internal error" }, 500)
    }
  },
}
