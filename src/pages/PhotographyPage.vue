<template>
  <div class="page">
    <!-- 页头大图 -->
    <div class="wrap">
      <div class="kicker">04 / PHOTOGRAPHY</div>
      <h1>Field Notes.</h1>
      <p class="lead">观察光线、街道与日常场景，记录那些值得注意的瞬间。多数照片拍摄于黄昏与夜晚。</p>
    </div>
    <div class="wrap">
      <figure
        class="hero-ph"
        role="button"
        tabindex="0"
        aria-label="查看页头照片大图"
        @click="openAt(flatIndex(heroPhoto.id))"
        @keydown.enter.prevent="openAt(flatIndex(heroPhoto.id))"
      >
        <picture>
          <source type="image/webp" :srcset="webpSrcset(heroPhoto.id)" :sizes="'100vw'" />
          <img
            :src="heroSrc"
            :alt="`Field Notes 页头照片，${heroPhoto.date} 摄`"
            fetchpriority="high"
            decoding="async"
            :class="{ ready: heroReady }"
            @load="heroReady = true"
          />
        </picture>
        <figcaption class="ph-label">FIG. 000 — {{ heroPhoto.date }} · 点击任意照片查看大图</figcaption>
      </figure>
    </div>

    <!-- 对齐行印样：按主题分组，每行等高、宽度按原比例分配 -->
    <div class="wrap gallery" ref="galleryEl">
      <div v-for="(g, gi) in photoGroups" :key="g.name" class="group">
        <div class="ghead">
          <div>
            <span class="gname">{{ g.name }}</span>
            <span class="gdesc">{{ g.desc }}</span>
          </div>
          <span class="gno">{{ g.plates.length }} PLATES</span>
        </div>
        <div v-for="(row, ri) in rowsFor(g)" :key="ri" class="jrow">
          <figure
            v-for="it in row.items"
            :key="it.p.id"
            class="plate"
            role="button"
            tabindex="0"
            :aria-label="`查看 ${plateLabel(it.p.id)} 大图`"
            :style="{ flex: `${it.r} 1 0px` }"
            @click="openAt(flatIndex(it.p.id))"
            @keydown.enter.prevent="openAt(flatIndex(it.p.id))"
          >
            <div class="ph" :style="{ height: row.h + 'px' }">
              <!-- 网格按显示宽度选档：webp 变体（400/800/1600，tools/gen-photo-variants.py 生成）优先，
                   jpg thumbs 640/原图 1600 作为不支持 webp 的回退。绝不让高分屏把轻量档放大显示。
                   Lightbox 与头图永远加载 1600px 原图。首组首行 eager，其余 lazy 避免同域排队。 -->
              <picture>
                <source
                  type="image/webp"
                  :srcset="webpSrcset(it.p.id)"
                  sizes="(max-width: 760px) 50vw, 48vw"
                />
                <img
                  :src="`${BASE}photos/${it.p.id}.jpg`"
                  :srcset="`${BASE}photos/thumbs/${it.p.id}.jpg ${wOf(it.p.id, 640)}w, ${BASE}photos/${it.p.id}.jpg ${wOf(it.p.id, 1600)}w`"
                  sizes="(max-width: 760px) 50vw, 48vw"
                  :alt="plateAlt(g, it.p)"
                  :loading="gi === 0 && ri === 0 ? 'eager' : 'lazy'"
                  :fetchpriority="gi === 0 && ri === 0 ? 'high' : undefined"
                  decoding="async"
                  :class="{ ready: loadedPlates.has(it.p.id) }"
                  @load="markPlateReady(it.p.id)"
                  @error="onPlateImgError($event, it.p.id)"
                />
              </picture>
            </div>
            <figcaption>{{ plateLabel(it.p.id) }} — {{ it.p.date }}<template v-if="it.p.cap"> · {{ it.p.cap }}</template></figcaption>
          </figure>
        </div>
      </div>
    </div>

    <p class="wrap note">* 对齐行排版：每行等高，宽度按原始比例分配，仅微量裁切对齐行缘；点击查看原比例大图（长边 1600px）。</p>

    <!-- Lightbox -->
    <Teleport to="body">
      <div v-if="lightbox >= 0" class="lightbox" role="dialog" aria-modal="true" aria-label="照片查看器" @click.self="close">
        <button ref="lbCloseRef" class="lb-close" aria-label="关闭" @click="close">×</button>
        <button class="lb-arrow lb-prev" aria-label="上一张" @click.stop="step(-1)">←</button>
        <figure class="lb-body" @click.self="close">
          <img :src="`${BASE}photos/${flatPlates[lightbox].id}.jpg`" :alt="lightboxAlt" />
          <figcaption>{{ lightboxLabel }} — {{ flatPlates[lightbox].date }}<template v-if="flatPlates[lightbox].cap"> · {{ flatPlates[lightbox].cap }}</template></figcaption>
        </figure>
        <button class="lb-arrow lb-next" aria-label="下一张" @click.stop="step(1)">→</button>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { heroPhoto, photoGroups, flatPlates, plateNo, dims } from '../data/photos.js'

