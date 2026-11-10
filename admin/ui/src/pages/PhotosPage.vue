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

    <!-- 分组管理：可增删、改名、改描述、排序；发布后同步到网站摄影页 -->
    <details class="group-admin">
      <summary>
        分组管理 · {{ groups.length }} 组
        <span>展示名 / 描述 / 顺序都会同步到网站摄影页；非空分组需先移走照片才能删除</span>
      </summary>
      <div class="ga-body">
        <div v-for="(g, i) in groups" :key="g.id" class="ga-row">
          <template v-if="gaEditId === g.id">
            <span class="ga-key">{{ g.groupKey }}</span>
            <input v-model="gaName" placeholder="显示名" />
            <input v-model="gaDesc" placeholder="分组描述" />
            <button class="mini" @click="saveGroup(g)">保存</button>
            <button class="mini" @click="gaEditId = null">取消</button>
          </template>
          <template v-else>
            <span class="ga-key">{{ g.groupKey }}</span>
            <span class="ga-name">{{ g.name }}</span>
            <span class="ga-desc">{{ g.description || '—' }}</span>
            <span class="ga-count">{{ g.photos.length }} 张</span>
            <button class="mini" :disabled="i === 0" title="上移" @click="moveGroup(i, -1)">↑</button>
            <button class="mini" :disabled="i === groups.length - 1" title="下移" @click="moveGroup(i, 1)">↓</button>
            <button class="mini" @click="startGroupEdit(g)">编辑</button>
            <button class="mini danger" @click="removeGroup(g)">删除</button>
          </template>
        </div>
        <div class="ga-row ga-new">
          <input v-model="newKey" placeholder="KEY（大写）" class="ga-key" />
          <input v-model="newName" placeholder="显示名，如 PORTRAIT / 人像" />
          <input v-model="newDesc" placeholder="分组描述（可空）" />
          <button class="mini" :disabled="!newKey || !newName" @click="createGroup">新建分组</button>
        </div>
      </div>
    </details>

    <!-- 待上传队列：逐张可改分组/日期 -->
    <div v-if="queue.length" class="queue">
      <div class="queue-head">
        <div class="kicker">待上传 {{ queue.length }} 张 — 逐张可改分组 / 日期（日期留空 = 自动识别）</div>
        <div class="bulk">
          <span>全部应用为</span>
          <select v-model="bulkGroup" @change="applyBulkGroup">
            <option v-for="g in groups" :key="g.key" :value="g.key">{{ g.name }}</option>
          </select>
          <button :disabled="uploading || !queue.some((i) => i.status === 'pending' || i.status === 'fail')" @click="doUpload">
            {{ uploading ? '上传中…' : `上传 ${queue.filter((i) => i.status !== 'done').length} 张` }}
          </button>
        </div>
      </div>
      <div class="queue-grid">
        <div v-for="(it, idx) in queue" :key="it.uid" class="qcard" :class="it.status">
          <img :src="it.url" :alt="it.name" />
          <select v-model="it.group" :disabled="it.status === 'uploading' || it.status === 'done'">
            <option v-for="g in groups" :key="g.key" :value="g.key">{{ g.key }}</option>
          </select>
          <select v-model="it.source" :disabled="it.status === 'uploading' || it.status === 'done'" @change="onSourceChange(it)">
            <option value="auto">自动识别</option>
            <option value="exif" :disabled="!it.exifDate">EXIF 拍摄{{ it.exifDate ? '' : '（无）' }}</option>
            <option value="mtime">文件时间</option>
            <option value="upload">上传日期</option>
            <option value="manual">手填</option>
          </select>
          <input v-model="it.date" type="date" :disabled="it.status === 'uploading' || it.status === 'done'" @input="onDateInput(it)" />
          <div class="q-status">
            <template v-if="it.status === 'done'">
              <span class="ok">✓ {{ SOURCE_LABELS[it.result?.dateSource] || '已识别' }} · {{ it.result?.shotDate || '—' }}</span>
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

    <p v-if="publishDirty" class="pub-dirty">
      ● 有未发布变更——上传/排序只写数据库，点「04 发布」才会更新到公网网站（约 1.5–3 分钟生效）。
    </p>

    <section v-for="g in groups" :key="g.key" class="group">
      <h2>
        {{ g.name }}
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
            <span class="meta">{{ p.width }}×{{ p.height }} · {{ p.shotDate || '日期—' }}<template v-if="p.dateSource"> · {{ SOURCE_LABELS[p.dateSource] || p.dateSource }}</template></span>
            <span v-if="p.caption" class="cap" :title="p.caption">{{ p.caption }}</span>
          </figcaption>

          <div v-if="editId !== p.id" class="ops">
            <button v-if="!p.isHero" class="mini" @click="setHero(p)" title="设为摄影页页头大图（当前头图会自动取消）">设头图</button>
            <button class="mini" @click="startEdit(p)">编辑</button>
            <button class="mini danger" @click="remove(p)">删除</button>
          </div>

          <div v-else class="edit">
            <select v-model="editSource" @change="onEditSourceChange(p)">
              <option v-if="p.dateSource === 'legacy'" value="legacy">原记录（未标注）</option>
              <option value="exif" :disabled="!p.exifDate">EXIF 拍摄</option>
              <option value="mtime" :disabled="!p.fileDate">文件时间</option>
              <option value="upload" :disabled="!p.uploadDate">上传日期</option>
              <option value="manual">手填</option>
            </select>
            <input v-model="editDate" type="date" :disabled="editSource !== 'manual'" />
            <select v-model="editGroup">
              <option v-for="gd in groups" :key="gd.key" :value="gd.key">{{ gd.key }}</option>
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

