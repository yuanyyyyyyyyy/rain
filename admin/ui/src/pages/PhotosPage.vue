<template>
  <div>
    <div class="head">
      <div>
        <div class="kicker">02 / PHOTOS</div>
        <h1>照片管理</h1>
      </div>
      <div class="upload">
        <input ref="fileRef" type="file" accept="image/*" multiple />
        <select v-model="uploadGroup">
          <option v-for="g in GROUP_DEFS" :key="g.key" :value="g.key">{{ g.key }} / {{ g.label }}</option>
        </select>
        <input v-model="uploadDate" type="date" title="拍摄日期（可空）" />
        <button :disabled="uploading || !selectedFiles.length" @click="doUpload">
          {{ uploading ? `上传中 ${uploadProgress}/${selectedFiles.length}…` : `上传${selectedFiles.length > 1 ? ` ${selectedFiles.length} 张` : ''}` }}
        </button>
      </div>
    </div>

    <!-- 上传前预览条 -->
    <div v-if="previews.length" class="preview-strip">
      <div class="kicker">即将上传到 {{ uploadGroup }}（确认后点右上「上传」）</div>
      <div class="strip">
        <img v-for="p in previews" :key="p.url" :src="p.url" :alt="p.name" :title="p.name" />
      </div>
    </div>

    <p v-if="msg" class="msg" :class="{ err: msgIsErr }">{{ msg }}</p>

    <div v-if="lastSnippet" class="snippet">
      <div class="kicker">photos.js 片段（仅当发布未自动写回时才需要手工粘贴，正常直接点「04 发布」即可）</div>
      <pre>{{ lastSnippet }}</pre>
      <button class="mini" @click="copySnippet">复制</button>
    </div>

    <section v-for="g in groups" :key="g.key" class="group">
      <h2>
        {{ g.key }} / {{ g.label }}
        <span class="count">{{ g.photos.length }} 张 — 拖拽卡片排序 · 点击图片看大图</span>
      </h2>
      <div class="grid">
        <figure
          v-for="(p, i) in g.photos"
          :key="p.id"
          class="card"
          :class="{ dragging: isDragging(g.key, i), hero: p.isHero, editing: editId === p.id }"
          draggable="true"
          @dragstart="onDragStart(g.key, i)"
          @dragover.prevent
          @drop="onDrop(g.key, i)"
          @dragend="dragFrom = null"
        >
          <span class="handle" title="拖拽排序">⠿</span>
          <span v-if="p.isHero" class="hero-badge" title="摄影页页头大图">头图</span>
          <img :src="`/photos/${p.pid}.jpg`" :alt="p.pid" loading="lazy" @click="previewPid = p.pid" />
          <figcaption>
            <span class="pid">{{ p.pid.toUpperCase() }}</span>
            <span class="meta">{{ p.width }}×{{ p.height }} · {{ p.shotDate || '日期—' }}</span>
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
import { onMounted, onUnmounted, ref } from 'vue'
import { api } from '../api.js'

const GROUP_DEFS = [
  { key: 'STREET', label: '街道与城市' },
  { key: 'LIGHT', label: '光线与氛围' },
  { key: 'DAILY', label: '日常与人' },
]

const groups = ref([])
const fileRef = ref(null)
const selectedFiles = ref([])
const previews = ref([])
const uploadGroup = ref('STREET')
const uploadDate = ref('')
const uploading = ref(false)
const uploadProgress = ref(0)
const msg = ref('')
const msgIsErr = ref(false)
const lastSnippet = ref('')
const dragFrom = ref(null)
const editId = ref(null)
const editDate = ref('')
const editGroup = ref('')
const previewPid = ref(null)

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

function onFilesChange() {
  selectedFiles.value = [...(fileRef.value?.files || [])]
  previews.value = selectedFiles.value.map((f) => ({ url: URL.createObjectURL(f), name: f.name }))
}

