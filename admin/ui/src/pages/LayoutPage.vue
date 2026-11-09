<template>
  <div class="layout">
    <aside class="side">
      <div class="brand">RAIN / ADMIN</div>
      <nav>
        <router-link to="/" exact-active-class="on">01 / 概览</router-link>
        <router-link to="/photos" active-class="on">02 / 照片管理</router-link>
        <router-link to="/content" active-class="on">03 / 项目内容</router-link>
        <router-link to="/publish" active-class="on">04 / 发布</router-link>
        <router-link to="/inbox" active-class="on">
          05 / 收件箱
          <span v-if="unread > 0" class="badge">{{ unread > 99 ? '99+' : unread }}</span>
        </router-link>
        <router-link to="/settings" active-class="on">06 / 设置</router-link>
      </nav>
      <button class="src" @click="configSource">数据源</button>
      <button class="out" @click="logout">退出登录</button>
    </aside>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, clearToken, getWorkerCfg, setWorkerCfg } from '../api.js'

const router = useRouter()
const unread = ref(0)

const logout = () => {
  clearToken()
  router.push('/login')
}

const loadUnread = async () => {
  try {
    const r = await api.get('/api/messages/unread')
    unread.value = r.unread
  } catch {
    unread.value = 0
  }
}

onMounted(loadUnread)

// 公网数据源（Cloudflare collector）：配置后概览/收件箱优先读 Worker，失败回退本地
const configSource = () => {
  const cur = getWorkerCfg()
  const base = prompt('Worker 地址（留空清除，回退本地后端）：', cur.base)
  if (base === null) return
  if (!base) {
    setWorkerCfg('', '')
    alert('已清除，概览/收件箱将读本地后端。刷新页面生效。')
    return
  }
  const token = prompt('ADMIN_TOKEN：', cur.token)
  if (token === null) return
  setWorkerCfg(base, token)
  alert('已保存。刷新页面生效。')
}
</script>

<style lang="scss" scoped>
.layout {
  display: flex;
  min-height: 100vh;
}
.side {
  width: 190px;
  flex-shrink: 0;
  background: var(--c-blue);
  display: flex;
  flex-direction: column;
  padding: 22px 0;

  .brand {
    font: 11px var(--font-mono);
    letter-spacing: 0.14em;
    color: #fff;
    padding: 0 18px;
    margin-bottom: 24px;
  }
  nav {
    display: grid;
  }
  nav a {
    padding: 10px 18px;
    font-size: 12.5px;
    color: var(--c-side-muted);
    border-left: 2px solid transparent;
    transition: 0.15s;

    &:hover {
      color: #fff;
    }
    &.on {
      color: #fff;
      background: rgba(255, 255, 255, 0.14);
      border-left-color: #fff;
    }
  }
  .badge {
    background: #fff;
    color: var(--c-blue);
    font-size: 9px;
    padding: 1px 6px;
    border-radius: 8px;
    margin-left: 6px;
    vertical-align: 1px;
  }
  .out,
  .src {
    align-self: flex-start;
    margin-left: 18px;
    background: none;
    border: none;
    color: #b5c7f2;
    font: 10px var(--font-mono);
    letter-spacing: 0.1em;
    cursor: pointer;

    &:hover {
      color: #fff;
    }
  }
  .src {
    margin-top: auto;
  }
  .out {
    margin-top: 10px;
    margin-bottom: 8px;
  }
}
.main {
  flex: 1;
  min-width: 0;
  padding: 30px 36px;
}
</style>
