<template>
  <div>
    <div class="head">
      <div>
        <div class="kicker">03 / CONTENT</div>
        <h1>项目内容</h1>
      </div>
      <div class="actions">
        <span v-if="dirty" class="dirty-tag">● 未保存</span>
        <button class="mini" @click="preview" title="打开公网项目页（显示的是已发布内容，未发布的改动发布后生效）">预览 ↗</button>
        <button class="primary" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
      </div>
    </div>

    <nav class="sec-nav">
      <span class="proj">
        <select v-model="slug" @change="load">
          <option v-for="p in projects" :key="p.slug" :value="p.slug">{{ p.slug }}{{ p.hidden ? '（已隐藏）' : '' }}</option>
        </select>
      </span>
      <button class="mini" @click="createProject" title="新建后会出现在项目下拉中，发布时自动上公网">+ 新建</button>
      <button class="mini" @click="toggleHidden" :title="currentHidden ? '当前已隐藏：前台卡片与详情页均不展示（留库）；点击恢复' : '点击后前台卡片与详情页均不展示（留库）'">{{ currentHidden ? '显示' : '隐藏' }}</button>
      <button class="mini danger" @click="deleteProject" title="删除详情数据；下次发布后前台卡片同步移除">删除项目</button>
      <span class="sep">|</span>
      <a v-for="(s, i) in form.sections" :key="i" @click="scrollToSec(i)">{{ padNo(i) }} {{ kickerTitle(s).slice(0, 10) }}</a>
      <button class="mini" @click="scrollToSec(-1)" title="页面顶部">↑ 顶部</button>
    </nav>

    <p v-if="msg" class="msg" :class="{ err: msgIsErr }">{{ msg }}</p>

    <p v-if="publishDirty && !dirty" class="pub-dirty">
      ● 有未发布变更——保存只写数据库，点「04 发布」才会更新到公网网站（约 1.5–3 分钟生效）。
    </p>

    <section class="panel">
      <h2>基本信息 — 01 / OVERVIEW</h2>
      <label class="field">
        <span>headline</span>
        <input v-model="form.headline" />
      </label>
      <label class="field">
        <span>summary</span>
        <textarea v-model="form.summary" rows="4"></textarea>
      </label>
      <label class="field">
        <span>头注释（证据基线 / 诚实口径，导出时原样带回）</span>
        <textarea v-model="form.headerNote" rows="3"></textarea>
      </label>
    </section>

    <section class="panel">
      <h2>META — FILE / META 侧栏</h2>
      <div v-for="(row, i) in form.meta" :key="i" class="meta-row">
        <input v-model="row[0]" placeholder="标签" class="k" />
        <input v-model="row[1]" placeholder="内容" />
        <button class="mini danger" @click="form.meta.splice(i, 1)">删</button>
      </div>
      <button class="mini" @click="form.meta.push(['', ''])">+ 加一行</button>
    </section>

    <section v-for="(s, i) in form.sections" :key="i" class="panel sec">
      <div class="sec-head">
        <span class="no">{{ padNo(i) }} /</span>
        <input
          class="sec-title"
          :value="kickerTitle(s)"
          @input="s.kicker = padNo(i) + ' / ' + $event.target.value"
        />
        <span class="type-tag">{{ TYPE_LABEL[s.type] || s.type }}</span>
        <button class="mini" :disabled="i === 0" @click="move(i, -1)">↑</button>
        <button class="mini" :disabled="i === form.sections.length - 1" @click="move(i, 1)">↓</button>
        <button v-if="s.type !== 'arch'" class="mini danger" @click="removeSection(i)">删</button>
      </div>

      <!-- arch：SVG 组件只读 -->
      <div v-if="s.type === 'arch'" class="readonly">
        架构图是代码 SVG 组件（{{ s.component }}），不开放编辑；如需调整请改 src/components/project/ 下的组件。
      </div>

      <!-- text -->
      <template v-else-if="s.type === 'text'">
        <textarea v-model="s.body" rows="5"></textarea>
      </template>

      <!-- impl -->
      <template v-else-if="s.type === 'impl'">
        <label class="field row">
          <span>三段式标签（以 / 分隔，默认 问题/方案/效果）</span>
          <input :value="(s.labels || []).join(' / ')" @input="setLabels(s, $event.target.value)" />
        </label>
        <div v-for="(it, j) in s.items" :key="j" class="item">
          <div class="item-head">
            <input v-model="it.title" placeholder="条目标题" />
            <button class="mini danger" @click="s.items.splice(j, 1)">删</button>
          </div>
          <textarea v-model="it.problem" rows="2" placeholder="问题"></textarea>
          <textarea v-model="it.solution" rows="3" placeholder="方案"></textarea>
          <textarea v-model="it.result" rows="2" placeholder="效果"></textarea>
        </div>
        <button class="mini" @click="s.items.push({ title: '', problem: '', solution: '', result: '' })">
          + 加一条
        </button>
      </template>

      <!-- metrics -->
      <template v-else-if="s.type === 'metrics'">
        <div v-for="(it, j) in s.items" :key="j" class="metric-row">
          <input v-model="it.label" placeholder="指标名" class="k" />
          <input v-model="it.value" placeholder="数值（如 1.468s → 0.148s）" />
          <input v-model="it.delta" placeholder="变化（可空）" class="delta" />
          <button class="mini danger" @click="s.items.splice(j, 1)">删</button>
        </div>
        <button class="mini" @click="s.items.push({ label: '', value: '' })">+ 加指标</button>
        <textarea v-model="s.note" rows="3" placeholder="脚注（测试环境说明 / 出处）"></textarea>
      </template>

      <!-- pits -->
      <template v-else-if="s.type === 'pits'">
        <div v-for="(it, j) in s.items" :key="j" class="item">
          <div class="item-head">
            <input v-model="it.title" placeholder="坑标题" />
            <button class="mini danger" @click="s.items.splice(j, 1)">删</button>
          </div>
          <textarea v-model="it.body" rows="4" placeholder="经过与解决"></textarea>
        </div>
        <button class="mini" @click="s.items.push({ title: '', body: '' })">+ 加一条</button>
      </template>

      <!-- shots -->
      <template v-else-if="s.type === 'shots'">
        <label class="check">
          <input v-model="s.pending" type="checkbox" />
          pending（截图收集占位，前台显示虚线框）
        </label>
        <template v-if="s.pending">
          <label class="field">
            <span>wanted 清单（每行一条）</span>
            <textarea :value="(s.wanted || []).join('\n')" rows="4" @input="setLines(s, 'wanted', $event)"></textarea>
          </label>
        </template>
        <template v-else>
          <div v-for="(it, j) in s.items" :key="j" class="shot-row">
            <input v-model="it.src" :list="`shots-${slug}`" placeholder="src（截图文件名或路径）" />
            <input v-model="it.caption" placeholder="图注" />
            <button class="mini danger" @click="s.items.splice(j, 1)">删</button>
          </div>
          <datalist :id="`shots-${slug}`">
            <option v-for="f in screenshots" :key="f" :value="f"></option>
          </datalist>
          <button class="mini" @click="s.items.push({ src: '', caption: '' })">+ 加截图</button>
        </template>
        <textarea v-model="s.note" rows="2" placeholder="note（如打码提醒）"></textarea>
      </template>
    </section>

    <div class="add-sec">
      <span>新增节：</span>
      <select v-model="newType">
        <option v-for="(desc, t) in TYPE_DESC" :key="t" :value="t">{{ desc }}</option>
      </select>
      <button class="mini" @click="addSection">+ 添加</button>
    </div>
    <p class="add-sec-guide">
      「01 概述」由页面自动渲染（headline + summary），不需要新增；新增节的编号自动顺延（02 起）并按位置重排，标题只需填文字。
      想让数据上首页卡片：加「数据指标」节，前 3 条会自动进 Works 卡片。
    </p>

    <!-- 新建项目模态框 -->
    <div v-if="creating" class="modal" @click.self="creating = false">
      <div class="box create">
        <h2>新建项目</h2>
        <label class="field">
          <span>slug（路由标识，小写字母/数字/连字符，创建后不可改）</span>
          <input v-model="newSlug" placeholder="如 my-project" spellcheck="false" />
        </label>
        <label class="field">
          <span>项目名称（首页卡片与详情页大标题）</span>
          <input v-model="newHeadline" placeholder="如 My Project — 一句话定位" />
        </label>
        <label class="field">
          <span>一句话简介（首页卡片描述 + 详情页概述）</span>
          <textarea v-model="newSummary" rows="3" placeholder="它是什么、你做了什么、亮点是什么"></textarea>
        </label>
        <p class="hint">
          创建后是空骨架：在下方「+ 添加」节填充正文；「04 发布」后卡片与详情页自动上公网（详情页正文由发布自动写回，无需改代码）。
        </p>
        <p v-if="createErr" class="err">{{ createErr }}</p>
        <div class="modal-ops">
          <button class="primary" :disabled="creatingBusy" @click="submitCreate">
            {{ creatingBusy ? '创建中…' : '创建' }}
          </button>
          <button class="mini" @click="creating = false">取消</button>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup>
