<template>
  <div>
    <div class="kicker">05 / INBOX · 数据源：{{ source }}</div>
    <div class="head">
      <h1>收件箱</h1>
      <div class="ops">
        <button
          v-for="t in tabs"
          :key="t.key"
          class="tab"
          :class="{ on: filter === t.key }"
          @click="filter = t.key"
        >
          {{ t.label }}<span v-if="t.key === 'unread' && unreadCount" class="n">{{ unreadCount }}</span>
        </button>
        <button class="op" :disabled="!unreadCount || busy" @click="readAll">全部已读</button>
      </div>
    </div>

    <div v-if="!shown.length" class="hint">{{ emptyText }}</div>

    <div v-for="m in shown" :key="m.id" class="msg" :class="{ unread: !m.read }" @click="markRead(m)">
      <div class="msg-head">
        <span class="who">{{ m.name }}</span>
        <a class="mail" :href="`mailto:${m.email}`">{{ m.email }}</a>
        <span class="t">{{ fmtTime(m.createdAt) }}</span>
        <span v-if="!m.read" class="dot">未读</span>
        <span v-if="m.archived" class="tag">已归档</span>
        <button class="arc" @click.stop="setArchived(m)">{{ m.archived ? '取消归档' : '归档' }}</button>
      </div>
      <p class="body">{{ m.body }}</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, getWorkerCfg, workerSend } from '../api.js'

const list = ref([])
const source = ref('本地')
const filter = ref('all')
const busy = ref(false)

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'unread', label: '未读' },
  { key: 'archived', label: '归档' },
]

const unreadCount = computed(() => list.value.filter((m) => !m.read).length)
const shown = computed(() => {
  if (filter.value === 'unread') return list.value.filter((m) => !m.read && !m.archived)
  if (filter.value === 'archived') return list.value.filter((m) => m.archived)
  return list.value
})
const emptyText = computed(() =>
  list.value.length ? '当前筛选下没有留言。' : '暂无留言。前台联系表单提交后会出现在这里。'
)

// Worker 返回 created_at（SQLite 格式），本地 Spring 返回 createdAt（ISO）→ 统一为 createdAt
function normalize(m) {
  return { ...m, read: !!m.read, archived: !!m.archived, createdAt: m.createdAt || m.created_at || '' }
}

const fmtTime = (t) => String(t).replace('T', ' ').slice(0, 16)

async function load() {
  const { base } = getWorkerCfg()
  if (base) {
    try {
      const r = await workerSend('/messages', 'GET')
      list.value = r.map(normalize)
      source.value = 'Worker（公网）'
      return
    } catch {
      source.value = '本地（Worker 失败回退）'
    }
  }
  list.value = (await api.get('/api/messages')).map(normalize)
}

async function markRead(m) {
  if (m.read) return
  const { base } = getWorkerCfg()
  if (base && source.value.startsWith('Worker')) {
    try {
      await workerSend(`/messages/${m.id}/read`, 'PUT')
    } catch {
      await api.put(`/api/messages/${m.id}/read`)
    }
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
      try {
        await workerSend('/messages/read-all', 'PUT')
      } catch {
        await api.post('/api/messages/read-all')
      }
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
    try {
      await workerSend(`/messages/${m.id}/archive`, 'PUT', { archived: next ? 1 : 0 })
    } catch {
      await api.put(`/api/messages/${m.id}/archive`, { archived: next })
    }
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
}
.hint {
  font: 12px var(--font-sans);
  color: var(--c-muted);
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
