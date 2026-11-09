<template>
  <div>
    <div class="head">
      <div>
        <div class="kicker">02 / PHOTOS</div>
        <h1>照片管理</h1>
      </div>
    </div>

    <!-- 上传区：点击 / 拖拽 / 粘贴 -->
    <div
      class="dropzone"
      :class="{ over: dzOver }"
      @click="fileRef?.click()"
      @dragover.prevent="dzOver = true"
      @dragleave="dzOver = false"
      @drop.prevent="onDropFiles($event)"
    >
      <input ref="fileRef" type="file" accept="image/*" multiple hidden @change="onPickFiles($event)" />
      <div class="dz-main">拖拽图片到这里，或点击选择（支持 Ctrl+V 粘贴截图）</div>
      <div class="dz-sub">拍摄日期自动从 EXIF 识别，识别不到用文件时间兜底；上传后仍可逐张修改</div>
    </div>

    <!-- 待上传队列：逐张可改分组/日期 -->
    <div v-if="queue.length" class="queue">
      <div class="queue-head">
        <div class="kicker">待上传 {{ queue.length }} 张 — 逐张可改分组 / 日期（日期留空 = 自动识别）</div>
        <button :disabled="uploading || !queue.some((i) => i.status === 'pending' || i.status === 'fail')" @click="doUpload">
          {{ uploading ? '上传中…' : `上传 ${queue.filter((i) => i.status !== 'done').length} 张` }}
        </button>
      </div>
      <div class="queue-grid">
        <div v-for="(it, idx) in queue" :key="it.uid" class="qcard" :class="it.status">
          <img :src="it.url" :alt="it.name" />
          <select v-model="it.group" :disabled="it.status === 'uploading' || it.status === 'done'">
            <option v-for="g in GROUP_DEFS" :key="g.key" :value="g.key">{{ g.key }}</option>
          </select>
          <input v-model="it.date" type="date" :disabled="it.status === 'uploading' || it.status === 'done'" @input="it.touched = true" />
          <div class="q-status">
            <template v-if="it.status === 'done'">
              <span class="ok">✓ {{ it.result?.dateSource === 'exif' ? 'EXIF' : it.result?.dateSource === 'manual' ? '手填' : '文件时间' }} · {{ it.result?.shotDate || '—' }}</span>
            </template>
            <template v-else-if="it.status === 'fail'">
              <span class="err">{{ it.error }}</span>
              <button class="mini" :disabled="uploading" @click="uploadOne(it)">重试</button>
            </template>
            <template v-else-if="it.exifDate">
              <span class="chip">EXIF 已识别 {{ it.date }}</span>
            </template>
            <template v-else>
              <span class="chip dim">自动识别</span>
            </template>
          </div>
          <button v-if="it.status === 'pending' || it.status === 'fail'" class="q-remove" title="移除" @click="removeFromQueue(idx)">×</button>
        </div>
      </div>
    </div>

    <p v-if="msg" class="msg" :class="{ err: msgIsErr }">{{ msg }}</p>

    <section v-for="g in groups" :key="g.key" class="group">
      <h2>
        {{ g.key }} / {{ g.label }}
        <span class="count">{{ g.photos.length }} 张 — 拖拽卡片排序（可跨组） · 点击图片看大图</span>
      </h2>
      <div class="grid" @dragover.prevent @drop="onDropAppend(g.key, $event)">
        <figure
          v-for="(p, i) in g.photos"
          :key="p.id"
          class="card"
          :class="{ dragging: isDragging(g.key, i), hero: p.isHero, editing: editId === p.id }"
          draggable="true"
          @dragstart="onDragStart(g.key, i)"
          @dragover.prevent
          @drop.stop="onDrop(g.key, i, $event)"
          @dragend="dragFrom = null"
        >
          <span class="handle" title="拖拽排序">⠿</span>
          <span v-if="p.isHero" class="hero-badge" title="摄影页页头大图">头图</span>
          <img :src="`/photos/${p.pid}.jpg`" :alt="p.pid" loading="lazy" @click="previewPid = p.pid" />
          <figcaption>
            <span class="pid">{{ p.pid.toUpperCase() }}</span>
            <span class="meta">{{ p.width }}×{{ p.height }} · {{ p.shotDate || '日期—' }}</span>
            <span v-if="p.caption" class="cap" :title="p.caption">{{ p.caption }}</span>
          </figcaption>

          <div v-if="editId !== p.id" class="ops">
            <button v-if="!p.isHero" class="mini" @click="setHero(p)" title="设为摄影页页头大图（当前头图会自动取消）">设头图</button>
            <button class="mini" @click="startEdit(p)">编辑</button>
            <button class="mini danger" @click="remove(p)">删除</button>
          </div>

          <div v-else class="edit">
            <input v-model="editDate" type="date" />
            <select v-model="editGroup">
              <option v-for="gd in GROUP_DEFS" :key="gd.key" :value="gd.key">{{ gd.key }}</option>
            </select>
            <input v-model="editCaption" type="text" placeholder="图注（可空）" class="cap-input" />
            <button class="mini" @click="saveEdit(p)">保存</button>
            <button class="mini" @click="editId = null">取消</button>
          </div>
        </figure>
      </div>
    </section>

    <!-- 大图预览 -->
    <Teleport to="body">
      <div v-if="previewPid" class="lightbox" @click.self="previewPid = null">
        <button class="lb-close" @click="previewPid = null">×</button>
        <img :src="`/photos/${previewPid}.jpg`" :alt="previewPid" />
        <div class="lb-cap">{{ previewPid.toUpperCase() }}</div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import exifr from 'exifr'
