<template>
  <div>
    <div class="kicker">06 / SETTINGS</div>
    <h1>设置</h1>

    <!-- 数据源 -->
    <section class="panel wide">
      <h2>数据源 · 概览与收件箱读哪里</h2>
      <p class="explain">
        本站公网部署在 GitHub Pages（纯静态，没有后端）。真实访客的<b>访问统计、留言、简历下载</b>
        都由部署在 Cloudflare 的 Worker 收集并存进 D1 数据库；本地 Spring 后端只能收到你本机联调的数据（通常为 0）。
        配置 Worker 地址后，概览与收件箱读取<b>真实公网数据</b>；留空保存则回退本地后端（本地联调模式）。
      </p>
      <div class="fields">
        <label>
          <span>Worker 地址</span>
          <input v-model="workerBase" type="text" placeholder="https://rain-collector.<子域>.workers.dev" spellcheck="false" />
        </label>
        <label>
          <span>ADMIN_TOKEN</span>
          <input v-model="workerToken" type="password" placeholder="部署 Worker 时设置的管理台读接口密钥" autocomplete="off" />
        </label>
      </div>
      <p class="explain dim">
        地址 = 部署 Worker 后记下的 workers.dev 域名（见 cloudflare/README.md；本站为
        <code>https://rain-collector.yuanyyyyyyyyy.workers.dev</code>）。
        ADMIN_TOKEN = 当时 <code>wrangler secret put ADMIN_TOKEN</code> 生成的那串长随机码（本机留档在
        <code>cloudflare/.admin-token.local</code>）。它与本页下方「登录密码」是两套独立凭证。
      </p>
      <div class="ops">
        <button class="ghost" :disabled="testing" @click="test">{{ testing ? '测试中…' : '测试连接' }}</button>
        <button @click="save" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button>
        <button v-if="workerCfg.base" class="ghost danger" @click="clearCfg">清除（回退本地）</button>
      </div>
      <p v-if="testMsg" :class="testOk ? 'ok' : 'err'">{{ testMsg }}</p>
      <p v-if="saveMsg" :class="saveOk ? 'ok' : 'err'">{{ saveMsg }}</p>
      <p class="note">当前生效：{{ workerCfg.base ? `公网（${workerCfg.base}）` : '本地后端' }}。
        保存后概览/收件箱在下次进入页面时自动使用新数据源，无需刷新浏览器。</p>
    </section>

    <!-- 账号安全 -->
    <section class="panel">
      <h2>修改密码</h2>
      <form @submit.prevent="submit">
        <input v-model="oldPwd" type="password" placeholder="原密码" autocomplete="current-password" />
        <input v-model="newPwd" type="password" placeholder="新密码（至少 8 位）" autocomplete="new-password" />
        <input v-model="newPwd2" type="password" placeholder="确认新密码" autocomplete="new-password" />
        <p v-if="msg" :class="ok ? 'ok' : 'err'">{{ msg }}</p>
        <button type="submit" :disabled="loading">{{ loading ? '提交中…' : '更新密码' }}</button>
      </form>
      <p class="note">密码用于登录本管理台（Spring 后端 /api/auth）。</p>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api, clearToken, getWorkerCfg, setWorkerCfg, testWorkerConnection } from '../api.js'

// ---- 数据源 ----
const workerCfg = ref({ base: '', token: '' })
const workerBase = ref('')
const workerToken = ref('')
const testing = ref(false)
const saving = ref(false)
const testMsg = ref('')
const testOk = ref(false)
const saveMsg = ref('')
const saveOk = ref(false)

onMounted(() => {
  workerCfg.value = getWorkerCfg()
  workerBase.value = workerCfg.value.base
  workerToken.value = workerCfg.value.token
})

async function test() {
  testMsg.value = ''
  testOk.value = false
  testing.value = true
  try {
    const r = await testWorkerConnection(workerBase.value, workerToken.value)
    testOk.value = r.ok
    testMsg.value = r.message
  } finally {
    testing.value = false
  }
}

