// 统一 fetch 封装：自动带 JWT，401 跳登录
const TOKEN_KEY = 'rain_admin_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}
export function setToken(t) {
  localStorage.setItem(TOKEN_KEY, t)
}
export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

async function request(method, url, body) {
  const headers = {}
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  const res = await fetch(url, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (res.status === 401) {
    clearToken()
    location.hash = '#/login'
    throw new Error('登录已过期')
  }
  const data = await res.json().catch(() => ({}))
  if (!res.ok) throw new Error(data.error || `请求失败（${res.status}）`)
  return data
}

// multipart 上传：不设 Content-Type（浏览器自动带 boundary），401 处理与 request 一致
async function requestForm(method, url, formData) {
  const headers = {}
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(url, { method, headers, body: formData })
  if (res.status === 401) {
    clearToken()
    location.hash = '#/login'
    throw new Error('登录已过期')
  }
  const data = await res.json().catch(() => ({}))
  if (!res.ok) throw new Error(data.error || `请求失败（${res.status}）`)
  return data
}

// text 响应（如导出 JS 全文）：401 处理一致，成功返回 text
async function requestText(method, url) {
  const headers = {}
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(url, { method, headers })
  if (res.status === 401) {
    clearToken()
    location.hash = '#/login'
    throw new Error('登录已过期')
  }
  if (!res.ok) {
    const data = await res.json().catch(() => ({}))
    throw new Error(data.error || `请求失败（${res.status}）`)
  }
  return res.text()
}

export const api = {
  get: (url) => request('GET', url),
  post: (url, body) => request('POST', url, body),
  put: (url, body) => request('PUT', url, body),
  delete: (url) => request('DELETE', url),
  postForm: (url, formData) => requestForm('POST', url, formData),
  getText: (url) => requestText('GET', url),
}

// ---- Worker 数据源（Cloudflare collector：公网统计/收件箱）----
// 侧栏「数据源」配置 base + ADMIN_TOKEN 存 localStorage；未配置或请求失败时页面回退本地 Spring 后端
const WORKER_BASE_KEY = 'rain_worker_base'
const WORKER_TOKEN_KEY = 'rain_worker_token'

export function getWorkerCfg() {
  return {
    base: localStorage.getItem(WORKER_BASE_KEY) || '',
    token: localStorage.getItem(WORKER_TOKEN_KEY) || '',
  }
}

export function setWorkerCfg(base, token) {
  localStorage.setItem(WORKER_BASE_KEY, base.replace(/\/$/, ''))
  localStorage.setItem(WORKER_TOKEN_KEY, token)
  // localStorage 非响应式：广播变更，让 LayoutPage 的状态徽标/未读数即时刷新（免手动刷新页面）
  window.dispatchEvent(new CustomEvent('rain-worker-cfg'))
}

/** 测试 Worker 连通性（用尚未保存的输入值也行）：区分 地址错/需代理、token 错、成功三种结果 */
export async function testWorkerConnection(base, token) {
  const b = (base || '').trim().replace(/\/$/, '')
  if (!b) return { ok: false, kind: 'empty', message: 'Worker 地址为空' }
  if (!/^https:\/\/.+/.test(b)) {
    return { ok: false, kind: 'format', message: '地址格式不对：应以 https:// 开头（形如 https://rain-collector.<子域>.workers.dev）' }
  }
  let res
  try {
    res = await fetch(`${b}/stats/summary`, {
      headers: { Authorization: `Bearer ${token || ''}` },
      signal: AbortSignal.timeout(8000),
    })
  } catch {
    return {
      ok: false,
      kind: 'network',
      message: '连接失败：地址不可达，或需代理（国内直连 workers.dev 常受限，8 秒超时）',
    }
  }
  if (res.status === 401) {
    return { ok: false, kind: 'auth', message: '鉴权失败（401）：ADMIN_TOKEN 不正确，或 Worker 端没有配置该 token' }
  }
  if (!res.ok) return { ok: false, kind: 'http', message: `请求失败（HTTP ${res.status}）` }
  const d = await res.json().catch(() => ({}))
  return { ok: true, kind: 'ok', message: `连接成功：累计 PV ${d.pvTotal ?? '—'} · 访客 ${d.uvTotal ?? '—'} · 简历下载 ${d.resumeDownloads ?? '—'}` }
}

export async function workerSend(path, method = 'GET', body) {
  const { base, token } = getWorkerCfg()
  const headers = { Authorization: `Bearer ${token}` }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const res = await fetch(`${base}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
    // workers.dev 国内直连可能受限：8 秒未响应即回退本地后端，不让页面干等
    signal: AbortSignal.timeout(8000),
  })
  if (!res.ok) throw new Error(`Worker ${res.status}`)
  return res.json()
}

export function workerGet(path) {
  return workerSend(path, 'GET')
}