import { onMounted, ref, watch, computed } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { api } from '../api.js'

const EDITABLE_TYPES = ['shots', 'impl', 'metrics', 'pits', 'text']

// 类型中文化（任务 F）：下拉与节标签可读，说明讲清各类型用途
const TYPE_LABEL = { arch: '架构图', shots: '界面截图', impl: '核心实现', metrics: '数据指标', pits: '踩坑复盘', text: '正文段落' }
const TYPE_DESC = {
  shots: '界面截图 — 上传截图，或先列 wanted 占位待补',
  impl: '核心实现 — 问题 / 方案 / 效果 三段式条目',
  metrics: '数据指标 — 数值 + 变化（前 3 条自动进首页卡片）',
  pits: '踩坑复盘 — 坑标题 + 经过与解决',
  text: '正文段落 — 纯文本（换行会保留）',
}

const projects = ref([])
const slug = ref('')
const form = ref({ headline: '', summary: '', headerNote: '', meta: [], sections: [] })
const screenshots = ref([])
const saving = ref(false)
const msg = ref('')
const msgIsErr = ref(false)
const dirty = ref(false)
const newType = ref('text')
// 新建项目模态框（任务 F：替代 3 连原生 prompt）
const creating = ref(false)
const creatingBusy = ref(false)
const newSlug = ref('')
const newHeadline = ref('')
const newSummary = ref('')
const createErr = ref('')

