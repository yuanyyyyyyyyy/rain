<template>
  <article v-if="detail" class="wrap detail">
    <div class="crumb">
      <RouterLink to="/#work">← 返回 WORK</RouterLink>
      <span>PROJECT / {{ project.no }} — {{ project.title }}</span>
    </div>

    <!-- 01 / OVERVIEW -->
    <section class="block">
      <div class="kicker">01 / OVERVIEW</div>
      <h1>{{ detail.headline }}</h1>
      <p class="summary">{{ detail.summary }}</p>
    </section>

    <div class="grid">
      <div class="main">
        <template v-for="sec in detail.sections" :key="sec.kicker">
          <!-- 架构 / 管线图 -->
          <section v-if="sec.type === 'arch'" class="block">
            <div class="kicker">{{ sec.kicker }}</div>
            <component :is="archMap[sec.component]" />
          </section>

          <!-- 实现 / 决策：问题→方案→效果 -->
          <section v-else-if="sec.type === 'impl'" class="block">
            <div class="kicker">{{ sec.kicker }}</div>
            <div class="impl">
              <div v-for="item in sec.items" :key="item.title" class="impl-item">
                <h3>{{ item.title }}</h3>
                <dl>
                  <div><dt>{{ (sec.labels || defaultLabels)[0] }}</dt><dd>{{ item.problem }}</dd></div>
                  <div><dt>{{ (sec.labels || defaultLabels)[1] }}</dt><dd>{{ item.solution }}</dd></div>
                  <div><dt>{{ (sec.labels || defaultLabels)[2] }}</dt><dd>{{ item.result }}</dd></div>
                </dl>
              </div>
            </div>
          </section>

          <!-- 数据卡 -->
          <section v-else-if="sec.type === 'metrics'" class="block">
            <div class="kicker">{{ sec.kicker }}</div>
            <div class="metrics">
              <div v-for="m in sec.items" :key="m.label" class="metric">
                <span class="mlabel">{{ m.label }}</span>
                <span class="mvalue">{{ m.value }}</span>
                <span v-if="m.delta" class="mdelta">{{ m.delta }}</span>
              </div>
            </div>
            <p class="mnote">{{ sec.note }}</p>
          </section>

          <!-- 问题与解决 -->
          <section v-else-if="sec.type === 'pits'" class="block">
            <div class="kicker">{{ sec.kicker }}</div>
            <div class="pits">
              <div v-for="p in sec.items" :key="p.title" class="pit">
                <h3>{{ p.title }}</h3>
                <p>{{ p.body }}</p>
              </div>
            </div>
          </section>

          <!-- 纯文本（收获等） -->
          <section v-else-if="sec.type === 'text'" class="block">
            <div class="kicker">{{ sec.kicker }}</div>
            <p class="gains">{{ sec.body }}</p>
          </section>
        </template>
      </div>

      <!-- 侧栏 FILE / META -->
      <aside class="meta">
        <div class="meta-title">FILE / META</div>
        <dl>
          <div v-for="[k, v] in detail.meta" :key="k">
            <dt>{{ k }}</dt>
            <dd>{{ v }}</dd>
          </div>
        </dl>
      </aside>
    </div>

    <nav class="pn">
      <RouterLink v-if="prev" :to="`/projects/${prev.slug}`">← PROJECT / {{ prev.no }} {{ prev.title }}</RouterLink>
      <span v-else></span>
      <RouterLink v-if="next" :to="`/projects/${next.slug}`">PROJECT / {{ next.no }} {{ next.title }} →</RouterLink>
    </nav>
  </article>

  <!-- 其余项目：占位骨架（素材到位后按同模式接入） -->
  <div v-else class="wrap detail">
    <template v-if="project">
      <div class="crumb">
        <RouterLink to="/#work">← 返回 WORK</RouterLink>
        <span>PROJECT / {{ project.no }} — {{ project.title }}</span>
      </div>
      <section class="block">
        <div class="kicker">01 / OVERVIEW</div>
        <h1>{{ project.title }}</h1>
        <p class="summary">{{ project.summary }}</p>
      </section>
      <section class="block">
        <div class="kicker">02 / 详情正文</div>
        <p class="todo">素材整理中，即将按档案式结构补全（架构 / 核心实现 / 问题与解决 / 收获）。</p>
      </section>
    </template>
    <p v-else>未找到该项目。<RouterLink to="/">返回首页</RouterLink></p>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { projects } from '../data/site.js'
import { yunshuDetail } from '../data/projects/yunshu.js'
import { intelnexusDetail } from '../data/projects/intelnexus.js'
import YunshuArch from '../components/project/YunshuArch.vue'
import IntelnexusArch from '../components/project/IntelnexusArch.vue'

const details = { yunshu: yunshuDetail, intelnexus: intelnexusDetail }
const archMap = { yunshu: YunshuArch, intelnexus: IntelnexusArch }
const defaultLabels = ['问题', '方案', '效果']

