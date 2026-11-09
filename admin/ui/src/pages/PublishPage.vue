<template>
  <div>
    <div class="head">
      <div>
        <div class="kicker">04 / PUBLISH</div>
        <h1>发布</h1>
      </div>
      <button class="primary" :disabled="publishing" @click="doPublish">
        {{ publishing ? '发布中…（写回 + 构建）' : '一键发布' }}
      </button>
    </div>

    <p v-if="msg" class="msg" :class="{ err: msgIsErr }">{{ msg }}</p>

    <section class="panel">
      <h2>当前状态</h2>
      <div class="stats" v-if="status">
        <div class="stat"><span class="v">{{ status.photos }}</span><span class="k">照片</span></div>
        <div class="stat"><span class="v">{{ status.projects }}</span><span class="k">项目</span></div>
        <div class="stat" v-if="status.lastPublish">
          <span class="v">{{ status.lastPublish.status }}</span>
          <span class="k">上次发布 · {{ shortTime(status.lastPublish.createdAt) }} · {{ (status.lastPublish.durationMs / 1000).toFixed(1) }}s</span>
        </div>
        <div class="stat" v-else><span class="v">—</span><span class="k">从未发布</span></div>
      </div>
      <p class="hint">
        发布将：写回 src/data/photos.js 与项目数据文件（原子替换）→ 触发 npm run build → 记录历史。
        公网地址：<a class="site-link" href="https://yuanyyyyyyyyy.github.io/rain/" target="_blank" rel="noopener">yuanyyyyyyyyy.github.io/rain</a>
        （部署完成后约 1 分钟更新）
      </p>
    </section>

    <section class="panel">
      <h2>发布历史</h2>
      <p v-if="!logs.length" class="hint">暂无发布记录。</p>
      <div v-for="l in logs" :key="l.id" class="log-row">
        <div class="log-head" @click="expanded = expanded === l.id ? null : l.id">
          <span class="t">{{ l.createdAt.replace('T', ' ').slice(0, 19) }}</span>
          <span class="badge" :class="l.status === 'SUCCESS' ? 'ok' : 'fail'">{{ l.status }}</span>
          <span class="dur">{{ (l.durationMs / 1000).toFixed(1) }}s · {{ l.triggeredBy }}</span>
          <span class="hash" v-if="l.commitHash">{{ l.commitHash }}</span>
          <span class="files" v-if="l.writtenFiles">{{ l.writtenFiles.split(', ').length }} 个文件</span>
          <span class="chev">{{ expanded === l.id ? '▲' : '▼' }}</span>
        </div>
        <pre v-if="expanded === l.id" class="log-body">{{ logText(l) }}</pre>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api.js'

const status = ref(null)
const logs = ref([])
const publishing = ref(false)
const msg = ref('')
const msgIsErr = ref(false)
const expanded = ref(null)

function say(text, isErr = false) {
  msg.value = text
  msgIsErr.value = isErr
}

const shortTime = (t) => (t || '').replace('T', ' ').slice(0, 16)

const logText = (l) => `${l.writtenFiles || '（无写回文件）'}\n\n${l.output || '（无日志）'}`

async function load() {
  status.value = await api.get('/api/publish/status')
  logs.value = await api.get('/api/publish/logs')
}

async function doPublish() {
  const n = status.value?.projects ?? 2
  if (!confirm(`发布将执行：\n\n1. 写回 photos.js + ${n} 个项目数据文件\n2. 触发前端构建\n3. 自动提交并推送到 GitHub（公网约 1 分钟后更新）\n\n继续？`)) return
  publishing.value = true
  try {
    const r = await api.post('/api/publish')
    say(`发布成功：写回 ${r.writtenFiles.length} 个文件，构建耗时 ${(r.durationMs / 1000).toFixed(1)}s`)
  } catch (e) {
    say(e.message, true)
  } finally {
    publishing.value = false
    await load()
  }
}

onMounted(load)
</script>

<style lang="scss" scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 20px;
  margin-bottom: 16px;
}
.kicker {
  font: 10px var(--font-mono);
  letter-spacing: 0.15em;
  color: var(--c-blue);
  margin-bottom: 10px;
}
h1 {
  font-size: 22px;
  font-weight: 600;
}
h2 {
  font: 11px var(--font-mono);
  letter-spacing: 0.14em;
  color: var(--c-ink-soft);
  margin: 0 0 14px;
}
.primary {
  border: 1px solid var(--c-blue);
  background: var(--c-blue);
  color: #fff;
  padding: 10px 22px;
  font: 10px var(--font-mono);
  letter-spacing: 0.12em;
  white-space: nowrap;
  cursor: pointer;

  &:hover:not(:disabled) {
    box-shadow: 4px 4px 0 var(--c-blue-shadow);
    transform: translateY(-2px);
  }
  &:disabled {
    opacity: 0.6;
    cursor: default;
  }
}
.msg {
  font: 11px var(--font-mono);
  color: var(--c-blue-mid);
  border-left: 2px solid var(--c-blue);
  padding: 6px 12px;
  margin: 0 0 14px;
  background: var(--c-blue-faint);

  &.err {
    color: var(--c-danger);
    border-left-color: var(--c-danger);
    background: #fbf1f1;
  }
}
.panel {
  border: 1px solid var(--c-line);
  padding: 16px 18px;
  margin-bottom: 16px;
}
.stats {
  display: flex;
  gap: 28px;
  flex-wrap: wrap;

  .stat {
    display: grid;
    gap: 4px;

    .v {
      font: 20px var(--font-mono);
      color: var(--c-blue);
      letter-spacing: 0.04em;
    }
    .k {
      font: 10px var(--font-mono);
      letter-spacing: 0.1em;
      color: var(--c-muted);
    }
  }
}
.hint {
  font: 11px var(--font-sans);
  color: var(--c-muted);
  margin: 12px 0 0;

  .site-link {
    color: var(--c-blue);
    text-decoration: underline;

    &:hover {
      text-decoration: none;
    }
  }
}
.log-row {
  border-bottom: 1px solid var(--c-line);

  &:last-child {
    border-bottom: none;
  }
}
.log-head {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 10px 2px;
  cursor: pointer;
  font: 11px var(--font-mono);
  letter-spacing: 0.04em;

  &:hover {
    color: var(--c-blue);
  }
  .t {
    color: var(--c-ink-soft);
  }
  .badge {
    padding: 2px 8px;
    font-size: 9px;
    letter-spacing: 0.1em;

    &.ok {
      background: var(--c-blue);
      color: #fff;
    }
    &.fail {
      background: var(--c-danger);
      color: #fff;
    }
  }
  .dur,
  .hash,
  .files {
    color: var(--c-muted);
  }
  .chev {
    margin-left: auto;
    color: var(--c-muted);
  }
}
.log-body {
  margin: 0 0 12px;
  padding: 10px 12px;
  border: 1px solid var(--c-line);
  background: var(--c-blue-faint);
  font: 10px/1.7 var(--font-mono);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow: auto;
}
</style>