import { api } from '../api.js'

const GROUP_DEFS = [
  { key: 'STREET', label: '街道与城市' },
  { key: 'LIGHT', label: '光线与氛围' },
  { key: 'DAILY', label: '日常与人' },
]

const groups = ref([])
const fileRef = ref(null)
const dzOver = ref(false)
const queue = ref([])
const uploading = ref(false)
const msg = ref('')
const msgIsErr = ref(false)
const dragFrom = ref(null)
const editId = ref(null)
const editDate = ref('')
const editGroup = ref('')
const editCaption = ref('')
const previewPid = ref(null)

let uidSeq = 0

const pendingCount = computed(() => queue.value.filter((i) => i.status !== 'done').length)

function groupPhotos(list) {
  groups.value = GROUP_DEFS.map((g) => ({
    ...g,
    photos: list.filter((p) => p.groupName === g.key),
  }))
}

async function load() {
  groupPhotos(await api.get('/api/photos'))
}

function say(text, isErr = false) {
  msg.value = text
  msgIsErr.value = isErr
}

// ---- 上传队列 ----

function addFiles(files) {
  const images = [...files].filter((f) => f.type.startsWith('image/'))
  if (!images.length) return
  for (const file of images) {
    const it = {
      uid: ++uidSeq,
      file,
      url: URL.createObjectURL(file),
      name: file.name,
      group: 'STREET',
      date: '',
      touched: false,
      exifDate: false,
      status: 'pending',
      error: '',
      result: null,
    }
    queue.value.push(it)
    prefillDate(it)
  }
  say(`已加入 ${images.length} 张待上传`)
}

/** 前端 exifr 预提取拍摄日期（体验层；后端 EXIF 读取是权威兜底） */
async function prefillDate(it) {
  try {
    const tags = await exifr.parse(it.file, { pick: ['DateTimeOriginal', 'OffsetTimeOriginal'] })
    const d = tags?.DateTimeOriginal
    if (d instanceof Date && !Number.isNaN(d.getTime())) {
      it.date = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
      it.exifDate = true
    }
  } catch {
    // 非 JPEG / 无 EXIF → 留空，后端走 mtime 兜底
  }
}

function onPickFiles(e) {
  addFiles(e.target.files)
  e.target.value = ''
}

function onDropFiles(e) {
  dzOver.value = false
  addFiles(e.dataTransfer?.files || [])
}

function removeFromQueue(idx) {
  const [it] = queue.value.splice(idx, 1)
  if (it) URL.revokeObjectURL(it.url)
}

