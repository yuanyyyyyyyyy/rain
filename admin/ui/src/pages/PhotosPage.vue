<template>
  <div>
    <div class="head">
      <div>
        <div class="kicker">02 / PHOTOS</div>
        <h1>照片管理</h1>
      </div>
      <div class="upload">
        <input ref="fileRef" type="file" accept="image/*" />
        <select v-model="uploadGroup">
          <option v-for="g in GROUP_DEFS" :key="g.key" :value="g.key">{{ g.key }} / {{ g.label }}</option>
        </select>
        <input v-model="uploadDate" type="date" title="拍摄日期（可空）" />
        <button :disabled="uploading" @click="doUpload">{{ uploading ? '上传中…' : '上传' }}</button>
      </div>
    </div>

    <p v-if="msg" class="msg" :class="{ err: msgIsErr }">{{ msg }}</p>

    <div v-if="lastSnippet" class="snippet">
      <div class="kicker">PHOTOS.JS 片段 — 第 1 行粘进对应组的 plates，第 2 行粘进 dims，然后 npm run build</div>
      <pre>{{ lastSnippet }}</pre>
      <button class="mini" @click="copySnippet">复制</button>
    </div>

    <section v-for="g in groups" :key="g.key" class="group">
      <h2>
        {{ g.key }} / {{ g.label }}
        <span class="count">{{ g.photos.length }} 张 — 组内拖拽排序</span>
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
          <img :src="`/photos/${p.pid}.jpg`" loading="lazy" :alt="p.pid" />
          <figcaption>
            <span class="pid">{{ p.pid.toUpperCase() }}</span>
            <span class="meta">{{ p.width }}×{{ p.height }} · {{ p.shotDate || '日期—' }}</span>
          </figcaption>

          <div v-if="editId !== p.id" class="ops">
            <span v-if="p.isHero" class="hero-badge">头图</span>
            <button v-else class="mini" @click="setHero(p)">设头图</button>
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
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api.js'

const GROUP_DEFS = [
  { key: 'STREET', label: '街道与城市' },
  { key: 'LIGHT', label: '光线与氛围' },
  { key: 'DAILY', label: '日常与人' },
]

const groups = ref([])
const fileRef = ref(null)
const uploadGroup = ref('STREET')
const uploadDate = ref('')
const uploading = ref(false)
const msg = ref('')
const msgIsErr = ref(false)
const lastSnippet = ref('')
const dragFrom = ref(null)
const editId = ref(null)
const editDate = ref('')
const editGroup = ref('')

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

async function doUpload() {
  const file = fileRef.value?.files?.[0]
  if (!file) return say('请先选择图片文件', true)
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('groupName', uploadGroup.value)
    if (uploadDate.value) fd.append('shotDate', uploadDate.value)
    const r = await api.postForm('/api/photos/upload', fd)
    lastSnippet.value = r.snippet
    say(`上传成功：${r.pid}（${r.width}×${r.height}），已归入 ${uploadGroup.value}`)
    fileRef.value.value = ''
    await load()
  } catch (e) {
    say(e.message, true)
  } finally {
    uploading.value = false
  }
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

onMounted(load)
</script>

<style lang="scss" scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 20px;
  margin-bottom: 18px;
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
  img {
    display: block;
    width: 100%;
    aspect-ratio: 4 / 3;
    object-fit: cover;
    background: var(--c-bg-soft);
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
.hero-badge {
  font: 10px var(--font-mono);
  letter-spacing: 0.1em;
  color: #fff;
  background: var(--c-blue);
  padding: 4px 8px;
}
</style>
