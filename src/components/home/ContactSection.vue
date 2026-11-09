<template>
  <section id="contact" class="section wrap">
    <div class="sec-head">
      <div>
        <div class="kicker">04 / CONTACT</div>
        <h2>Let's build something.</h2>
      </div>
      <div class="note">OPEN TO INTERESTING OPPORTUNITIES</div>
    </div>
    <p class="lead">期待与团队交流技术、产品和真实的问题。</p>

    <form class="form" @submit.prevent="submit">
      <div class="row">
        <input v-model="name" placeholder="怎么称呼你" aria-label="怎么称呼你" maxlength="80" required />
        <input v-model="email" type="email" placeholder="你的邮箱" aria-label="你的邮箱" maxlength="200" required />
      </div>
      <textarea v-model="body" rows="4" placeholder="想聊点什么（招聘合作 / 技术交流都可以）" aria-label="留言内容" maxlength="4000" required></textarea>
      <div class="form-foot">
        <button class="btn primary" type="submit" :disabled="sending">
          {{ sending ? '发送中…' : 'SEND MESSAGE' }} <b v-if="!sending">↗</b>
        </button>
        <span v-if="done" class="ok" role="status">已收到，感谢留言！</span>
        <span v-else-if="err" class="err" role="status">{{ err }}</span>
      </div>
    </form>

    <div class="actions">
      <!-- 邮箱交互：点击复制（mailto: 会唤起系统邮件客户端如 Outlook，体验差，弃用） -->
      <button class="btn copy-btn" type="button" @click="copyEmail">
        {{ copied ? 'COPIED ✓' : 'EMAIL ME' }} <b v-if="!copied">⧉</b>
      </button>
      <span class="addr" aria-label="邮箱地址">{{ profile.email }}</span>
      <a v-if="profile.github" class="btn" :href="profile.github" target="_blank" rel="noopener">GITHUB <b>↗</b></a>
    </div>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { profile, apiBase } from '../../data/site.js'

const name = ref('')
const email = ref('')
const body = ref('')
const sending = ref(false)
const done = ref(false)
const err = ref('')
const copied = ref(false)
let copyTimer = null

// 点击复制邮箱：clipboard API 优先，非 HTTPS/旧浏览器回退 execCommand
async function copyEmail() {
  const text = profile.email
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    copied.value = true
    clearTimeout(copyTimer)
    copyTimer = setTimeout(() => (copied.value = false), 2000)
  } catch {
    // 复制失败静默——邮箱地址本身就在按钮旁边可见
  }
}

async function submit() {
  err.value = ''
  sending.value = true
  try {
    let res
    try {
      res = await fetch(`${apiBase}/api/messages`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: name.value, email: email.value, body: body.value }),
      })
    } catch {
      // 网络层失败（静态托管上无后端）→ 降级为邮件联系
      err.value = `在线表单暂不可用，请直接邮件联系：${profile.email}`
      return
    }
    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      err.value = data.error || `发送失败（${res.status}）`
      return
    }
    done.value = true
    name.value = email.value = body.value = ''
  } finally {
    sending.value = false
  }
}
</script>

<style lang="scss" scoped>
.lead {
  font-size: 13px;
  color: var(--c-muted);
  line-height: 2;
}
.form {
  max-width: 640px;
  margin-top: 26px;
  display: grid;
  gap: 12px;

  .row {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;

    @media (max-width: 640px) {
      grid-template-columns: 1fr;
    }
  }
  input,
  textarea {
    border: 1px solid var(--c-line);
    background: var(--c-bg);
    padding: 12px 14px;
    font: 13px/1.6 var(--font-sans);
    color: var(--c-ink);
    outline: none;
    transition: 0.2s;
    resize: vertical;

    &:focus {
      border-color: var(--c-blue);
    }
  }
  .form-foot {
    display: flex;
    gap: 14px;
    align-items: center;

    .ok {
      font: 11px var(--font-mono);
      color: var(--c-blue-mid);
    }
    .err {
      font: 11px var(--font-mono);
      color: var(--c-danger);
    }
  }
  button:disabled {
    opacity: 0.6;
    cursor: default;
  }
}
.actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
  flex-wrap: wrap;
  align-items: center;
}
.copy-btn {
  cursor: pointer;
  font-family: inherit;
}
.addr {
  font: 11px var(--font-mono);
  letter-spacing: 0.08em;
  color: var(--c-muted);
  user-select: all; // 整段选中方便手动复制
}
</style>
