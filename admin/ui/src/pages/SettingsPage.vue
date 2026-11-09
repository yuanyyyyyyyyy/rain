<template>
  <div>
    <div class="kicker">06 / SETTINGS</div>
    <h1>设置</h1>

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
import { ref } from 'vue'
import { api } from '../api.js'

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
    msg.value = '已更新，下次登录使用新密码'
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
  max-width: 420px;
  border: 1px solid var(--c-line);
  padding: 18px 20px;
}
h2 {
  font: 11px var(--font-mono);
  letter-spacing: 0.14em;
  color: var(--c-ink-soft);
  margin: 0 0 14px;
}
form {
  display: grid;
  gap: 10px;
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
button {
  margin-top: 4px;
  padding: 11px;
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
}
.err {
  color: var(--c-danger);
  font-size: 12px;
}
.ok {
  color: var(--c-blue-mid, #4167c5);
  font-size: 12px;
}
.note {
  margin-top: 14px;
  font: 10px var(--font-mono);
  color: var(--c-muted);
}
</style>
