<template>
  <div class="wrap page notfound">
    <div class="kicker">404 / NOT FOUND</div>
    <h1>页面不存在。</h1>
    <p class="desc">你要找的页面可能已被移动、删除，或者从未存在过。</p>
    <RouterLink class="btn" to="/">返回首页 <span>↩</span></RouterLink>
  </div>
</template>

<script setup>
// 404 兜底：vue-router catch-all 指向本页，避免未知路径渲染空白 main
</script>

<style lang="scss" scoped>
.notfound {
  padding-top: 96px;
  padding-bottom: 120px;

  h1 {
    font-size: 42px;
    font-weight: 600;
    letter-spacing: -0.04em;
    margin: 10px 0 14px;
  }
  .desc {
    color: var(--c-ink-soft);
    margin-bottom: 34px;
  }
}
</style>