const SOURCE_LABELS = {
  auto: '自动识别',
  exif: 'EXIF 拍摄',
  mtime: '文件时间',
  upload: '上传日期',
  manual: '手填',
  legacy: '原记录（未标注）',
}

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
const editSource = ref('')
const previewPid = ref(null)

// 分组管理表单
const gaEditId = ref(null)
const gaName = ref('')
const gaDesc = ref('')
const newKey = ref('')
const newName = ref('')
const newDesc = ref('')

let uidSeq = 0

const pendingCount = computed(() => queue.value.filter((i) => i.status !== 'done').length)

// 批量分组：一次把所有待上传项设为同一组（逐张仍可单独改）
const bulkGroup = ref('STREET')
function applyBulkGroup() {
  queue.value.forEach((i) => {
    if (i.status === 'pending' || i.status === 'fail') i.group = bulkGroup.value
  })
}

async function load() {
  const [photos, defs] = await Promise.all([
    api.get('/api/photos'),
    api.get('/api/photo-groups'),
  ])
  groups.value = defs.map((d) => ({
    ...d,
    key: d.groupKey,
    photos: photos.filter((p) => p.groupName === d.groupKey),
  }))
  if (!groups.value.some((g) => g.key === bulkGroup.value)) {
    bulkGroup.value = groups.value[0]?.key || ''
  }
}

// ---- 分组管理（发布后才会同步到网站） ----

function startGroupEdit(g) {
  gaEditId.value = g.id
  gaName.value = g.name
  gaDesc.value = g.description || ''
}

async function saveGroup(g) {
  try {
    await api.put(`/api/photo-groups/${g.id}`, { name: gaName.value, description: gaDesc.value || null })
    gaEditId.value = null
    say(`分组「${g.groupKey}」已更新，点「04 发布」后同步到网站`)
    await load()
    await checkPublishDirty()
  } catch (e) {
    say(e.message, true)
  }
}

async function createGroup() {
  try {
    await api.post('/api/photo-groups', {
      groupKey: newKey.value,
      name: newName.value,
      description: newDesc.value || null,
    })
    newKey.value = newName.value = newDesc.value = ''
    say('分组已创建，可在上传队列里选用；发布后同步到网站')
    await load()
    await checkPublishDirty()
  } catch (e) {
    say(e.message, true)
  }
}

async function removeGroup(g) {
  if (!confirm(`删除分组「${g.name}」？组内必须已清空照片。`)) return
  try {
    await api.delete(`/api/photo-groups/${g.id}`)
    say(`分组「${g.groupKey}」已删除`)
    await load()
    await checkPublishDirty()
  } catch (e) {
    say(e.message, true)
  }
}

async function moveGroup(i, delta) {
  const arr = groups.value.slice()
  const j = i + delta
  if (j < 0 || j >= arr.length) return
  ;[arr[i], arr[j]] = [arr[j], arr[i]]
  try {
    await api.put('/api/photo-groups/reorder', arr.map((g) => g.id))
    await load()
    say('分组顺序已保存，发布后同步到网站')
    await checkPublishDirty()
  } catch (e) {
    say(e.message, true)
  }
}

function say(text, isErr = false) {
  msg.value = text
  msgIsErr.value = isErr
}

