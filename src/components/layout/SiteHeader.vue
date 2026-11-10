<template>
  <header class="site-header">
    <div class="wrap nav">
      <RouterLink class="brand" to="/">
        <span class="mark">Y</span>
        <span>YUAN / PERSONAL ARCHIVE</span>
      </RouterLink>
      <button class="menu-btn" :aria-expanded="open" :aria-label="open ? '关闭菜单' : '打开菜单'" @click="open = !open" @keydown.esc="open = false">
        <span></span><span></span>
      </button>
      <nav :class="{ open }" @keydown.esc="open = false">
        <RouterLink to="/#work" @click="open = false">WORK</RouterLink>
        <RouterLink to="/#capabilities" @click="open = false">CAPABILITIES</RouterLink>
        <RouterLink to="/about" @click="open = false">ABOUT</RouterLink>
        <RouterLink to="/photography" @click="open = false">PHOTOGRAPHY</RouterLink>
        <RouterLink class="contact" to="/#contact" @click="open = false">CONTACT ↗</RouterLink>
      </nav>
    </div>
  </header>
</template>

<script setup>
import { ref } from 'vue'
const open = ref(false)
</script>

<style lang="scss" scoped>
.site-header {
  height: 76px;
  border-bottom: 1px solid var(--c-line);
  background: var(--c-bg);
  position: sticky;
  top: 0;
  z-index: 20;
}
.nav {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  font: 500 10px var(--font-mono);
  letter-spacing: 0.14em;
}
.mark {
  width: 28px;
  height: 28px;
  border: 1px solid var(--c-blue);
  color: var(--c-blue);
  display: grid;
  place-items: center;
  position: relative;
  &::after {
    content: '';
    position: absolute;
    width: 6px;
    height: 6px;
    right: -3px;
    top: -3px;
    background: var(--c-blue);
  }
}
nav {
  display: flex;
  align-items: center;
  gap: 30px;
  color: #666d75;
  font: 10px var(--font-mono);
  letter-spacing: 0.12em;

  a {
    padding: 8px 0;
    position: relative;
    &::after {
      content: '';
      position: absolute;
      left: 0;
      right: 100%;
      bottom: 2px;
      height: 1px;
      background: var(--c-blue);
      transition: 0.25s;
    }
    &:hover::after {
      right: 0;
    }
  }
  .contact {
    border: 1px solid var(--c-blue);
    padding: 10px 14px;
    color: var(--c-blue);
    &::after {
      display: none;
    }
  }
}
.menu-btn {
  display: none;
}

@media (max-width: 760px) {
  .site-header {
    height: 65px;
  }
  .menu-btn {
    display: grid;
    gap: 5px;
    background: none;
    border: none;
    padding: 8px;
    cursor: pointer;
    span {
      display: block;
      width: 20px;
      height: 1.5px;
      background: var(--c-ink);
    }
  }
  nav {
    display: none;
    position: absolute;
    top: 65px;
    left: 0;
    right: 0;
    background: var(--c-bg);
    border-bottom: 1px solid var(--c-line);
    flex-direction: column;
    align-items: flex-start;
    padding: 18px 18px 22px;
    gap: 16px;

    &.open {
      display: flex;
    }
    .contact {
      padding: 10px 14px;
    }
  }
}
</style>