const route = useRoute()
const detail = computed(() => details[route.params.slug] || null)
const project = computed(() => projects.find((x) => x.slug === route.params.slug) || null)
const idx = computed(() => projects.findIndex((x) => x.slug === route.params.slug))
const prev = computed(() => projects[idx.value - 1] || null)
const next = computed(() => projects[idx.value + 1] || null)
</script>

<style lang="scss" scoped>
.detail {
  padding: 30px 0 75px;
}
.crumb {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--c-line);
  padding-bottom: 14px;
  font: 10px var(--font-mono);
  letter-spacing: 0.12em;
  color: var(--c-muted);

  a:hover {
    color: var(--c-blue);
  }
  span {
    color: var(--c-blue);
    text-align: right;
  }
}
.block {
  margin-top: 34px;

  .kicker {
    font: 10px var(--font-mono);
    letter-spacing: 0.15em;
    color: var(--c-blue);
    margin-bottom: 12px;
  }
  h1 {
    font-size: clamp(30px, 4vw, 44px);
    font-weight: 600;
    letter-spacing: -0.05em;
  }
}
.summary {
  margin-top: 12px;
  font-size: 14px;
  line-height: 2;
  color: #626970;
  max-width: 680px;
}
.grid {
  display: grid;
  grid-template-columns: 1fr 230px;
  gap: 40px;
  margin-top: 8px;
}

// 核心实现 / 决策
.impl {
  display: grid;
  gap: 14px;
}
.impl-item {
  border: 1px solid var(--c-line);
  padding: 18px 20px;
  background: #fff;
  position: relative;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    width: 42px;
    border-top: 2px solid var(--c-blue);
  }
  h3 {
    font-size: 15px;
    font-weight: 600;
    margin: 0 0 12px;
  }
  dl div {
    display: grid;
    grid-template-columns: 52px 1fr;
    gap: 10px;
    margin-bottom: 8px;
  }
  dt {
    font: 9px var(--font-mono);
    letter-spacing: 0.14em;
    color: var(--c-blue);
    padding-top: 3px;
  }
  dd {
    margin: 0;
    font-size: 12.5px;
    line-height: 1.9;
    color: #626970;
  }
}

// 数据卡
.metrics {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;

  .metric {
    background: var(--c-blue-soft);
    padding: 12px 16px;
    min-width: 150px;
    flex: 1;
  }
  .mlabel {
    display: block;
    font: 9px var(--font-mono);
    letter-spacing: 0.12em;
    color: var(--c-muted);
  }
  .mvalue {
    font-size: 16px;
    font-weight: 600;
    color: var(--c-blue);
  }
  .mdelta {
    display: inline-block;
    margin-left: 8px;
    font: 9px var(--font-mono);
    color: var(--c-muted);
  }
}
.mnote {
  margin-top: 12px;
  font-size: 11px;
  line-height: 1.8;
  color: var(--c-muted);
  border-left: 2px solid var(--c-line);
  padding-left: 12px;
}

// 问题与解决
.pits {
  display: grid;
  gap: 14px;
}
.pit {
  border-left: 2px solid var(--c-blue);
  padding: 2px 0 2px 16px;

  h3 {
    font-size: 14px;
    font-weight: 600;
    margin: 0 0 6px;
  }
  p {
    margin: 0;
    font-size: 12.5px;
    line-height: 1.95;
    color: #626970;
  }
}

// 纯文本
.gains {
  margin: 0;
  font-size: 13px;
  line-height: 2.05;
  color: #626970;
  max-width: 680px;
}

// 侧栏
.meta {
  border-left: 1px solid var(--c-line);
  padding-left: 20px;
  font: 11px var(--font-mono);
  letter-spacing: 0.08em;
  color: var(--c-muted);
  height: fit-content;
  position: sticky;
  top: 100px;

  .meta-title {
    color: var(--c-blue);
    letter-spacing: 0.14em;
    margin-bottom: 12px;
  }
  dl div {
    margin-bottom: 12px;
  }
  dt {
    font-size: 9px;
    letter-spacing: 0.14em;
    color: #a1a6ad;
  }
  dd {
    margin: 3px 0 0;
    line-height: 1.75;
  }
}
.todo {
  border-left: 2px solid var(--c-blue);
  padding: 4px 0 4px 14px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--c-muted);
}
.pn {
  margin-top: 50px;
  border-top: 1px solid var(--c-line);
  padding-top: 18px;
  display: flex;
  justify-content: space-between;
  font: 10px var(--font-mono);
  letter-spacing: 0.1em;
  color: var(--c-blue);
}
@media (max-width: 760px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .meta {
    border-left: none;
    border-top: 1px solid var(--c-line);
    padding: 16px 0 0;
    position: static;
  }
  .impl-item dl div {
    grid-template-columns: 1fr;
    gap: 2px;
  }
  .pn {
    flex-direction: column;
    gap: 12px;
  }
}
</style>
