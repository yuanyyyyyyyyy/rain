<template>
  <div>
    <div class="kicker">05 / INBOX · 数据源：{{ source }}</div>
    <h1>收件箱</h1>

    <div v-if="!list.length" class="hint">暂无留言。前台联系表单提交后会出现在这里。</div>

    <div v-for="m in list" :key="m.id" class="msg" :class="{ unread: !m.read }" @click="markRead(m)">
      <div class="msg-head">
        <span class="who">{{ m.name }}</span>
        <a class="mail" :href="`mailto:${m.email}`">{{ m.email }}</a>
        <span class="t">{{ m.createdAt.replace('T', ' ').slice(0, 16) }}</span>
        <span v-if="!m.read" class="dot">未读</span>
      </div>
      <p class="body">{{ m.body }}</p>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api, getWorkerCfg, workerGet } from '../api.js'

const list = ref([])
const source = ref('本地')

async function load() {
  const { base } = getWorkerCfg()
  if (base) {
    try {
      const r = await workerGet('/messages')
      // Worker 字段与本地 Spring 对齐（read: 0/1 → boolean 统一）
      list.value = r.map((m) => ({ ...m, read: !!m.read }))
      source.value = 'Worker（公网）'
      return
    } catch {
      source.value = '本地（Worker 失败回退）'
    }
  }
  list.value = await api.get('/api/messages')
}

async function markRead(m) {
  if (m.read) return
  const { base } = getWorkerCfg()
  if (base && source.value.startsWith('Worker')) {
    await workerGet(`/messages/${m.id}/read`).catch(() => api.put(`/api/messages/${m.id}/read`))
  } else {
    await api.put(`/api/messages/${m.id}/read`)
  }
  m.read = true
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
h1 {
  font-size: 22px;
  font-weight: 600;
  margin-bottom: 18px;
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
  }
  .body {
    margin: 10px 0 0;
    font: 13px/1.8 var(--font-sans);
    color: var(--c-ink-soft);
    white-space: pre-wrap;
  }
}
</style>
