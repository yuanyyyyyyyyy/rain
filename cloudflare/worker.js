/**
 * rain collector — Cloudflare Worker（免费层）
 * 前台埋点 + 联系表单的公网接收端；管理台经 Bearer ADMIN_TOKEN 读统计与收件箱。
 * 存储：D1（绑定名 DB）。密钥：secret ADMIN_TOKEN（wrangler secret put ADMIN_TOKEN）。
 * 部署见同目录 README.md。
 */

const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET,POST,PUT,OPTIONS",
  "access-control-allow-headers": "content-type,authorization",
}

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

/** 管理台读接口鉴权 */
function authorized(request, env) {
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

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return corsPreflight()
    const url = new URL(request.url)
    try {
      const ip = request.headers.get("cf-connecting-ip") || "0.0.0.0"
      const ipHash = await sha256hex(ip + "|rain-salt")

      // ---- 前台埋点（公开）----
      if (request.method === "POST" && url.pathname === "/t") {
        const body = await request.json().catch(() => ({}))
        const path = String(body.path || "/").split("?")[0].slice(0, 200)
        await env.DB.prepare(
          "INSERT OR IGNORE INTO visits (ip_hash, path, minute_bucket) VALUES (?, ?, ?)"
        ).bind(ipHash, path, bjMinute()).run()
        return new Response(null, { status: 204, headers: JSON_HEADERS })
      }

      // ---- 联系表单（公开，60s 限频）----
      if (request.method === "POST" && url.pathname === "/messages") {
        const body = await request.json().catch(() => ({}))
        const name = String(body.name || "").trim().slice(0, 80)
        const email = String(body.email || "").trim().slice(0, 200)
        const text = String(body.body || "").trim().slice(0, 4000)
        if (!name || !email || !text) return json({ error: "请填写完整" }, 400)
        const recent = await env.DB.prepare(
          "SELECT id FROM messages WHERE ip_hash = ? AND created_at > datetime('now', '-60 seconds') LIMIT 1"
        ).bind(ipHash).first()
        if (recent) return json({ error: "发送过于频繁，请稍后再试" }, 429)
        await env.DB.prepare(
          "INSERT INTO messages (name, email, body, ip_hash) VALUES (?, ?, ?, ?)"
        ).bind(name, email, text, ipHash).run()
        return json({ ok: true })
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
          daily14: dailyMap,
          topPaths: Object.fromEntries((top.results || []).map((r) => [r.path, r.n])),
        })
      }

      if (request.method === "GET" && url.pathname === "/messages") {
        const r = await env.DB.prepare(
          "SELECT id, name, email, body, read, created_at FROM messages ORDER BY id DESC LIMIT 100"
        ).all()
        return json(r.results || [])
      }

      if (request.method === "PUT" && url.pathname.startsWith("/messages/")) {
        const id = Number(url.pathname.split("/")[3])
        await env.DB.prepare("UPDATE messages SET read = 1 WHERE id = ?").bind(id).run()
        return json({ ok: true })
      }

      return json({ error: "not found" }, 404)
    } catch (e) {
      return json({ error: String(e && e.message || e) }, 500)
    }
  },
}
