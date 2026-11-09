<template>
  <div class="layout">
    <aside class="side">
      <div class="brand">RAIN / ADMIN</div>
      <nav>
        <router-link to="/" exact-active-class="on">01 / 概览</router-link>
        <router-link to="/photos" active-class="on">02 / 照片管理</router-link>
        <router-link to="/content" active-class="on">03 / 项目内容</router-link>
        <router-link to="/publish" active-class="on">
          04 / 发布
          <span v-if="publishDirty" class="dot" title="有未发布变更，点「04 发布」更新公网">●</span>
        </router-link>
        <router-link to="/inbox" active-class="on">
          05 / 收件箱
          <span v-if="unread > 0" class="badge">{{ unread > 99 ? '99+' : unread }}</span>
        </router-link>
        <router-link to="/settings" active-class="on">06 / 设置</router-link>
      </nav>
      <router-link to="/settings" class="src">
        数据源：<b :class="{ pub: workerCfg.base }">{{ workerCfg.base ? '公网' : '本地' }}</b>
      </router-link>
      <button class="out" @click="logout">退出登录</button>
    </aside>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, clearToken, getWorkerCfg, workerSend } from '../api.js'

const router = useRouter()
const route = useRoute()
const unread = ref(0)
const publishDirty = ref(false)
// 当前数据源配置快照（localStorage 非响应式：靠 rain-worker-cfg 事件与路由切换时刷新）
const workerCfg = ref({ base: '', token: '' })

const logout = () => {
  clearToken()
  router.push('/login')
}

function refreshCfg() {
  workerCfg.value = getWorkerCfg()
}

/** 未读数随数据源走：配了 Worker 读公网 D1，否则读本地 Spring（此前恒读本地，公网留言徽标恒 0） */
async function loadUnread() {
  const { base, token } = getWorkerCfg()
  if (base && token) {
    try {
      const list = await workerSend('/messages')
      unread.value = list.filter((m) => !m.read && !m.archived).length
      return
    } catch {
      // Worker 失败回退本地
    }
  }
  try {
    const r = await api.get('/api/messages/unread')
    unread.value = r.unread
  } catch {
    unread.value = 0
  }
}

/** 未发布变更角标（任务 C）：照片/内容落库但没点发布时提醒 */
async function loadDirty() {
  try {
    publishDirty.value = (await api.get('/api/publish/dirty')).dirty
  } catch {
    publishDirty.value = false
  }
}

onMounted(() => {
  refreshCfg()
  loadUnread()
  loadDirty()
  window.addEventListener('rain-worker-cfg', onCfgChange)
})
onUnmounted(() => window.removeEventListener('rain-worker-cfg', onCfgChange))

function onCfgChange() {
  refreshCfg()
  loadUnread()
}

// 路由切换时刷新（收件箱读信后角标即时更新；发布后红点消失）
watch(() => route.fullPath, () => {
  loadUnread()
  loadDirty()
})
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
  .dot {
    color: #ffd34d;
    font-size: 10px;
    margin-left: 5px;
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
    text-decoration: none;

    &:hover {
      color: #fff;
    }
  }
  .src {
    margin-top: auto;

    b {
      font-weight: 400;

      &.pub {
        color: #9effd6;
      }
    }
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
