<template>
  <div>
    <div class="kicker">05 / INBOX · 数据源：{{ source }}</div>
    <div class="head">
      <h1>收件箱</h1>
      <div class="ops">
        <input v-model="q" class="search" type="search" placeholder="搜姓名 / 邮箱 / 正文" spellcheck="false" />
        <button
          v-for="t in tabs"
          :key="t.key"
          class="tab"
          :class="{ on: filter === t.key }"
          @click="filter = t.key"
        >
          {{ t.label }}<span v-if="t.key === 'unread' && unreadCount" class="n">{{ unreadCount }}</span><span v-if="t.key === 'trash' && trashCount" class="n">{{ trashCount }}</span>
        </button>
        <button v-if="filter !== 'trash'" class="op" :disabled="!unreadCount || busy" @click="readAll">全部已读</button>
        <button v-else class="op danger" :disabled="!trashCount || busy" @click="emptyTrash">清空回收站</button>
      </div>
    </div>

    <div v-if="workerUnsupported" class="hint warn">
      当前 Worker 版本不支持删除/回收站（接口 404）。请部署最新 worker.js 并执行
      <code>npx wrangler d1 migrations apply rain-collector --remote</code>（0002）后重试。
    </div>

    <div v-if="!shown.length" class="hint">{{ emptyText }}</div>

    <div v-for="m in shown" :key="m.id" class="msg" :class="{ unread: !m.read, archived: m.archived }" @click="markRead(m)">
      <div class="msg-head">
        <span class="who">{{ m.name }}</span>
        <!-- 访客可控 email：仅严格邮箱格式才生成 mailto 链接，防止 javascript: 等伪 scheme 存储型 XSS（2026-11-10 审计） -->
        <a v-if="isSafeEmail(m.email)" class="mail" :href="`mailto:${m.email}`">{{ m.email }}</a>
        <span v-else class="mail">{{ m.email }}</span>
        <span class="t">{{ fmtTime(m.createdAt) }}</span>
        <span v-if="!m.read" class="dot">未读</span>
        <span v-if="m.archived && filter !== 'trash'" class="tag">已归档</span>
        <template v-if="filter !== 'trash'">
          <button class="arc" @click.stop="setArchived(m)">{{ m.archived ? '取消归档' : '归档' }}</button>
          <button class="arc danger" @click.stop="softDelete(m)">删除</button>
        </template>
        <template v-else>
          <button class="arc" @click.stop="restore(m)">恢复</button>
          <button class="arc danger" @click.stop="purge(m)">彻底删除</button>
        </template>
      </div>
      <p class="body">{{ m.body }}</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, getWorkerCfg, workerSend } from '../api.js'

const list = ref([])
const trash = ref([])
const trashCount = ref(0)
const source = ref('本地')
const filter = ref('all')
const busy = ref(false)
// load() 时快照当前数据源配置（localStorage 非响应式，供空态文案区分提示）
const workerCfg = ref({ base: '', token: '' })

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'unread', label: '未读' },
  { key: 'archived', label: '归档' },
  { key: 'trash', label: '回收站' },
]

