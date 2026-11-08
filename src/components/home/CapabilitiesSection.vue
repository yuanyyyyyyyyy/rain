<template>
  <section id="capabilities" class="section wrap">
    <div class="sec-head">
      <div>
        <div class="kicker">02 / CAPABILITIES</div>
        <h2>What I work with.</h2>
      </div>
      <div class="note">TOOLS ARE MEANS, NOT THE GOAL</div>
    </div>
    <div class="capabilities">
      <div v-for="c in capabilities" :key="c.num" class="cap">
        <div class="num">{{ c.num }}</div>
        <h3>{{ c.title }}</h3>
        <p>{{ c.desc }}</p>
      </div>
    </div>
  </section>
</template>

<script setup>
import { capabilities } from '../../data/site.js'
</script>

<style lang="scss" scoped>
.capabilities {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: var(--c-line);
  border: 1px solid var(--c-line);
}
.cap {
  background: #fff;
  padding: 24px;
  min-height: 150px;

  .num {
    font: 10px var(--font-mono);
    color: var(--c-blue);
  }
  h3 {
    font-size: 16px;
    margin: 23px 0 7px;
    font-weight: 600;
  }
  p {
    font-size: 11px;
    line-height: 1.8;
    color: var(--c-muted);
    white-space: pre-line;
  }
}
@media (max-width: 760px) {
  .capabilities {
    grid-template-columns: 1fr;
  }
}
</style>
