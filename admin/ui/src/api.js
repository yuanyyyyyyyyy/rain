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

export const api = {
  get: (url) => request('GET', url),
  post: (url, body) => request('POST', url, body),
  put: (url, body) => request('PUT', url, body),
  delete: (url) => request('DELETE', url),
  postForm: (url, formData) => requestForm('POST', url, formData),
}