const BASE = import.meta.env.BASE_URL
const heroSrc = `${BASE}photos/${heroPhoto.id}.jpg`

// 响应式 webp：tools/gen-photo-variants.py 生成的 <pid>-<width>.webp 变体，
// 档位 400/800/1600 + 原宽（竖图原宽 <1600 时跳过放大档）。灯箱仍用原 jpg（最高画质）。
function webpSrcset(id) {
  const r = dims[id] || 1.5
  const origW = r >= 1 ? 1600 : Math.round(1600 * r)
  const widths = [400, 800, 1600].filter((w) => w < origW)
  widths.push(origW)
  return widths.map((w) => `${BASE}photos/${id}-${w}.webp ${w}w`).join(', ')
}

// 组件 chunk 一就绪就发起头图请求：不等 Vue 挂载、不等模板渲染，缩短首屏空白。
// href 与 <img src> 完全一致，浏览器复用同一请求，不会重复下载。
const heroPreload = document.createElement('link')
heroPreload.rel = 'preload'
heroPreload.as = 'image'
heroPreload.href = heroSrc
heroPreload.setAttribute('fetchpriority', 'high')
document.head.appendChild(heroPreload)
onUnmounted(() => heroPreload.remove())

const heroReady = ref(false)

// 就绪后才淡入：加载期间是纯色骨架（不是模糊占位图），避免"先糊一下再变清"；
// .ph 容器已由对齐行算法固定高度，图片到达不会把下方内容顶开。
const loadedPlates = ref(new Set())
function markPlateReady(id) {
  if (loadedPlates.value.has(id)) return
  const next = new Set(loadedPlates.value)
  next.add(id)
  loadedPlates.value = next
}
// 缩略图缺失（新照片发布时漏带 thumbs）→ 清空 srcset 后回退原图，只回退一次
function onPlateImgError(e, id) {
  const el = e.target
  if (el.dataset.fallback) return
  el.dataset.fallback = '1'
  el.removeAttribute('srcset')
  el.src = `${BASE}photos/${id}.jpg`
}

// 编号：全局按分组顺序连续
const flatIndex = (id) => flatPlates.findIndex((x) => x.id === id)
const plateLabel = (id) => plateNo(Math.max(flatIndex(id), 0))
const plateAlt = (g, p) => `${g.name} — ${p.date}`

// srcset 的 w 描述符必须是图片真实像素宽：Thumbnails 按「长边不超过 edge」等比缩，
// 竖拍图（宽高比 < 1）的实际宽度 = edge × 宽高比，不能一律写 640w / 1600w。
const wOf = (id, edge) => {
  const r = dims[id] || 1.5
  return Math.round(r >= 1 ? edge : edge * r)
}

// ---- 对齐行（justified rows）布局 ----
// 贪心装行：累计宽高比达到目标值即封行；行高 = 可用宽度 / 行内比例和（有上限）
const galleryEl = ref(null)
const galleryW = ref(1120)
let ro = null

// 行内间距须与下方 .gallery 样式的 CSS gap 同步（桌面 14px / 移动端 10px），改一处必改另一处
const GAP_DESKTOP = 14
const GAP_MOBILE = 10

const rowsFor = (group) => {
  const w = galleryW.value || 1120
  const gap = w < 640 ? GAP_MOBILE : GAP_DESKTOP
  const target = w < 640 ? 2.0 : 3.3
  const maxH = w < 640 ? 200 : 320
  const rows = []
  let cur = []
  let sum = 0
  for (const p of group.plates) {
    const r = dims[p.id] || 1.5
    cur.push({ p, r })
    sum += r
    if (sum >= target) {
      rows.push(cur)
      cur = []
      sum = 0
    }
  }
  if (cur.length) rows.push(cur)
  return rows.map((items) => {
    const s = items.reduce((a, x) => a + x.r, 0)
    const h = Math.min((w - gap * (items.length - 1)) / s, maxH)
    return { h, items }
  })
}

onMounted(async () => {
  await nextTick()
  if (galleryEl.value) {
    ro = new ResizeObserver((entries) => {
      galleryW.value = entries[0].contentRect.width
    })
    ro.observe(galleryEl.value)
  }
})
onUnmounted(() => {
  if (ro) ro.disconnect()
  document.body.style.overflow = ''
})

