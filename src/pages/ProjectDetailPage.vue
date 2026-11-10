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
          <!-- 架构 / 管线图（component 未注册时整节跳过，不渲染空壳） -->
          <section v-if="sec.type === 'arch' && archMap[sec.component]" class="block">
            <div class="kicker">{{ sec.kicker }}</div>
            <component :is="archMap[sec.component]" />
          </section>

          <!-- 界面实录：截图网格 + Lightbox（pending 时显示待办占位） -->
          <section v-else-if="sec.type === 'shots'" class="block">
            <div class="kicker">{{ sec.kicker }}</div>
            <div v-if="sec.pending" class="shots-pending">
              <div class="sp-title">PENDING — 界面截图收集中</div>
              <ul>
                <li v-for="w in sec.wanted" :key="w">{{ w }}</li>
              </ul>
            </div>
            <template v-else>
              <div class="shots">
                <figure
                  v-for="(s, i) in sec.items || []"
                  :key="s.src"
                  class="shot"
                  role="button"
                  tabindex="0"
                  :aria-label="`查看截图大图：${s.caption || s.src}`"
                  @click="openShot(sec, i)"
                  @keydown.enter.prevent="openShot(sec, i)"
                >
                  <img
                    :src="shotThumb(s.src)"
                    :alt="s.caption || s.src"
                    loading="lazy"
                    decoding="async"
                    :class="{ ready: loadedShots.has(s.src) }"
                    @load="markShotReady(s.src)"
                    @error="onShotImgError($event, s.src)"
                  />
                  <figcaption>{{ s.caption }}<span class="shot-hint"> · 点击查看完整截图</span></figcaption>
                </figure>
              </div>
              <p v-if="sec.note" class="mnote">{{ sec.note }}</p>
            </template>
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
    <!-- 界面截图 Lightbox -->
    <Teleport to="body">
      <div v-if="shot" class="shotbox" role="dialog" aria-modal="true" aria-label="截图查看器" @click.self="closeShot">
        <button ref="sbCloseRef" class="lb-close" aria-label="关闭" @click="closeShot">×</button>
        <button class="lb-arrow lb-prev" aria-label="上一张" @click.stop="stepShot(-1)">←</button>
        <figure class="lb-body" @click.self="closeShot">
          <img :src="shotSrc(shot.s.src)" :alt="shot.s.caption || shot.s.src" />
          <figcaption>{{ shot.s.caption }}</figcaption>
        </figure>
        <button class="lb-arrow lb-next" aria-label="下一张" @click.stop="stepShot(1)">→</button>
      </div>
    </Teleport>
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
import { computed, nextTick, onMounted, onUnmounted, ref, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import { projects } from '../data/site.js'
import YunshuArch from '../components/project/YunshuArch.vue'
import IntelnexusArch from '../components/project/IntelnexusArch.vue'
import ForgeArch from '../components/project/ForgeArch.vue'

const BASE = import.meta.env.BASE_URL

// 界面实录：网格加载 1200px/q85 缩略图（npm run shots 自动产出），Lightbox 才取完整原图。
// 缩略图缺失时回退原图，保证管理台新挂的截图不会出现空白。
const shotSrc = (src) => `${BASE}${src.replace(/^\//, '')}`
const shotThumb = (src) =>
  `${BASE}${src.replace(/^\//, '').replace(/\/([^/]+)\.(png|jpe?g)$/i, '/thumbs/$1.jpg')}`

// 就绪后才淡入：加载期间由纯色骨架占位（不用模糊占位图，避免"先糊后清"的观感），
// 同时 CSS 固定 16:10 高度，图片到达不再把下方内容逐张往下顶。
const loadedShots = ref(new Set())
function markShotReady(src) {
  if (loadedShots.value.has(src)) return
  const next = new Set(loadedShots.value)
  next.add(src)
  loadedShots.value = next
}
function onShotImgError(e, src) {
  const el = e.target
  if (el.dataset.fallback) return // 原图也失败时不再重试，避免死循环
  el.dataset.fallback = '1'
  el.src = shotSrc(src)
}

// 详情数据动态收集：管理台新建的项目（发布后生成 content/projects/<slug>.json）自动可渲染，
// 无需改此文件注册（此前静态 import 三个项目，新 slug 只能显示占位骨架）
const detailModules = import.meta.glob('../../content/projects/*.json', { eager: true })
const details = Object.fromEntries(
  Object.entries(detailModules).map(([path, mod]) => {
    const slug = path.split('/').pop().replace(/\.json$/, '')
    const value = mod.default || mod[Object.keys(mod)[0]]
    return [slug, value]
  }),
)
const archMap = { yunshu: YunshuArch, intelnexus: IntelnexusArch, forge: ForgeArch }
const defaultLabels = ['问题', '方案', '效果']

const route = useRoute()
const detail = computed(() => details[route.params.slug] || null)
const project = computed(() => projects.find((x) => x.slug === route.params.slug) || null)
const idx = computed(() => projects.findIndex((x) => x.slug === route.params.slug))
const prev = computed(() => projects[idx.value - 1] || null)
const next = computed(() => projects[idx.value + 1] || null)

// 每路由 SEO：项目页 title 按数据动态设置
watchEffect(() => {
  document.title = detail.value
    ? `${detail.value.headline} — YUAN`
    : project.value
      ? `${project.value.title} — YUAN`
      : '未找到项目 — YUAN'
})

// 界面截图 Lightbox
const shotSec = ref(null)
const shotIdx = ref(-1)
const sbCloseRef = ref(null)
let lastFocus = null // 打开前聚焦元素，关闭时归还焦点
const shot = computed(() => {
  if (!shotSec.value || shotIdx.value < 0) return null
  const items = shotSec.value.items
  return { s: items[shotIdx.value], n: items.length }
})
const openShot = (sec, i) => {
  lastFocus = document.activeElement
  shotSec.value = sec
  shotIdx.value = i
  document.body.style.overflow = 'hidden'
  nextTick(() => sbCloseRef.value?.focus())
}
const closeShot = () => {
  shotSec.value = null
  shotIdx.value = -1
  document.body.style.overflow = ''
  lastFocus?.focus?.()
  lastFocus = null
}
const stepShot = (d) => {
  const n = shotSec.value.items.length
  shotIdx.value = (shotIdx.value + d + n) % n
}
const onKey = (e) => {
  if (!shot.value) return
  if (e.key === 'Escape') closeShot()
  else if (e.key === 'ArrowLeft') stepShot(-1)
  else if (e.key === 'ArrowRight') stepShot(1)
}
onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => {
  window.removeEventListener('keydown', onKey)
  document.body.style.overflow = ''
})
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
  color: var(--c-ink-soft);
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
  background: var(--c-bg);
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
    color: var(--c-ink-soft);
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
    color: var(--c-ink-soft);
  }
}

