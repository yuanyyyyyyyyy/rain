<template>
  <div class="login">
    <div class="box">
      <div class="kicker">RAIN / ADMIN</div>
      <h1>Content Console</h1>
      <form @submit.prevent="submit">
        <input v-model="username" placeholder="用户名" autocomplete="username" />
        <input v-model="password" type="password" placeholder="密码" autocomplete="current-password" />
        <p v-if="err" class="err">{{ err }}</p>
        <button type="submit" :disabled="loading">{{ loading ? '验证中…' : '登录' }}</button>
      </form>
      <p class="hint">默认账号 admin / admin123（首次启动自动创建）</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, setToken } from '../api.js'

const router = useRouter()
const username = ref('')
const password = ref('')
const err = ref('')
const loading = ref(false)

async function submit() {
  err.value = ''
  loading.value = true
  try {
    const data = await api.post('/api/auth/login', {
      username: username.value,
      password: password.value,
    })
    setToken(data.token)
    router.push('/')
  } catch (e) {
    err.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.login {
  min-height: 100vh;
  display: grid;
  place-items: center;
}
.box {
  width: 360px;
  border: 1px solid var(--c-line);
  padding: 38px 34px;

  .kicker {
    font: 10px var(--font-mono);
    letter-spacing: 0.15em;
    color: var(--c-blue);
    margin-bottom: 10px;
  }
  h1 {
    font-size: 24px;
    font-weight: 600;
    letter-spacing: -0.03em;
    margin-bottom: 26px;
  }
}
form {
  display: grid;
  gap: 12px;
}
input {
  border: 1px solid var(--c-line);
  padding: 11px 13px;
  font: 13px var(--font-sans);
  outline: none;
  transition: 0.2s;

  &:focus {
    border-color: var(--c-blue);
  }
}
button {
  margin-top: 6px;
  padding: 12px;
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
  color: #a32d2d;
  font-size: 12px;
}
.hint {
  margin-top: 20px;
  font: 10px var(--font-mono);
  letter-spacing: 0.08em;
  color: var(--c-muted);
}
</style>
