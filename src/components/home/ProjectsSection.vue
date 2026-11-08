<template>
  <section id="work" class="section wrap">
    <div class="sec-head">
      <div>
        <div class="kicker">01 / SELECTED WORKS</div>
        <h2>Projects &amp; Experiments.</h2>
      </div>
      <div class="note">A SELECTION OF BUILT THINGS / 2025—2026</div>
    </div>

    <div class="projects">
      <!-- 旗舰卡：云枢，横跨两栏 -->
      <RouterLink class="project flagship" :to="`/projects/${flagship.slug}`">
        <div class="ptop">
          <span>PROJECT / {{ flagship.no }}</span>
          <span>{{ flagship.period }}</span>
        </div>
        <div class="fbody">
          <div class="ftext">
            <h3>{{ flagship.title }}</h3>
            <p>{{ flagship.summary }}</p>
            <div class="tags">
              <span v-for="t in flagship.tags" :key="t" class="tag">{{ t }}</span>
            </div>
          </div>
          <!-- 旗舰卡装饰：与 Hero 主视觉同语言的几何线条 -->
          <svg class="fart" viewBox="0 0 260 200" fill="none" aria-hidden="true">
            <g stroke="#002FA7" stroke-width=".8" opacity=".6">
              <path d="M40 165 L110 40 L180 165 Z" />
              <path d="M110 40 V180 M40 165 H190" />
              <path d="M160 55 L225 120 L160 185 L95 120 Z" opacity=".5" />
            </g>
            <g stroke="#aab8d8" stroke-width=".6" stroke-dasharray="3 5">
              <path d="M15 105 H245" /><path d="M130 15 V190" />
            </g>
            <circle cx="160" cy="120" r="4" stroke="#002FA7" stroke-width="1.1" />
            <path d="M160 111 V129 M151 120 H169" stroke="#002FA7" stroke-width="1.1" />
          </svg>
        </div>
        <div class="fmetrics">
          <div v-for="m in flagship.metrics" :key="m.label" class="metric">
            <span class="mlabel">{{ m.label }}</span>
            <span class="mvalue">{{ m.value }}</span>
          </div>
          <span class="mnote">50 并发 / 2000 请求 · 测试环境数据</span>
        </div>
        <span class="arrow">↗</span>
      </RouterLink>

      <!-- 常规项目卡 -->
      <RouterLink
        v-for="p in regular"
        :key="p.slug"
        class="project"
        :to="`/projects/${p.slug}`"
      >
        <div class="ptop">
          <span>PROJECT / {{ p.no }}</span>
          <span>{{ p.period }}</span>
        </div>
        <h3>{{ p.title }}</h3>
        <p>{{ p.summary }}</p>
        <div class="tags">
          <span v-for="t in p.tags" :key="t" class="tag">{{ t }}</span>
        </div>
        <span class="arrow">↗</span>
      </RouterLink>

      <!-- Visual Notes：通往摄影页 -->
      <RouterLink class="project" to="/photography">
        <div class="ptop">
          <span>PERSONAL / 004</span>
          <span>ONGOING</span>
        </div>
        <h3>Visual Notes</h3>
        <p>一些关于光线、街道、游戏与日常观察的记录。技术之外，也保留一点自由探索的空间。</p>
        <div class="tags">
          <span class="tag">PHOTOGRAPHY</span>
          <span class="tag">OBSERVATION</span>
        </div>
        <span class="arrow">↗</span>
      </RouterLink>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { projects } from '../../data/site.js'

const flagship = computed(() => projects.find((p) => p.flagship))
const regular = computed(() => projects.filter((p) => !p.flagship))
</script>

<style lang="scss" scoped>
.projects {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 15px;
}
.project {
  min-height: 245px;
  border: 1px solid var(--c-line);
  padding: 24px;
  position: relative;
  background: var(--c-bg);
  transition: 0.22s;
  overflow: hidden;
  display: block;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    width: 42px;
    border-top: 2px solid var(--c-blue);
  }
  &:hover {
    border-color: var(--c-blue-hover);
    transform: translateY(-4px);
    box-shadow: 5px 5px 0 var(--c-blue-shadow-soft);
  }

  h3 {
    font-size: 21px;
    margin: 30px 0 9px;
    font-weight: 600;
  }
  p {
    font-size: 12px;
    color: var(--c-muted);
    line-height: 1.9;
    max-width: 450px;
  }
}
.ptop {
  display: flex;
  justify-content: space-between;
  color: var(--c-blue);
  font: 9px var(--font-mono);
  letter-spacing: 0.12em;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 20px;

  .tag {
    font: 9px var(--font-mono);
    color: #596985;
    border: 1px solid #d9e0ef;
    padding: 5px 8px;
    background: var(--c-blue-faint);
  }
}
.arrow {
  position: absolute;
  right: 24px;
  bottom: 22px;
  color: var(--c-blue);
  font-size: 20px;
}

// 旗舰卡
.flagship {
  grid-column: 1 / -1;
  min-height: 0;
}
.fbody {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 24px;
  align-items: center;
  margin-top: 6px;

  .ftext h3 {
    font-size: 26px;
  }
  .ftext p {
    font-size: 13px;
    max-width: none;
  }
}
.fart {
  width: 100%;
  max-width: 300px;
  justify-self: end;
  border-left: 1px solid var(--c-line);
  padding-left: 20px;
}
.fmetrics {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 18px;
  border-top: 1px solid var(--c-line);
  padding-top: 16px;

  .metric {
    background: var(--c-blue-soft);
    padding: 10px 14px;
    min-width: 130px;

    .mlabel {
      display: block;
      font: 9px var(--font-mono);
      letter-spacing: 0.12em;
      color: var(--c-muted);
    }
    .mvalue {
      font-size: 14px;
      font-weight: 600;
      color: var(--c-blue);
    }
  }
  .mnote {
    font: 9px var(--font-mono);
    letter-spacing: 0.08em;
    color: var(--c-muted);
    margin-left: auto;
  }
}

@media (max-width: 760px) {
  .projects {
    grid-template-columns: 1fr;
  }
  .fbody {
    grid-template-columns: 1fr;

    .fart {
      justify-self: start;
      border-left: none;
      padding-left: 0;
      max-width: 240px;
    }
  }
  .fmetrics .mnote {
    margin-left: 0;
  }
}
</style>
