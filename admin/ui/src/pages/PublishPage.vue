<template>
  <div>
    <div class="kicker">04 / PUBLISH</div>
    <h1>发布</h1>
    <p class="todo">M4 接入：写回数据文件 + 触发构建 + 发布历史。</p>
  </div>
</template>

<style scoped>
.kicker {
  font: 10px var(--font-mono);
  letter-spacing: 0.15em;
  color: var(--c-blue);
  margin-bottom: 12px;
}
h1 {
  font-size: 22px;
  font-weight: 600;
}
.todo {
  margin-top: 14px;
  border-left: 2px solid var(--c-blue);
  padding-left: 14px;
  font-size: 13px;
  color: var(--c-muted);
  line-height: 1.9;
}
</style>
