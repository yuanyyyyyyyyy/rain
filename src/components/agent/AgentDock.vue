<template>
  <Teleport to="body">
    <!-- 悬浮入口：右下角 FAB，Klein Blue 实底 + 硬投影 hover（仿 base.scss .btn primary） -->
    <button
      v-if="!open"
      class="dock-fab"
      type="button"
      :aria-label="`打开${config.name}`"
      @click="openDock"
    >
      {{ config.fabLabel }}
    </button>

    <!-- 对话抽屉：Teleport 到 body，z-index 40（低于全屏 Lightbox 100，两者可共存）；
         非 aria-modal 的非模态 dialog——桌面端保留页面可滚动可参照，仅 ≤760px 全屏时锁滚动 -->
    <div v-if="open" ref="dockRef" class="dock" role="dialog" :aria-label="config.name">
      <header class="dock-head">
        <div>
          <div class="kicker">{{ config.name }}</div>
          <div class="sub">AGENT · 站内项目档案驱动</div>
        </div>
        <button ref="closeRef" class="dock-close" type="button" aria-label="关闭" @click="close">×</button>
      </header>

      <div ref="listRef" class="dock-body">
        <div v-if="!messages.length" class="empty">
          <p class="greeting">{{ config.greeting }}</p>
          <div class="chips">
            <button v-for="s in config.suggestions" :key="s" class="chip" type="button" @click="send(s)">
              {{ s }}
            </button>
          </div>
        </div>

        <div v-for="(m, i) in messages" :key="i" class="msg" :class="[m.role, { error: m.error }]">
          <span class="who">{{ m.role === 'user' ? 'YOU' : 'AI' }}</span>
          <p class="bubble">{{ m.content }}<span v-if="m.streaming" class="caret" /></p>
        </div>
      </div>

      <footer class="dock-foot">
        <div class="input-row">
          <input
            ref="inputRef"
            v-model="input"
            :placeholder="config.inputPlaceholder"
            maxlength="1000"
            :disabled="sending"
            aria-label="对话输入"
            @keydown.enter.prevent="send()"
          />
          <button v-if="sending" class="send stop" type="button" aria-label="停止生成" @click="stop">■</button>
          <button v-else class="send" type="button" aria-label="发送" :disabled="!input.trim()" @click="send()">↗</button>
        </div>
        <p class="disclaimer">{{ config.disclaimer }}</p>
      </footer>
    </div>
  </Teleport>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { apiBase, profile } from '../../data/site.js'
import { agentConfig as config } from '../../data/agentConfig.js'

/**
 * AI 分身对话 dock（2026-11-15 P0）。仓库首个 SSE 客户端：POST + ReadableStream 解析
 * `data:` 帧（POST-SSE 无法用 EventSource）。网络层失败兜底文案与 ContactSection 同哲学：
 * 给出真实邮箱让访客自行联系，绝不因 AI 故障挡住联系路径。
 */

const open = ref(false)
const messages = ref([]) // { role: 'user'|'assistant', content, streaming?, error? }
const input = ref('')
const sending = ref(false)
const listRef = ref(null)
const inputRef = ref(null)
const closeRef = ref(null)
let controller = null
let lastFocus = null

// ---- 会话与访客标识 ----
// sessionId：sessionStorage（页内刷新会话延续，关页即弃，对应 Worker 的 L1 会话记忆）
// visitorId：localStorage 匿名 UUID（跨会话访客画像的钥匙；P0 服务端只存不用，P1 蒸馏注入）
const uuid = () =>
  crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`
let sessionId = sessionStorage.getItem('rain-agent-session')
if (!sessionId) {
  sessionId = uuid()
  sessionStorage.setItem('rain-agent-session', sessionId)
}
let visitorId = localStorage.getItem('rain-visitor-id')
if (!visitorId) {
  visitorId = uuid()
  localStorage.setItem('rain-visitor-id', visitorId)
}

// ---- 打开/关闭（焦点管理与 Esc：仿 PhotographyPage Lightbox 模式）----
const openDock = () => {
  lastFocus = document.activeElement
  open.value = true
  if (window.innerWidth <= 760) document.body.style.overflow = 'hidden' // 全屏形态才锁滚动
  nextTick(() => inputRef.value?.focus())
}
const close = () => {
  open.value = false
  document.body.style.overflow = ''
  lastFocus?.focus?.()
  lastFocus = null
}
const onKey = (e) => {
  if (open.value && e.key === 'Escape') close()
}
onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => {
  window.removeEventListener('keydown', onKey)
  document.body.style.overflow = ''
  controller?.abort()
})

const scrollBottom = () =>
  nextTick(() => {
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
watch(() => messages.value.length, scrollBottom)

function stop() {
  controller?.abort()
}

async function send(preset) {
  const content = String(preset ?? input.value).trim().slice(0, 1000)
  if (!content || sending.value) return
  input.value = ''
  sending.value = true
  controller = new AbortController()
  messages.value.push({ role: 'user', content })
  const reply = { role: 'assistant', content: '', streaming: true, error: false }
  messages.value.push(reply)

  try {
    const res = await fetch(`${apiBase}/api/agent/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionId, visitorId, message: content }),
      signal: controller.signal,
    })
    if (!res.ok || !res.body) {
      const data = await res.json().catch(() => ({}))
      reply.error = true
      reply.content = data.error || `请求失败（${res.status}），请稍后再试`
      return
    }
    // SSE 帧解析：服务端契约 data: {"delta"|"done"|"error"}（见 cloudflare/agent.js）
    const reader = res.body.getReader()
    const dec = new TextDecoder()
    let buf = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += dec.decode(value, { stream: true })
      let idx
      while ((idx = buf.indexOf('\n\n')) >= 0) {
        const frame = buf.slice(0, idx).trim()
        buf = buf.slice(idx + 2)
        if (!frame.startsWith('data:')) continue
        let evt
        try {
          evt = JSON.parse(frame.slice(5).trim())
        } catch {
          continue
        }
        if (evt.delta) {
          reply.content += evt.delta
          scrollBottom()
        } else if (evt.error) {
          reply.error = true
          reply.content = evt.error
        }
      }
    }
  } catch (e) {
    if (e?.name !== 'AbortError') {
      // 网络层失败（workers.dev 直连受限等）：给出真实联系路径，与 ContactSection 兜底一致
      reply.error = true
      reply.content = `${config.offlineHint}${profile.email}`
    }
    // 主动停止：保留已生成的部分内容
  } finally {
    reply.streaming = false
    sending.value = false
    controller = null
    scrollBottom()
  }
}
</script>