async function doUpload() {
  const targets = queue.value.filter((i) => i.status === 'pending' || i.status === 'fail')
  if (!targets.length) return
  uploading.value = true
  let cursor = 0
  // 并发 2：20MB 大图 × N 不打爆内存与后端 multipart 限制
  const worker = async () => {
    while (cursor < targets.length) {
      await uploadOne(targets[cursor++])
    }
  }
  await Promise.all([worker(), worker()])
  const ok = targets.filter((i) => i.status === 'done').length
  const fail = targets.length - ok
  if (fail) say(`${ok} 张成功，${fail} 张失败（列表内可单张重试）`, true)
  else say(`${ok} 张已上传入库。点「04 发布」即可更新到公网。`)
  if (!queue.value.some((i) => i.status !== 'done')) await clearQueue()
  await load()
}

async function uploadOne(it) {
  it.status = 'uploading'
  it.error = ''
  try {
    const fd = new FormData()
    fd.append('file', it.file)
    fd.append('groupName', it.group)
    if (it.date && it.touched) fd.append('shotDate', it.date)
    const r = await api.postForm('/api/photos/upload', fd)
    it.result = r
    it.status = 'done'
    // 未手动改过日期 → 展示后端权威解析结果（来源标注）
    if (!it.touched) it.date = r.shotDate || ''
  } catch (e) {
    it.status = 'fail'
    it.error = e.message
  }
}

async function clearQueue() {
  queue.value.forEach((i) => URL.revokeObjectURL(i.url))
  queue.value = []
}

// 组内/跨组拖拽：drop 后本地重排，一次请求批量写回（支持跨组移动）
function onDragStart(groupKey, index) {
  dragFrom.value = { groupKey, index }
}

function isDragging(groupKey, index) {
  return dragFrom.value?.groupKey === groupKey && dragFrom.value?.index === index
}

async function onDrop(groupKey, index, evt) {
  evt?.preventDefault()
  const from = dragFrom.value
  dragFrom.value = null
  if (!from) return
  const fromGroup = groups.value.find((x) => x.key === from.groupKey)
  const toGroup = groups.value.find((x) => x.key === groupKey)
  if (!fromGroup || !toGroup || (from.groupKey === groupKey && from.index === index)) return
  const [moved] = fromGroup.photos.splice(from.index, 1)
  moved.groupName = groupKey
  toGroup.photos.splice(index, 0, moved)
  await persistOrders([fromGroup, toGroup])
}

/** 拖到组内空白处 = 移到组尾（跨组拖拽的快捷路径） */
async function onDropAppend(groupKey, evt) {
  evt?.preventDefault()
  const from = dragFrom.value
  dragFrom.value = null
  if (!from) return
  const fromGroup = groups.value.find((x) => x.key === from.groupKey)
  const toGroup = groups.value.find((x) => x.key === groupKey)
  if (!fromGroup || !toGroup) return
  const [moved] = fromGroup.photos.splice(from.index, 1)
  moved.groupName = groupKey
  toGroup.photos.push(moved)
  await persistOrders([fromGroup, toGroup])
}

async function persistOrders(affected) {
  const items = []
  for (const g of affected) {
    g.photos.forEach((p, i) => {
      p.sortNo = i + 1
      items.push({ id: p.id, groupName: g.key, sortNo: i + 1 })
    })
  }
  try {
    await api.put('/api/photos/reorder', items)
    const cross = affected.length > 1
    say(cross ? `已跨组移动并保存排序` : `${affected[0].key} 组排序已保存`)
  } catch (e) {
    say(`排序保存失败：${e.message}`, true)
    await load()
  }
}

function startEdit(p) {
  editId.value = p.id
  editDate.value = p.shotDate || ''
  editGroup.value = p.groupName
  editCaption.value = p.caption || ''
}

async function saveEdit(p) {
  try {
    await api.put(`/api/photos/${p.id}`, {
      groupName: editGroup.value,
      shotDate: editDate.value || null,
      sortNo: p.sortNo,
      caption: editCaption.value || null,
    })
    editId.value = null
    say(`${p.pid} 已更新`)
    await load()
  } catch (e) {
    say(e.message, true)
  }
}