// Lightbox
const lightbox = ref(-1)
const lbCloseRef = ref(null)
let lastFocus = null // 打开前聚焦元素，关闭时归还焦点
const lightboxLabel = computed(() =>
  lightbox.value >= 0 ? plateNo(lightbox.value) : ''
)
const lightboxAlt = computed(() =>
  lightbox.value >= 0 ? `照片 ${lightboxLabel.value}，${flatPlates[lightbox.value].date} 摄` : ''
)
const openAt = (idx) => {
  lastFocus = document.activeElement
  lightbox.value = idx
  document.body.style.overflow = 'hidden'
  nextTick(() => lbCloseRef.value?.focus())
}
const close = () => {
  lightbox.value = -1
  document.body.style.overflow = ''
  lastFocus?.focus?.()
  lastFocus = null
}
const step = (d) => {
  const n = flatPlates.length
  lightbox.value = (lightbox.value + d + n) % n
}
const onKey = (e) => {
  if (lightbox.value < 0) return
  if (e.key === 'Escape') close()
  else if (e.key === 'ArrowLeft') step(-1)
  else if (e.key === 'ArrowRight') step(1)
}
onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => window.removeEventListener('keydown', onKey))
</script>

<style lang="scss" scoped>
.page {
  padding: 50px 0 75px;
}
.kicker {
  font: 10px var(--font-mono);
  letter-spacing: 0.15em;
  color: var(--c-blue);
  margin-bottom: 14px;
}
h1 {
  font-size: clamp(30px, 4vw, 46px);
  font-weight: 600;
  letter-spacing: -0.04em;
}
.lead {
  margin-top: 10px;
  font-size: 13px;
  color: var(--c-muted);
  line-height: 2;
  max-width: 560px;
}
.hero-ph {
  margin: 24px 0 0;
  position: relative;
  border: 1px solid var(--c-line-blue);
  overflow: hidden;
  cursor: pointer;
  aspect-ratio: 16 / 9;
  background: var(--c-bg-soft); // 图片到达前的纯色骨架，不做模糊占位
  transition: 0.22s;

  &:hover {
    border-color: var(--c-blue-hover);
    box-shadow: 5px 5px 0 var(--c-blue-shadow-soft);
  }
  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
    opacity: 0;
    transition: opacity 0.35s ease;

    &.ready {
      opacity: 1;
    }
  }
}
.ph-label {
  position: absolute;
  bottom: 12px;
  left: 15px;
  font: 9px var(--font-mono);
  letter-spacing: 0.1em;
  color: var(--c-blue);
  background: rgba(255, 255, 255, 0.92);
  padding: 4px 7px;
}
.gallery {
  margin-top: 50px;
}
.group {
  margin-bottom: 44px;

  &:last-child {
    margin-bottom: 0;
  }
}
.ghead {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 16px;
  border-top: 1px solid var(--c-line);
  padding-top: 14px;
  margin-bottom: 14px;

  .gname {
    font: 500 14px var(--font-sans);
    letter-spacing: 0.06em;
    margin-right: 12px;
  }
  .gdesc {
    font-size: 12px;
    color: var(--c-muted);
  }
  .gno {
    font: 9px var(--font-mono);
    letter-spacing: 0.14em;
    color: var(--c-blue);
    white-space: nowrap;
  }
}
// 对齐行：flex-basis 0 + flex-grow=宽高比 → 宽度精确按比例分配，行缘对齐
// gap 与上方 rowsFor() 的 GAP_DESKTOP/GAP_MOBILE 常量同步
.jrow {
  display: flex;
  gap: 14px;
  margin-bottom: 14px;
}
.plate {
  margin: 0;
  cursor: pointer;
  min-width: 0;

  .ph {
    border: 1px solid var(--c-line);
    overflow: hidden;
    background: var(--c-bg-soft);

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
      opacity: 0;
      transition: opacity 0.32s ease, transform 0.25s;

      &.ready {
        opacity: 1;
      }
    }
  }
  &:hover .ph img {
    transform: scale(1.025);
  }
  &:hover .ph {
    border-color: var(--c-blue-hover);
  }
  figcaption {
    margin-top: 8px;
    font: 9px var(--font-mono);
    letter-spacing: 0.12em;
    color: var(--c-muted);
  }
}
.note {
  margin-top: 40px;
  font: 10px var(--font-mono);
  letter-spacing: 0.08em;
  color: var(--c-muted);
}

// Lightbox
.lightbox {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.96);
  display: grid;
  grid-template-columns: 70px 1fr 70px;
  align-items: center;
}
.lb-body {
  margin: 0;
  text-align: center;
  min-width: 0;

  img {
    max-width: 100%;
    max-height: 82vh;
    display: block;
    margin: auto;
    border: 1px solid var(--c-line);
  }
  figcaption {
    margin-top: 14px;
    font: 10px var(--font-mono);
    letter-spacing: 0.14em;
    color: var(--c-blue);
  }
}
.lb-arrow {
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
.lb-close {
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
  .jrow {
    gap: 10px;
    margin-bottom: 10px;
  }
  .ghead {
    flex-direction: column;
    gap: 6px;
  }
  .lightbox {
    grid-template-columns: 1fr;
  }
  .lb-arrow {
    position: absolute;
    bottom: 24px;
  }
  .lb-prev {
    left: 24px;
  }
  .lb-next {
    right: 24px;
  }
}
</style>
