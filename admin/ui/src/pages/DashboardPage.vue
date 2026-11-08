<template>
  <div>
    <div class="kicker">01 / DASHBOARD · 数据源：{{ source }}</div>
    <h1>概览</h1>

    <div class="cards">
      <div class="card"><span class="v">{{ s.pvToday ?? '—' }}</span><span class="k">今日 PV</span></div>
      <div class="card"><span class="v">{{ s.pvTotal ?? '—' }}</span><span class="k">累计 PV</span></div>
      <div class="card"><span class="v">{{ s.uvTotal ?? '—' }}</span><span class="k">访客数（IP 去重）</span></div>
      <div class="card"><span class="v">{{ photos ?? '—' }}</span><span class="k">照片</span></div>
      <div class="card"><span class="v">{{ projects ?? '—' }}</span><span class="k">项目</span></div>
    </div>

    <section class="panel">
      <h2>近 14 天访问趋势</h2>
      <div class="chart" v-if="days.length">
        <div v-for="d in days" :key="d.day" class="bar-col" :title="`${d.day} · ${d.pv} PV`">
          <div class="bar" :style="{ height: barH(d.pv) + 'px' }"></div>
          <span class="lbl">{{ d.day.slice(5) }}</span>
        </div>
      </div>
      <p v-else class="hint">暂无数据（前台埋点在访客浏览后产生记录）。</p>
    </section>

    <section class="panel">
      <h2>热门页面 Top 5</h2>
      <p v-if="!top.length" class="hint">暂无数据。</p>
      <div v-for="t in top" :key="t.path" class="top-row">
        <span class="path">{{ t.path }}</span>
        <span class="pv">{{ t.pv }} PV</span>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, getWorkerCfg, workerGet } from '../api.js'

const s = ref({})
const photos = ref(null)
const projects = ref(null)
const source = ref('本地')

const days = computed(() =>
  Object.entries(s.value.daily14 || {}).map(([day, pv]) => ({ day, pv })))
const top = computed(() =>
  Object.entries(s.value.topPaths || {}).map(([path, pv]) => ({ path, pv })))

const barH = (pv) => {
  const max = Math.max(...days.value.map((d) => d.pv), 1)
  return Math.max(4, Math.round((pv / max) * 120))
}

onMounted(async () => {
  // 数据源：配置了 Worker（公网 collector）优先，失败回退本地 Spring
  const { base } = getWorkerCfg()
  if (base) {
    try {
      s.value = await workerGet('/stats/summary')
      source.value = 'Worker（公网）'
    } catch {
      s.value = await api.get('/api/stats/summary')
    }
  } else {
    s.value = await api.get('/api/stats/summary')
  }
  const st = await api.get('/api/publish/status')
  photos.value = st.photos
  projects.value = st.projects
})
</script>

<style lang="scss" scoped>
.kicker {
  font: 10px var(--font-mono);
  letter-spacing: 0.15em;
  color: var(--c-blue);
  margin-bottom: 10px;
}
h1 {
  font-size: 22px;
  font-weight: 600;
  margin-bottom: 16px;
}
.cards {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.card {
  border: 1px solid var(--c-line);
  padding: 14px 18px;
  display: grid;
  gap: 4px;
  min-width: 120px;

  .v {
    font: 20px var(--font-mono);
    color: var(--c-blue);
  }
  .k {
    font: 10px var(--font-mono);
    letter-spacing: 0.1em;
    color: var(--c-muted);
  }
}
.panel {
  border: 1px solid var(--c-line);
  padding: 16px 18px;
  margin-bottom: 16px;
}
h2 {
  font: 11px var(--font-mono);
  letter-spacing: 0.14em;
  color: var(--c-ink-soft);
  margin: 0 0 14px;
}
.chart {
  display: flex;
  gap: 6px;
  align-items: flex-end;
  height: 150px;
}
.bar-col {
  flex: 1;
  display: grid;
  grid-template-rows: 1fr auto;
  justify-items: center;
  align-items: end;
  height: 100%;

  .bar {
    width: 100%;
    max-width: 36px;
    background: var(--c-blue);
    opacity: 0.85;
  }
  .lbl {
    font: 8px var(--font-mono);
    color: var(--c-muted);
    margin-top: 4px;
    white-space: nowrap;
  }
}
.hint {
  font: 11px var(--font-sans);
  color: var(--c-muted);
}
.top-row {
  display: flex;
  justify-content: space-between;
  padding: 7px 2px;
  border-bottom: 1px solid var(--c-line);
  font: 11px var(--font-mono);

  &:last-child {
    border-bottom: none;
  }
  .path {
    color: var(--c-ink-soft);
  }
  .pv {
    color: var(--c-blue);
  }
}
</style>
