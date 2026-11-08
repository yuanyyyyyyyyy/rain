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
}

export async function workerGet(path) {
  const { base, token } = getWorkerCfg()
  const res = await fetch(`${base}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!res.ok) throw new Error(`Worker ${res.status}`)
  return res.json()
}
