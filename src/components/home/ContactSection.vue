<template>
  <section id="contact" class="section wrap">
    <div class="sec-head">
      <div>
        <div class="kicker">04 / CONTACT</div>
        <h2>Let's build something.</h2>
      </div>
      <div class="note">OPEN TO INTERESTING OPPORTUNITIES</div>
    </div>
    <p class="lead">期待与团队交流技术、产品和真实的问题。</p>
    <div class="actions">
      <a class="btn primary" :href="`mailto:${profile.email}`">EMAIL ME <b>↗</b></a>
      <a v-if="profile.github" class="btn" :href="profile.github" target="_blank" rel="noopener">GITHUB <b>↗</b></a>
    </div>
  </section>
</template>

<script setup>
import { profile } from '../../data/site.js'
</script>

<style lang="scss" scoped>
.lead {
  font-size: 13px;
  color: var(--c-muted);
  line-height: 2;
}
.actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
  flex-wrap: wrap;
}
</style>
