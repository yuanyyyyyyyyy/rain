<template>
  <footer class="wrap site-footer">
    <span>© 2026 YUAN / PERSONAL ARCHIVE</span>
    <span>DESIGNED WITH CURIOSITY · BUILT WITH CODE</span>
  </footer>
</template>

<style lang="scss" scoped>
.site-footer {
  padding: 25px 0 35px;
  border-top: 1px solid var(--c-line);
  display: flex;
  justify-content: space-between;
  color: var(--c-muted);
  font: 9px var(--font-mono);
  letter-spacing: 0.1em;
}
@media (max-width: 760px) {
  .site-footer {
    flex-direction: column;
    gap: 12px;
  }
}
</style>
