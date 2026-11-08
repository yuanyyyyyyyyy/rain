<template>
  <div class="page">
    <!-- 页头大图 -->
    <div class="wrap">
      <div class="kicker">04 / PHOTOGRAPHY</div>
      <h1>Field Notes.</h1>
      <p class="lead">观察光线、街道与日常场景，记录那些值得注意的瞬间。多数照片拍摄于黄昏与夜晚。</p>
    </div>
    <div class="wrap">
      <figure class="hero-ph" @click="openAt(flatIndex(heroPhoto.id))">
        <img :src="heroSrc" :alt="'页头照片 ' + heroPhoto.date" />
        <figcaption class="ph-label">FIG. 000 — {{ heroPhoto.date }} · 点击任意照片查看大图</figcaption>
      </figure>
    </div>

    <!-- 对齐行印样：按主题分组，每行等高、宽度按原比例分配 -->
    <div class="wrap gallery" ref="galleryEl">
      <div v-for="g in photoGroups" :key="g.name" class="group">
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
            :style="{ flex: `${it.r} 1 0px` }"
            @click="openAt(flatIndex(it.p.id))"
          >
            <div class="ph" :style="{ height: row.h + 'px' }">
              <img :src="`/photos/${it.p.id}.jpg`" :alt="plateAlt(g, it.p)" loading="lazy" />
            </div>
            <figcaption>{{ plateLabel(it.p.id) }} — {{ it.p.date }}</figcaption>
          </figure>
        </div>
      </div>
    </div>

    <p class="wrap note">* 对齐行排版：每行等高，宽度按原始比例分配，仅微量裁切对齐行缘；点击查看原比例大图（长边 1600px）。</p>

    <!-- Lightbox -->
    <Teleport to="body">
      <div v-if="lightbox >= 0" class="lightbox" @click.self="close">
        <button class="lb-close" aria-label="关闭" @click="close">×</button>
        <button class="lb-arrow lb-prev" aria-label="上一张" @click.stop="step(-1)">←</button>
        <figure class="lb-body" @click.self="close">
          <img :src="`/photos/${flatPlates[lightbox].id}.jpg`" :alt="lightboxAlt" />
          <figcaption>{{ lightboxLabel }} — {{ flatPlates[lightbox].date }}</figcaption>
        </figure>
        <button class="lb-arrow lb-next" aria-label="下一张" @click.stop="step(1)">→</button>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { heroPhoto, photoGroups, flatPlates, plateNo, dims } from '../data/photos.js'

const heroSrc = `/photos/${heroPhoto.id}.jpg`

// 编号：全局按分组顺序连续
const flatIndex = (id) => flatPlates.findIndex((x) => x.id === id)
const plateLabel = (id) => plateNo(Math.max(flatIndex(id), 0))
const plateAlt = (g, p) => `${g.name} — ${p.date}`

// ---- 对齐行（justified rows）布局 ----
// 贪心装行：累计宽高比达到目标值即封行；行高 = 可用宽度 / 行内比例和（有上限）
const galleryEl = ref(null)
const galleryW = ref(1120)
let ro = null

const rowsFor = (group) => {
  const w = galleryW.value || 1120
  const gap = w < 640 ? 10 : 14
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
const lightboxLabel = computed(() =>
  lightbox.value >= 0 ? plateNo(lightbox.value) : ''
)
const lightboxAlt = computed(() =>
  lightbox.value >= 0 ? `照片 ${lightboxLabel.value}` : ''
)
const openAt = (idx) => {
  lightbox.value = idx
  document.body.style.overflow = 'hidden'
}
const close = () => {
  lightbox.value = -1
  document.body.style.overflow = ''
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
  border: 1px solid #bfc8dc;
  overflow: hidden;
  cursor: pointer;
  aspect-ratio: 16 / 9;
  transition: 0.22s;

  &:hover {
    border-color: #8fa5dc;
    box-shadow: 5px 5px 0 #e7ecf8;
  }
  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
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
    background: #f6f8fc;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
      transition: 0.25s;
    }
  }
  &:hover .ph img {
    transform: scale(1.025);
  }
  &:hover .ph {
    border-color: #8fa5dc;
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
  background: #fff;
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
  background: #fff;
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
