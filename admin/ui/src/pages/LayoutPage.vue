<template>
  <div class="layout">
    <aside class="side">
      <div class="brand">RAIN / ADMIN</div>
      <nav>
        <router-link to="/" exact-active-class="on">01 / 概览</router-link>
        <router-link to="/photos" active-class="on">02 / 照片管理</router-link>
        <router-link to="/content" active-class="on">03 / 项目内容</router-link>
        <router-link to="/publish" active-class="on">04 / 发布</router-link>
      </nav>
      <button class="out" @click="logout">退出登录</button>
    </aside>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { clearToken } from '../api.js'

const router = useRouter()
const logout = () => {
  clearToken()
  router.push('/login')
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
  .out {
    margin-top: auto;
    align-self: flex-start;
    margin-left: 18px;
    background: none;
    border: none;
    color: var(--c-side-muted);
    font: 10px var(--font-mono);
    letter-spacing: 0.1em;
    cursor: pointer;

    &:hover {
      color: #fff;
    }
  }
}
.main {
  flex: 1;
  min-width: 0;
  padding: 30px 36px;
}
</style>