// 未保存跟踪：任何编辑置 dirty；保存/切换成功后清除
watch(form, () => { dirty.value = true }, { deep: true })

window.addEventListener('beforeunload', (e) => {
  if (dirty.value) { e.preventDefault(); e.returnValue = '' }
})

onBeforeRouteLeave(() => {
  if (dirty.value && !confirm('有未保存的修改，确定离开？')) return false
  dirty.value = false
})

function scrollToSec(i) {
  if (i < 0) { window.scrollTo({ top: 0, behavior: 'smooth' }); return }
  const el = document.querySelectorAll('section.panel.sec')[i]
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function createProject() {
  newSlug.value = newHeadline.value = newSummary.value = ''
  createErr.value = ''
  creating.value = true
}

async function submitCreate() {
  const s = newSlug.value.trim()
  const headline = newHeadline.value.trim()
  const summary = newSummary.value.trim()
  createErr.value = ''
  if (!/^[a-z0-9][a-z0-9-]{1,40}$/.test(s)) {
    createErr.value = 'slug 需为 2-41 位小写字母/数字/连字符（字母开头），如 my-project'
    return
  }
  if (!headline || !summary) {
    createErr.value = '项目名称与一句话简介必填'
    return
  }
  creatingBusy.value = true
  try {
    await api.post('/api/content/projects', { slug: s, headline, summary })
    projects.value = await api.get('/api/content/projects')
    slug.value = s
    creating.value = false
    await load()
    say(`项目 ${s} 已创建（空骨架）。填好内容后点「04 发布」即上公网首页卡片；详情页正文随发布自动渲染。`)
    await checkPublishDirty()
  } catch (e) {
    createErr.value = e.message
  } finally {
    creatingBusy.value = false
  }
}

// 公网预览：打开项目详情页（显示已发布内容；未发布改动需先发布）
function preview() {
  window.open(`https://yuanyyyyyyyyy.github.io/rain/projects/${slug.value}`, '_blank', 'noopener')
}

async function deleteProject() {
  if (!confirm(`删除项目 ${slug.value}？详情数据将删除；下次发布后前台卡片同步移除。`)) return
  try {
    await api.delete(`/api/content/projects/${slug.value}`)
    projects.value = await api.get('/api/content/projects')
    slug.value = projects.value[0]?.slug || ''
    await load()
    say(`项目已删除。点「04 发布」后公网卡片同步移除。`)
  } catch (e) {
    say(e.message, true)
  }
}

const currentHidden = computed(() => projects.value.find((p) => p.slug === slug.value)?.hidden === true)

async function toggleHidden() {
  try {
    const next = !currentHidden.value
    await api.put(`/api/content/projects/${slug.value}/hidden`, { hidden: next })
    const item = projects.value.find((p) => p.slug === slug.value)
    if (item) item.hidden = next
    await checkPublishDirty()
    say(next ? `「${slug.value}」已隐藏（前台卡片与详情页均不展示，数据仍保留）；点「04 发布」生效。` : `「${slug.value}」已恢复展示；点「04 发布」生效。`)
  } catch (e) {
    say(e.message, true)
  }
}

function say(text, isErr = false) {
  msg.value = text
  msgIsErr.value = isErr
}

async function load() {
  const d = await api.get(`/api/content/projects/${slug.value}`)
  form.value = {
    headline: d.headline,
    summary: d.summary,
    headerNote: d.headerNote || '',
    meta: d.meta.map((r) => [...r]),
    sections: d.sections,
  }
  screenshots.value = await api.get(`/api/content/screenshots/${slug.value}`)
  dirty.value = false
}

const padNo = (i) => String(i + 2).padStart(2, '0')
const kickerTitle = (s) => {
  const k = s.kicker || ''
  const idx = k.indexOf(' / ')
  return idx >= 0 ? k.slice(idx + 3) : k
}
const setLabels = (s, text) => {
  const parts = text.split('/').map((x) => x.trim()).filter(Boolean)
  s.labels = parts.length ? parts : undefined
}
const setLines = (s, field, e) => {
  const lines = e.target.value.split('\n').filter((x) => x.trim() !== '')
  if (lines.length) s[field] = lines
  else delete s[field]
}

function move(i, dir) {
  const arr = form.value.sections
  const t = arr[i]
  arr[i] = arr[i + dir]
  arr[i + dir] = t
}

function removeSection(i) {
  const s = form.value.sections[i]
  if (confirm(`删除第 ${padNo(i)} 节（${kickerTitle(s)}）？保存后生效。`)) {
    form.value.sections.splice(i, 1)
  }
}

function addSection() {
  const base = { type: newType.value, kicker: `${padNo(form.value.sections.length)} / 新节标题` }
  if (newType.value === 'text') base.body = ''
  if (newType.value === 'impl') base.items = [{ title: '', problem: '', solution: '', result: '' }]
  if (newType.value === 'metrics') base.items = [{ label: '', value: '' }]
  if (newType.value === 'pits') base.items = [{ title: '', body: '' }]
  if (newType.value === 'shots') base.pending = true
  form.value.sections.push(base)
  say('已添加新节（编号将按位置自动重排），填写后记得保存')
}

async function save() {
  saving.value = true
  try {
    await api.put(`/api/content/projects/${slug.value}`, form.value)
    dirty.value = false
    say('已保存。公网生效：点「04 发布」。')
    await checkPublishDirty()
  } catch (e) {
    say(e.message, true)
  } finally {
    saving.value = false
  }
}

// 未发布变更检测（保存落库 ≠ 公网生效）：有变更时页头横幅提示去发布
const publishDirty = ref(false)
async function checkPublishDirty() {
  try {
    publishDirty.value = (await api.get('/api/publish/dirty')).dirty
  } catch {
    publishDirty.value = false
  }
}

onMounted(async () => {
  projects.value = await api.get('/api/content/projects')
  slug.value = projects.value[0]?.slug || ''
  await load()
  await checkPublishDirty()
})
</script>

<style lang="scss" scoped>
.sec-nav {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 14px;
  padding: 9px 12px;
  border: 1px solid var(--c-line);
  background: var(--c-blue-faint);
  // 长表单滚动时节锚点导航吸顶（配合 scrollToSec 平滑滚动）
  position: sticky;
  top: 0;
  z-index: 20;

  .proj select {
    width: 130px;
    padding: 6px 8px;
  }
  a {
    font: 10px var(--font-mono);
    letter-spacing: 0.06em;
    color: var(--c-ink-soft);
    cursor: pointer;
    padding: 4px 7px;

    &:hover {
      color: var(--c-blue);
      background: #fff;
    }
  }
  .mini {
    margin-left: auto;
  }
  .sep {
    color: var(--c-line);
  }
  .mini.danger:hover {
    border-color: var(--c-danger);
    color: var(--c-danger);
  }
}
.dirty-tag {
  font: 10px var(--font-mono);
  letter-spacing: 0.08em;
  color: var(--c-danger);
  align-self: center;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 20px;
  margin-bottom: 16px;
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
h2 {
  font: 11px var(--font-mono);
  letter-spacing: 0.14em;
  color: var(--c-ink-soft);
  margin: 0 0 14px;
}
.actions {
  display: flex;
  gap: 8px;
  align-items: center;

  select,
  button {
    border: 1px solid var(--c-line);
    background: var(--c-bg);
    padding: 8px 12px;
    font: 12px var(--font-sans);
  }
  button {
    font: 10px var(--font-mono);
    letter-spacing: 0.1em;
    cursor: pointer;
    white-space: nowrap;

    &:hover {
      border-color: var(--c-blue);
      color: var(--c-blue);
    }
    &.primary {
      background: var(--c-blue);
      border-color: var(--c-blue);
      color: #fff;

      &:hover {
        color: #fff;
        box-shadow: 4px 4px 0 var(--c-blue-shadow);
        transform: translateY(-2px);
      }
      &:disabled {
        opacity: 0.6;
        cursor: default;
        transform: none;
        box-shadow: none;
      }
    }
  }
}
.msg {
  font: 11px var(--font-mono);
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
.pub-dirty {
  font: 11px var(--font-mono);
  color: var(--c-ink-soft);
  border-left: 2px solid var(--c-aux);
  padding: 6px 12px;
  margin: 0 0 14px;
  background: var(--c-blue-faint);
}
.panel {
  border: 1px solid var(--c-line);
  padding: 16px 18px;
  margin-bottom: 16px;
}
.field {
  display: grid;
  gap: 5px;
  margin-bottom: 12px;

  span {
    font: 10px var(--font-mono);
    letter-spacing: 0.08em;
    color: var(--c-muted);
  }
  &.row {
    grid-template-columns: 220px 1fr;
    align-items: center;
  }
}
input,
textarea,
select {
  border: 1px solid var(--c-line);
  padding: 8px 10px;
  font: 12px/1.6 var(--font-sans);
  background: var(--c-bg);
  width: 100%;
  box-sizing: border-box;

  &:focus {
    border-color: var(--c-blue);
    outline: none;
  }
}
textarea {
  resize: vertical;
}
.meta-row {
  display: grid;
  grid-template-columns: 160px 1fr auto;
  gap: 8px;
  margin-bottom: 8px;
}
.sec-head {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;

  .no {
    font: 11px var(--font-mono);
    color: var(--c-blue);
    letter-spacing: 0.1em;
  }
  .sec-title {
    max-width: 340px;
    font: 12px var(--font-sans);
  }
  .type-tag {
    font: 9px var(--font-mono);
    letter-spacing: 0.1em;
    color: var(--c-muted);
    border: 1px solid var(--c-line);
    padding: 3px 7px;
  }
}
.readonly {
  font: 11px var(--font-sans);
  color: var(--c-muted);
  border-left: 2px solid var(--c-line);
  padding: 4px 12px;
}
.item {
  border: 1px solid var(--c-line);
  padding: 10px 12px;
  margin-bottom: 10px;
  display: grid;
  gap: 8px;

  .item-head {
    display: flex;
    gap: 8px;

    input {
      flex: 1;
    }
  }
}
.metric-row {
  display: grid;
  grid-template-columns: 160px 1fr 140px auto;
  gap: 8px;
  margin-bottom: 8px;
}
.shot-row {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 8px;
  margin-bottom: 8px;
}
.check {
  display: flex;
  gap: 8px;
  align-items: center;
  font: 12px var(--font-sans);
  margin-bottom: 10px;

  input {
    width: auto;
  }
}
.add-sec {
  display: flex;
  gap: 8px;
  align-items: center;
  font: 11px var(--font-mono);
  color: var(--c-muted);
  margin: 18px 0 40px;

  select {
    width: 140px;
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
  width: auto;

  &:hover:not(:disabled) {
    border-color: var(--c-blue);
    color: var(--c-blue);
  }
  &:disabled {
    opacity: 0.4;
    cursor: default;
  }
  &.danger:hover {
    border-color: var(--c-danger);
    color: var(--c-danger);
  }
}
.modal {
  position: fixed;
  inset: 0;
  background: rgba(32, 35, 39, 0.45);
  display: grid;
  place-items: center;
  z-index: 50;

  .box {
    width: min(560px, 92vw);
    max-height: 86vh;
    overflow: auto;
    display: grid;
    gap: 12px;
    background: var(--c-bg);
    border: 1px solid var(--c-line);
    padding: 18px 20px;

    h2 {
      font: 11px var(--font-mono);
      letter-spacing: 0.14em;
      color: var(--c-ink-soft);
      margin: 0;
    }
    .hint {
      font: 11px/1.8 var(--font-sans);
      color: var(--c-muted);
      margin: 0;
    }
    .err {
      color: var(--c-danger);
      font-size: 12px;
      margin: 0;
    }
    .modal-ops {
      display: flex;
      gap: 8px;

      .primary {
        padding: 9px 18px;
      }
    }
  }
}
.add-sec-guide {
  font: 10px/1.9 var(--font-mono);
  color: var(--c-muted);
  margin: 10px 0 0;
}
</style>