async function setHero(p) {
  try {
    await api.put(`/api/photos/${p.id}/hero`)
    say(`${p.pid} 已设为头图（原头图已取消）`)
    await load()
  } catch (e) {
    say(e.message, true)
  }
}

async function remove(p) {
  if (!confirm(`删除 ${p.pid.toUpperCase()}？压缩文件、原始副本与记录将一并删除，不可恢复。`)) return
  try {
    const r = await api.delete(`/api/photos/${p.id}`)
    say(r.warning || `${r.deleted} 已删除`)
    await load()
  } catch (e) {
    say(e.message, true)
  }
}

// Ctrl+V 粘贴图片到页面任意位置
function onPaste(e) {
  const files = e.clipboardData?.files
  if (files?.length) addFiles(files)
}

function onKey(e) {
  if (e.key === 'Escape' && previewPid.value) previewPid.value = null
}

onMounted(async () => {
  await load()
  window.addEventListener('keydown', onKey)
  window.addEventListener('paste', onPaste)
})
onUnmounted(() => {
  window.removeEventListener('keydown', onKey)
  window.removeEventListener('paste', onPaste)
})
</script>

<style lang="scss" scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 20px;
  margin-bottom: 14px;
}
.kicker {
  font: 10px var(--font-mono);
  letter-spacing: 0.15em;
  color: var(--c-blue);
  margin-bottom: 10px;
}
h1 {
  font-size: 22px;
  font-weight: 600;
}
.dropzone {
  border: 1px dashed var(--c-blue-hover);
  padding: 22px 16px;
  text-align: center;
  cursor: pointer;
  margin-bottom: 14px;
  transition: 0.15s;

  &:hover,
  &.over {
    border-color: var(--c-blue);
    background: var(--c-blue-faint);
  }
  .dz-main {
    font: 13px var(--font-sans);
    color: var(--c-ink);
  }
  .dz-sub {
    font: 10px var(--font-mono);
    letter-spacing: 0.06em;
    color: var(--c-muted);
    margin-top: 6px;
  }
}
.queue {
  border: 1px solid var(--c-line);
  padding: 12px 14px;
  margin-bottom: 14px;

  .queue-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;

    button {
      border: 1px solid var(--c-blue);
      background: var(--c-blue);
      color: #fff;
      padding: 8px 16px;
      font: 10px var(--font-mono);
      letter-spacing: 0.12em;
      cursor: pointer;

      &:hover:not(:disabled) {
        box-shadow: 4px 4px 0 var(--c-blue-shadow);
        transform: translateY(-2px);
      }
      &:disabled {
        opacity: 0.6;
        cursor: default;
      }
    }
  }
  .queue-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 12px;
    margin-top: 10px;
  }
  .qcard {
    position: relative;
    border: 1px solid var(--c-line-blue);
    padding: 8px;
    display: grid;
    gap: 6px;
    align-content: start;

    &.uploading {
      opacity: 0.6;
    }
    &.done {
      border-color: var(--c-blue);
    }
    &.fail {
      border-color: var(--c-danger);
    }
    img {
      width: 100%;
      height: 110px;
      object-fit: cover;
      background: var(--c-bg-soft);
    }
    select,
    input {
      border: 1px solid var(--c-line);
      font: 11px var(--font-sans);
      padding: 4px 6px;
      min-width: 0;
    }
    .q-status {
      display: flex;
      gap: 6px;
      align-items: center;
      min-height: 20px;

      .chip,
      .ok,
      .err {
        font: 10px var(--font-mono);
        letter-spacing: 0.04em;
      }
      .chip {
        color: var(--c-muted);
        &.dim {
          opacity: 0.7;
        }
      }
      .ok {
        color: var(--c-blue);
      }
      .err {
        color: var(--c-danger);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        flex: 1;
      }
    }
    .q-remove {
      position: absolute;
      top: 4px;
      right: 4px;
      border: 1px solid var(--c-line);
      background: rgba(255, 255, 255, 0.92);
      color: var(--c-ink-soft);
      width: 20px;
      height: 20px;
      line-height: 1;
      cursor: pointer;

      &:hover {
        border-color: var(--c-danger);
        color: var(--c-danger);
      }
    }
  }
}
.msg {
  font: 11px var(--font-mono);
  letter-spacing: 0.06em;
  color: var(--c-blue-mid);
  border-left: 2px solid var(--c-blue);
  padding: 6px 12px;
  margin: 0 0 14px;
  background: var(--c-blue-faint);

  &.err {
    color: var(--c-danger);
    border-left-color: var(--c-danger);
    background: #fbf1f1;
  }
}
.group {
  margin-bottom: 30px;
}
h2 {
  font: 11px var(--font-mono);
  letter-spacing: 0.14em;
  color: var(--c-ink-soft);
  border-bottom: 1px solid var(--c-line);
  padding-bottom: 8px;
  margin: 0 0 14px;
  display: flex;
  justify-content: space-between;

  .count {
    color: var(--c-muted);
    font-weight: 400;
  }
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 14px;
}
.card {
  margin: 0;
  border: 1px solid var(--c-line-blue);
  background: var(--c-bg);
  transition: 0.15s;
  position: relative;

  &:hover {
    border-color: var(--c-blue-hover);
    box-shadow: 4px 4px 0 var(--c-blue-shadow-soft);
  }
  &.dragging {
    opacity: 0.4;
  }
  &.hero {
    border-color: var(--c-blue);
    box-shadow: 0 0 0 1px var(--c-blue);
  }
  .handle {
    position: absolute;
    top: 6px;
    left: 6px;
    z-index: 2;
    background: rgba(255, 255, 255, 0.9);
    color: var(--c-ink-soft);
    font-size: 12px;
    line-height: 1;
    padding: 3px 5px;
    cursor: grab;
    border: 1px solid var(--c-line);

    &:active {
      cursor: grabbing;
    }
  }
  .hero-badge {
    position: absolute;
    top: 6px;
    right: 6px;
    z-index: 2;
    background: var(--c-blue);
    color: #fff;
    font: 9px var(--font-mono);
    letter-spacing: 0.1em;
    padding: 3px 8px;
  }
  img {
    display: block;
    width: 100%;
    aspect-ratio: 4 / 3;
    object-fit: cover;
    background: var(--c-bg-soft);
    cursor: zoom-in;
  }
  figcaption {
    display: grid;
    gap: 2px;
    padding: 8px 10px 4px;

    .pid {
      font: 10px var(--font-mono);
      letter-spacing: 0.12em;
      color: var(--c-blue);
    }
    .meta {
      font: 10px var(--font-mono);
      letter-spacing: 0.04em;
      color: var(--c-muted);
    }
    .cap {
      font: 10px var(--font-sans);
      color: var(--c-ink-soft);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
  .ops,
  .edit {
    display: flex;
    gap: 6px;
    padding: 6px 10px 10px;
    align-items: center;
  }
  .edit {
    flex-wrap: wrap;

    input,
    select {
      border: 1px solid var(--c-line);
      font: 11px var(--font-sans);
      padding: 4px 6px;
      min-width: 0;
    }
    .cap-input {
      flex: 1;
      min-width: 110px;
    }
  }
}
.lightbox {
  position: fixed;
  inset: 0;
  z-index: 60;
  background: rgba(32, 35, 39, 0.85);
  display: grid;
  place-items: center;

  img {
    max-width: 92vw;
    max-height: 86vh;
    border: 1px solid var(--c-line);
  }
  .lb-close {
    position: absolute;
    top: 18px;
    right: 24px;
    background: none;
    border: none;
    color: #fff;
    font-size: 30px;
    cursor: pointer;
  }
  .lb-cap {
    position: absolute;
    bottom: 18px;
    color: #fff;
    font: 11px var(--font-mono);
    letter-spacing: 0.14em;
  }
}
.mini {
  border: 1px solid var(--c-line);
  background: var(--c-bg);
  color: var(--c-ink-soft);
  font: 10px var(--font-mono);
  letter-spacing: 0.08em;
  padding: 5px 9px;
  cursor: pointer;

  &:hover {
    border-color: var(--c-blue);
    color: var(--c-blue);
  }
  &.danger:hover {
    border-color: var(--c-danger);
    color: var(--c-danger);
  }
}
</style>