const isWorker = computed(() => source.value.startsWith('Worker'))
// Worker 端删除接口 404 = 版本旧（未含 0002 migration / 新路由），提示部署而不是裸报错
const workerUnsupported = ref(false)
/** Worker 管理写操作统一走这里：404 时置 workerUnsupported 并给出部署指引 */
async function workerManage(path, method, body) {
  try {
    return await workerSend(path, method, body)
  } catch (e) {
    if (String(e.message || '').includes('404')) {
      workerUnsupported.value = true
      throw new Error('当前 Worker 版本不支持该操作：请部署最新 worker.js + 0002 migration')
    }
    throw e
  }
}
// 严格邮箱格式：Worker 端校验（^[^\s@]+@[^\s@]+\.[^\s@]+$）拦不住 javascript:alert(1)@x.y 这类伪 scheme，
// 这里用字符白名单兜底，不匹配就降级为纯文本展示
function isSafeEmail(email) {
  return /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/.test(email || '')
}
const unreadCount = computed(() => list.value.filter((m) => !m.read).length)
// 搜索（2026-11-11 F3）：姓名/邮箱/正文包含匹配，与 Tab 筛选叠加；列表 ≤100 条，纯内存过滤足够
const q = ref('')
const shown = computed(() => {
  let arr
  if (filter.value === 'trash') arr = trash.value
  else if (filter.value === 'unread') arr = list.value.filter((m) => !m.read && !m.archived)
  else if (filter.value === 'archived') arr = list.value.filter((m) => m.archived)
  else arr = list.value
  const k = q.value.trim().toLowerCase()
  if (!k) return arr
  return arr.filter((m) =>
    [m.name, m.email, m.body].some((x) => String(x || '').toLowerCase().includes(k)))
})
const emptyText = computed(() => {
  if (filter.value === 'trash') return '回收站是空的。删除的留言会先进回收站，可在此恢复或彻底删除。'
  return list.value.length
    ? '当前筛选下没有留言。'
    : workerCfg.value.base
      ? '暂无留言。公网访客提交联系表单后会出现在这里（收件箱当前读取 Worker/D1 数据源）。'
      : '暂无留言。当前读取本地后端；公网访客的留言在 Cloudflare D1 里——请在「06 / 设置 → 数据源」配置 Worker 地址与 ADMIN_TOKEN 后再查看。'
})
// Worker 返回 created_at（SQLite 格式），本地 Spring 返回 createdAt（ISO）→ 统一为 createdAt
function normalize(m) {
  return {
    ...m,
    read: !!m.read,
    archived: !!m.archived,
    deleted: !!m.deleted,
    createdAt: m.createdAt || m.created_at || '',
    deletedAt: m.deletedAt || m.deleted_at || '',
  }
}

const fmtTime = (t) => String(t).replace('T', ' ').slice(0, 16)

async function load() {
  workerCfg.value = getWorkerCfg()
  const { base } = workerCfg.value
  if (base) {
    try {
      const r = await workerSend('/messages', 'GET')
      list.value = r.map(normalize)
      source.value = 'Worker（公网）'
      // 回收站与本地源同能力（2026-11-11 F1）；旧版 Worker 无此接口 → 保持空并在操作时提示部署
      try {
        trash.value = (await workerSend('/messages/trash', 'GET')).map(normalize)
      } catch {
        trash.value = []
      }
      trashCount.value = trash.value.length
      return
    } catch {
      source.value = '本地（Worker 失败回退）'
    }
  }
  list.value = (await api.get('/api/messages')).map(normalize)
  trash.value = (await api.get('/api/messages/trash')).map(normalize)
  trashCount.value = (await api.get('/api/messages/trash-count')).trash || 0
}

// 软删：移入回收站（可恢复）。Worker/本地两数据源同能力（2026-11-11 F1）
async function softDelete(m) {
  try {
    if (isWorker.value) await workerManage(`/messages/${m.id}`, 'DELETE')
    else await api.delete(`/api/messages/${m.id}`)
    await load()
  } catch (e) {
    alert(e.message || '删除失败')
  }
}

// 恢复：从回收站取回
async function restore(m) {
  try {
    if (isWorker.value) await workerManage(`/messages/${m.id}/restore`, 'POST')
    else await api.post(`/api/messages/${m.id}/restore`)
    await load()
  } catch (e) {
    alert(e.message || '恢复失败')
  }
}

// 彻底删除：真正删库，不可恢复
async function purge(m) {
  if (!confirm('彻底删除该留言？此操作不可恢复。')) return
  try {
    if (isWorker.value) await workerManage(`/messages/${m.id}/purge`, 'DELETE')
    else await api.delete(`/api/messages/${m.id}/purge`)
    await load()
  } catch (e) {
    alert(e.message || '彻底删除失败')
  }
}

// 清空回收站：彻底删除全部已软删留言
async function emptyTrash() {
  if (!confirm('清空回收站？全部已删除留言将彻底移除，不可恢复。')) return
  try {
    if (isWorker.value) await workerManage('/messages/trash', 'DELETE')
    else await api.delete('/api/messages/trash')
    await load()
  } catch (e) {
    alert(e.message || '清空失败')
  }
}

async function markRead(m) {
  if (m.read) return
  const { base } = getWorkerCfg()
  if (base && source.value.startsWith('Worker')) {
    // 列表来自 Worker/D1：id 只对 D1 有意义，Worker 写失败绝不能回退写本地 MySQL
    // （两库 id 独立自增，回退会把本地无关留言误标已读/归档）
    await workerSend(`/messages/${m.id}/read`, 'PUT')
  } else {
    await api.put(`/api/messages/${m.id}/read`)
  }
  m.read = true
}