async function doUpload() {
  if (!selectedFiles.value.length) return say('请先选择图片文件', true)
  uploading.value = true
  let ok = 0
  try {
    for (const [i, file] of selectedFiles.value.entries()) {
      uploadProgress.value = i
      const fd = new FormData()
      fd.append('file', file)
      fd.append('groupName', uploadGroup.value)
      if (uploadDate.value) fd.append('shotDate', uploadDate.value)
      const r = await api.postForm('/api/photos/upload', fd)
      lastSnippet.value = r.snippet
      ok++
    }
    say(`${ok} 张已上传并入库。点「04 发布」即可更新到公网。`)
    clearFiles()
    await load()
  } catch (e) {
    say(e.message, true)
  } finally {
    uploading.value = false
  }
}

function clearFiles() {
  previews.value.forEach((p) => URL.revokeObjectURL(p.url))
  previews.value = []
  selectedFiles.value = []
  if (fileRef.value) fileRef.value.value = ''
}

async function copySnippet() {
  await navigator.clipboard.writeText(lastSnippet.value)
  say('片段已复制到剪贴板')
}

// 组内拖拽：drop 后本地重排，再批量写回 sortNo（1 起，全组覆盖）
function onDragStart(groupKey, index) {
  dragFrom.value = { groupKey, index }
}

function isDragging(groupKey, index) {
  return dragFrom.value?.groupKey === groupKey && dragFrom.value?.index === index
}

async function onDrop(groupKey, index) {
  const from = dragFrom.value
  dragFrom.value = null
  if (!from || from.groupKey !== groupKey || from.index === index) return
  const g = groups.value.find((x) => x.key === groupKey)
  const moved = g.photos.splice(from.index, 1)[0]
  g.photos.splice(index, 0, moved)
  try {
    await Promise.all(
      g.photos.map((p, i) =>
        api.put(`/api/photos/${p.id}`, { groupName: groupKey, shotDate: p.shotDate, sortNo: i + 1 }),
      ),
    )
    say(`${groupKey} 组排序已保存`)
  } catch (e) {
    say(`排序保存失败：${e.message}`, true)
    await load()
  }
}

function startEdit(p) {
  editId.value = p.id
  editDate.value = p.shotDate || ''
  editGroup.value = p.groupName
}

async function saveEdit(p) {
  try {
    await api.put(`/api/photos/${p.id}`, {
      groupName: editGroup.value,
      shotDate: editDate.value || null,
      sortNo: p.sortNo,
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

function onKey(e) {
  if (e.key === 'Escape' && previewPid.value) previewPid.value = null
}

onMounted(async () => {
  await load()
  window.addEventListener('keydown', onKey)
})
onUnmounted(() => window.removeEventListener('keydown', onKey))
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
.upload {
  display: flex;
  gap: 8px;
  align-items: center;

  input,
  select {
    border: 1px solid var(--c-line);
    padding: 8px 10px;
    font: 12px var(--font-sans);
    background: var(--c-bg);
  }
  button {
    border: 1px solid var(--c-blue);
    background: var(--c-blue);
    color: #fff;
    padding: 9px 16px;
    font: 10px var(--font-mono);
    letter-spacing: 0.12em;
    white-space: nowrap;
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
.preview-strip {
  border: 1px dashed var(--c-blue-hover);
  padding: 12px 14px;
  margin-bottom: 14px;

  .strip {
    display: flex;
    gap: 8px;
    overflow-x: auto;

    img {
      height: 72px;
      border: 1px solid var(--c-line);
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
.snippet {
  border: 1px solid var(--c-line);
  padding: 12px 14px;
  margin-bottom: 20px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px 12px;
  align-items: center;

  .kicker {
    grid-column: 1 / 3;
    margin-bottom: 4px;
  }
  pre {
    margin: 0;
    font: 12px/1.7 var(--font-mono);
    color: var(--c-ink);
    white-space: pre-wrap;
  }
  .mini {
    align-self: start;
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