<style lang="scss" scoped>
.dock-fab {
  position: fixed;
  right: 22px;
  bottom: 22px;
  z-index: 30;
  width: 48px;
  height: 48px;
  border: 1px solid var(--c-blue);
  background: var(--c-blue);
  color: #fff;
  font: 12px var(--font-mono);
  letter-spacing: 0.08em;
  cursor: pointer;
  transition: 0.2s;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 4px 4px 0 var(--c-blue-shadow);
  }
}

.dock {
  position: fixed;
  right: 22px;
  bottom: 22px;
  z-index: 40;
  width: 380px;
  height: 560px;
  max-height: calc(100vh - 44px);
  display: flex;
  flex-direction: column;
  background: var(--c-bg);
  border: 1px solid var(--c-line);
  box-shadow: 8px 8px 0 var(--c-blue-shadow-soft);
}

.dock-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px;
  border-bottom: 1px solid var(--c-line);

  .kicker {
    font: 10px var(--font-mono);
    letter-spacing: 0.15em;
    color: var(--c-blue);
  }
  .sub {
    margin-top: 3px;
    font: 9px var(--font-mono);
    letter-spacing: 0.1em;
    color: var(--c-muted);
  }
}
.dock-close {
  width: 28px;
  height: 28px;
  border: 1px solid var(--c-line);
  background: var(--c-bg);
  color: var(--c-muted);
  font: 14px/1 var(--font-mono);
  cursor: pointer;
  transition: 0.2s;

  &:hover {
    border-color: var(--c-blue);
    color: var(--c-blue);
  }
}

.dock-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 14px;

  .empty {
    margin: auto 0;

    .greeting {
      font-size: 12.5px;
      line-height: 1.9;
      color: var(--c-ink-soft);
      border-left: 2px solid var(--c-blue);
      padding-left: 12px;
      margin-bottom: 16px;
    }
    .chips {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
    .chip {
      border: 1px solid var(--c-line);
      background: var(--c-bg);
      padding: 6px 9px;
      font: 10px var(--font-mono);
      letter-spacing: 0.05em;
      color: var(--c-ink-soft);
      cursor: pointer;
      transition: 0.2s;

      &:hover {
        border-color: var(--c-blue);
        color: var(--c-blue);
      }
    }
  }

  .msg {
    display: flex;
    flex-direction: column;
    align-items: flex-start;

    .who {
      font: 9px var(--font-mono);
      letter-spacing: 0.12em;
      color: var(--c-muted);
      margin-bottom: 4px;
    }
    .bubble {
      max-width: 88%;
      padding: 9px 11px;
      font: 12.5px/1.7 var(--font-sans);
      white-space: pre-wrap;
      word-break: break-word;
    }

    &.user {
      align-items: flex-end;

      .who {
        color: var(--c-blue);
      }
      .bubble {
        background: var(--c-blue);
        border: 1px solid var(--c-blue);
        color: #fff;
      }
    }
    &:not(.user) .bubble {
      background: var(--c-bg);
      border: 1px solid var(--c-line);
      color: var(--c-ink);
    }
    &.error .bubble {
      border-color: var(--c-danger);
      color: var(--c-danger);
      font-size: 11.5px;
    }
  }

  .caret {
    display: inline-block;
    width: 7px;
    height: 13px;
    margin-left: 2px;
    vertical-align: -2px;
    background: var(--c-blue);
    animation: blink 1s step-end infinite;
  }
}
@keyframes blink {
  50% {
    opacity: 0;
  }
}

.dock-foot {
  border-top: 1px solid var(--c-line);

  .input-row {
    display: flex;
    gap: 8px;
    padding: 10px 12px 6px;

    input {
      flex: 1;
      min-width: 0;
      border: 1px solid var(--c-line);
      background: var(--c-bg);
      padding: 10px 12px;
      font: 12.5px var(--font-sans);
      color: var(--c-ink);
      outline: none;
      transition: 0.2s;

      &:focus {
        border-color: var(--c-blue);
      }
      &:disabled {
        opacity: 0.6;
      }
    }
    .send {
      width: 40px;
      border: 1px solid var(--c-blue);
      background: var(--c-blue);
      color: #fff;
      font: 13px var(--font-mono);
      cursor: pointer;
      transition: 0.2s;

      &:disabled {
        opacity: 0.5;
        cursor: default;
      }
      &.stop {
        background: var(--c-bg);
        border-color: var(--c-danger);
        color: var(--c-danger);
      }
    }
  }
  .disclaimer {
    margin: 0 12px 10px;
    font: 9px var(--font-mono);
    letter-spacing: 0.05em;
    color: var(--c-muted);
  }
}

@media (max-width: 760px) {
  .dock-fab {
    right: 16px;
    bottom: 16px;
  }
  .dock {
    inset: 0;
    width: auto;
    height: auto;
    max-height: none;
    border-left: none;
    border-right: none;
    box-shadow: none;
  }
}
</style>