// 纯文本
.gains {
  margin: 0;
  font-size: 13px;
  line-height: 2.05;
  color: var(--c-ink-soft);
  max-width: 680px;
  white-space: pre-line; // 管理台 textarea 里的换行原样保留（任务 F）
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
    color: var(--c-muted);
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
// 界面实录
.shots {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}
.shot {
  margin: 0;
  cursor: pointer;

  img {
    width: 100%;
    // 固定比例占位：加载前就有高度，消除逐张撑开造成的抖动；2 倍屏下 1200px 缩略图仍有余量
    aspect-ratio: 16 / 10;
    // contain 而非 cover：界面截图看的是文字与布局，宁可上下留白也不裁掉左右内容
    object-fit: contain;
    object-position: top center;
    display: block;
    border: 1px solid var(--c-line);
    background: var(--c-bg);
    opacity: 0;
    transition: opacity 0.32s ease, border-color 0.22s, box-shadow 0.22s;

    &.ready {
      opacity: 1;
    }
  }
  &:hover img {
    border-color: var(--c-blue-hover);
    box-shadow: 4px 4px 0 var(--c-blue-shadow);
  }
  figcaption {
    margin-top: 8px;
    font: 9px var(--font-mono);
    letter-spacing: 0.12em;
    color: var(--c-muted);
  }
  .shot-hint {
    color: var(--c-blue);
    opacity: 0.75;
  }
}
.shots-pending {
  border: 1px dashed var(--c-aux);
  padding: 18px 22px;
  background: var(--c-blue-faint);

  .sp-title {
    font: 10px var(--font-mono);
    letter-spacing: 0.15em;
    color: var(--c-blue);
    margin-bottom: 10px;
  }
  ul {
    margin: 0;
    padding-left: 18px;

    li {
      font-size: 12.5px;
      line-height: 2;
      color: var(--c-muted);
    }
  }
}
// 截图 Lightbox（与摄影页同款交互）
.shotbox {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.96);
  display: grid;
  grid-template-columns: 70px 1fr 70px;
  align-items: center;
}
.shotbox .lb-body img {
  max-width: 92%;
  max-height: 82vh;
  border: 1px solid var(--c-line);
}
.shotbox .lb-body figcaption {
  margin-top: 14px;
  font: 10px var(--font-mono);
  letter-spacing: 0.14em;
  color: var(--c-blue);
}
.shotbox .lb-arrow {
  font: 20px var(--font-mono);
  color: var(--c-blue);
  background: var(--c-bg);
  border: 1px solid var(--c-line);
  width: 46px;
  height: 46px;
  cursor: pointer;
  justify-self: center;
  transition: 0.2s;

  &:hover {
    border-color: var(--c-blue);
    box-shadow: 3px 3px 0 var(--c-blue-shadow);
  }
}
.shotbox .lb-close {
  position: absolute;
  top: 18px;
  right: 26px;
  width: 46px;
  height: 46px;
  font-size: 22px;
  color: var(--c-muted);
  background: var(--c-bg);
  border: 1px solid var(--c-line);
  cursor: pointer;

  &:hover {
    color: var(--c-blue);
    border-color: var(--c-blue);
  }
}
@media (max-width: 760px) {
  .shots {
    grid-template-columns: 1fr;
  }
  .shotbox {
    grid-template-columns: 1fr;
  }
  .shotbox .lb-arrow {
    position: absolute;
    bottom: 24px;
  }
  .shotbox .lb-prev {
    left: 24px;
  }
  .shotbox .lb-next {
    right: 24px;
  }
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