// 未发布变更检测：上传/排序/编辑落库后横幅提示去发布
const publishDirty = ref(false)
async function checkPublishDirty() {
  try {
    publishDirty.value = (await api.get('/api/publish/dirty')).dirty
  } catch {
    publishDirty.value = false
  }
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
      group: bulkGroup.value || groups.value[0]?.key || '',
      date: '',
      source: 'auto',
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
  await checkPublishDirty()
}

async function uploadOne(it) {
  it.status = 'uploading'
  it.error = ''
  try {
    const fd = new FormData()
    fd.append('file', it.file)
    fd.append('groupName', it.group)
    fd.append('dateSource', it.source)
    if (it.date && (it.touched || it.source === 'manual')) fd.append('shotDate', it.date)
    const r = await api.postForm('/api/photos/upload', fd)
    it.result = r
    it.status = 'done'
    // 自动识别时用后端权威结果回填日期（来源标注一并返回）
    if (!it.touched && it.source === 'auto') it.date = r.shotDate || ''
  } catch (e) {
    it.status = 'fail'
    it.error = e.message
  }
}

/** 手动改日期 = 切到「手填」，避免用户以为改的是别的来源的值 */
function onDateInput(it) {
  it.touched = true
  it.source = 'manual'
}

/** 切回自动/其它来源时，之前的手填值不再随表单提交 */
function onSourceChange(it) {
  if (it.source !== 'manual') it.touched = false
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
    await checkPublishDirty()
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
  editSource.value = p.dateSource || 'legacy'
}

/** 切换来源时把日期框填成该来源的候选值；只有「手填」允许继续编辑 */
function onEditSourceChange(p) {
  const candidates = {
    exif: p.exifDate,
    mtime: p.fileDate,
    upload: p.uploadDate,
    manual: p.manualDate,
    legacy: p.shotDate,
  }
  editDate.value = candidates[editSource.value] || ''
}

async function saveEdit(p) {
  try {
    await api.put(`/api/photos/${p.id}`, {
      groupName: editGroup.value,
      shotDate: editDate.value || null,
      dateSource: editSource.value,
      sortNo: p.sortNo,
      caption: editCaption.value || null,
    })
    editId.value = null
    say(`${p.pid} 已更新`)
    await load()
    await checkPublishDirty()
  } catch (e) {
    say(e.message, true)
  }
}

async function setHero(p) {
  try {
    await api.put(`/api/photos/${p.id}/hero`)
    say(`${p.pid} 已设为头图（原头图已取消）`)
    await load()
    await checkPublishDirty()
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
    await checkPublishDirty()
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
  await checkPublishDirty()
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
.group-admin {
  border: 1px solid var(--c-line);
  padding: 10px 14px;
  margin-bottom: 14px;

  summary {
    cursor: pointer;
    font: 11px var(--font-mono);
    letter-spacing: 0.08em;
    color: var(--c-blue);

    span {
      color: var(--c-muted);
      margin-left: 10px;
      letter-spacing: 0.02em;
    }
  }
  .ga-body {
    margin-top: 12px;
    display: grid;
    gap: 8px;
  }
  .ga-row {
    display: grid;
    grid-template-columns: 90px minmax(140px, 1.1fr) minmax(160px, 1.6fr) 54px repeat(4, auto);
    gap: 8px;
    align-items: center;
    font: 11px var(--font-sans);
    color: var(--c-ink-soft);

    .ga-key {
      font: 10px var(--font-mono);
      color: var(--c-blue);
      letter-spacing: 0.08em;
    }
    .ga-desc,
    .ga-count {
      color: var(--c-muted);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    input {
      border: 1px solid var(--c-line);
      font: 11px var(--font-sans);
      padding: 5px 7px;
      min-width: 0;
    }
    .mini {
      padding: 4px 8px;
      &:disabled {
        opacity: 0.35;
        cursor: default;
      }
    }
    &.ga-new {
      border-top: 1px dashed var(--c-line);
      padding-top: 10px;
      margin-top: 2px;
    }
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

    .bulk {
      display: flex;
      gap: 8px;
      align-items: center;

      span {
        font: 10px var(--font-mono);
        color: var(--c-muted);
        white-space: nowrap;
      }
      select {
        border: 1px solid var(--c-line);
        font: 11px var(--font-sans);
        padding: 5px 8px;
      }
    }
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
.pub-dirty {
  font: 11px var(--font-mono);
  color: var(--c-ink-soft);
  border-left: 2px solid var(--c-aux);
  padding: 6px 12px;
  margin: 0 0 14px;
  background: var(--c-blue-faint);
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