async function save() {
  saveMsg.value = ''
  saveOk.value = false
  const base = workerBase.value.trim().replace(/\/$/, '')
  if (base && !/^https:\/\/.+/.test(base)) {
    saveMsg.value = '地址格式不对：应以 https:// 开头'
    return
  }
  if (base && !workerToken.value.trim()) {
    saveMsg.value = '配置了 Worker 地址就需要 ADMIN_TOKEN（Worker 端所有读接口都要求鉴权）'
    return
  }
  saving.value = true
  try {
    setWorkerCfg(base, workerToken.value.trim())
    workerCfg.value = getWorkerCfg()
    saveOk.value = true
    saveMsg.value = base ? '已保存。概览/收件箱将读取公网数据源。' : '已清除。概览/收件箱将回退本地后端（本地联调数据，通常为 0）。'
  } finally {
    saving.value = false
  }
}

function clearCfg() {
  workerBase.value = ''
  workerToken.value = ''
  save()
}

// ---- 修改密码 ----
const oldPwd = ref('')
const newPwd = ref('')
const newPwd2 = ref('')
const msg = ref('')
const ok = ref(false)
const loading = ref(false)

async function submit() {
  msg.value = ''
  if (newPwd.value.length < 8) {
    ok.value = false
    msg.value = '新密码至少 8 位'
    return
  }
  if (newPwd.value !== newPwd2.value) {
    ok.value = false
    msg.value = '两次输入的新密码不一致'
    return
  }
  loading.value = true
  try {
    await api.put('/api/auth/password', { oldPassword: oldPwd.value, newPassword: newPwd.value })
    ok.value = true
    // 改密后旧 JWT 并未吊销（最长还能用 120 分钟），立即清 token 强制重新登录，缩短旧凭据可用窗口
    clearToken()
    msg.value = '密码已更新，请用新密码重新登录…'
    setTimeout(() => { location.hash = '#/login' }, 1200)
    oldPwd.value = newPwd.value = newPwd2.value = ''
  } catch (e) {
    ok.value = false
    msg.value = e.message
  } finally {
    loading.value = false
  }
}
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
.panel {
  border: 1px solid var(--c-line);
  padding: 18px 20px;
  margin-bottom: 16px;
  max-width: 460px;

  &.wide {
    max-width: 640px;
  }
}
h2 {
  font: 11px var(--font-mono);
  letter-spacing: 0.14em;
  color: var(--c-ink-soft);
  margin: 0 0 14px;
}
.explain {
  font: 12px/1.9 var(--font-sans);
  color: var(--c-ink-soft);
  margin: 0 0 12px;

  b {
    color: var(--c-ink);
  }
  &.dim {
    font-size: 11px;
    color: var(--c-muted);
    margin-bottom: 14px;

    code {
      font: 10px var(--font-mono);
      background: var(--c-bg-soft);
      padding: 1px 4px;
    }
  }
}
.fields {
  display: grid;
  gap: 10px;
  margin-bottom: 12px;

  label {
    display: grid;
    gap: 4px;

    span {
      font: 10px var(--font-mono);
      letter-spacing: 0.1em;
      color: var(--c-muted);
    }
  }
}
input {
  border: 1px solid var(--c-line);
  padding: 10px 12px;
  font: 13px var(--font-sans);
  outline: none;
  transition: 0.2s;

  &:focus {
    border-color: var(--c-blue);
  }
}
.ops {
  display: flex;
  gap: 10px;
}
button {
  margin-top: 4px;
  padding: 10px 16px;
  background: var(--c-blue);
  color: #fff;
  border: none;
  font: 11px var(--font-mono);
  letter-spacing: 0.14em;
  cursor: pointer;
  transition: 0.2s;

  &:hover:not(:disabled) {
    box-shadow: 4px 4px 0 var(--c-blue-shadow);
    transform: translateY(-2px);
  }
  &:disabled {
    opacity: 0.6;
    cursor: default;
  }
  &.ghost {
    background: var(--c-bg);
    color: var(--c-ink-soft);
    border: 1px solid var(--c-line);

    &:hover:not(:disabled) {
      border-color: var(--c-blue);
      color: var(--c-blue);
      box-shadow: none;
      transform: none;
    }
    &.danger:hover:not(:disabled) {
      border-color: var(--c-danger);
      color: var(--c-danger);
    }
  }
  form & {
    width: 100%;
  }
}
form {
  display: grid;
  gap: 10px;
}
.err {
  color: var(--c-danger);
  font-size: 12px;
  margin: 8px 0 0;
}
.ok {
  color: var(--c-blue-mid, #4167c5);
  font-size: 12px;
  margin: 8px 0 0;
}
.note {
  margin-top: 14px;
  font: 10px/1.8 var(--font-mono);
  color: var(--c-muted);
}
</style>