async function readAll() {
  busy.value = true
  try {
    const { base } = getWorkerCfg()
    if (base && source.value.startsWith('Worker')) {
      await workerSend('/messages/read-all', 'PUT')
    } else {
      await api.post('/api/messages/read-all')
    }
    list.value.forEach((m) => {
      if (!m.archived) m.read = true
    })
  } finally {
    busy.value = false
  }
}

async function setArchived(m) {
  const next = !m.archived
  const { base } = getWorkerCfg()
  if (base && source.value.startsWith('Worker')) {
    // 同 markRead：D1 id 不可写本地库
    await workerSend(`/messages/${m.id}/archive`, 'PUT', { archived: next ? 1 : 0 })
  } else {
    await api.put(`/api/messages/${m.id}/archive`, { archived: next })
  }
  m.archived = next
}

onMounted(load)
</script>

<style lang="scss" scoped>
.kicker {
  font: 10px var(--font-mono);
  letter-spacing: 0.15em;
  color: var(--c-blue);
  margin-bottom: 10px;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 18px;

  h1 {
    font-size: 22px;
    font-weight: 600;
  }
}
.ops {
  display: flex;
  gap: 8px;
  align-items: center;
}
.search {
  border: 1px solid var(--c-line);
  background: var(--c-bg);
  padding: 6px 10px;
  font: 11px var(--font-sans);
  width: 180px;
  outline: none;

  &:focus {
    border-color: var(--c-blue);
  }
}
.tab,
.op {
  border: 1px solid var(--c-line);
  background: var(--c-bg);
  padding: 6px 12px;
  font: 10px var(--font-mono);
  letter-spacing: 0.1em;
  color: var(--c-ink-soft);
  cursor: pointer;
  transition: 0.15s;

  &:hover {
    border-color: var(--c-blue);
    color: var(--c-blue);
  }
  &.on {
    border-color: var(--c-blue);
    color: var(--c-blue);
    background: var(--c-blue-soft);
  }
  &:disabled {
    opacity: 0.5;
    cursor: default;
  }
  .n {
    margin-left: 4px;
    color: var(--c-blue);
  }
  &.danger {
    border-color: var(--c-danger);
    color: var(--c-danger);

    &:hover {
      background: var(--c-danger);
      color: #fff;
    }
  }
}
.hint {
  font: 12px var(--font-sans);
  color: var(--c-muted);

  &.warn {
    border: 1px solid var(--c-danger);
    color: var(--c-danger);
    padding: 8px 12px;
    border-radius: 2px;
    background: var(--c-danger-faint, transparent);
  }
}
.msg {
  border: 1px solid var(--c-line);
  padding: 14px 16px;
  margin-bottom: 12px;
  cursor: default;

  &.unread {
    border-left: 3px solid var(--c-blue);
  }
  &.archived {
    opacity: 0.75;
  }
  .msg-head {
    display: flex;
    gap: 12px;
    align-items: center;
    font: 11px var(--font-mono);
    flex-wrap: wrap;

    .who {
      color: var(--c-ink);
      font-weight: 600;
    }
    .mail {
      color: var(--c-blue);
    }
    .t {
      color: var(--c-muted);
    }
    .dot {
      background: var(--c-blue);
      color: #fff;
      padding: 2px 7px;
      font-size: 9px;
      letter-spacing: 0.1em;
    }
    .tag {
      border: 1px solid var(--c-line-blue);
      color: var(--c-ink-soft);
      padding: 1px 7px;
      font-size: 9px;
      letter-spacing: 0.1em;
    }
    .arc {
      margin-left: auto;
      background: none;
      border: 1px solid var(--c-line);
      padding: 3px 9px;
      font: 9px var(--font-mono);
      letter-spacing: 0.1em;
      color: var(--c-muted);
      cursor: pointer;
      transition: 0.15s;

      &:hover {
        border-color: var(--c-blue);
        color: var(--c-blue);
      }
      &.danger {
        border-color: var(--c-danger);
        color: var(--c-danger);

        &:hover {
          background: var(--c-danger);
          color: #fff;
        }
      }
    }
  }
  .body {
    margin: 10px 0 0;
    font: 13px/1.8 var(--font-sans);
    color: var(--c-ink-soft);
    white-space: pre-wrap;
  }
}
</style>
