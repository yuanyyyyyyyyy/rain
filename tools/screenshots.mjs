#!/usr/bin/env node
/**
 * rain 自动截图管线（任务 G）：Playwright 对构建产物（vite preview）截取项目界面，
 * 产出 public/screenshots/<slug>/<slug>-NN.png，接入仍走管理台（保持 DB 唯一内容源纪律）。
 *
 * 用法：
 *   npm run build          # 先有构建产物（或 --url 指向已起的服务）
 *   npm run shots          # 默认：起 vite preview → 截全部目标 → 退出
 *   npm run shots -- --url http://localhost:4173/rain/   # 复用已起的服务
 *   npm run shots -- --target rain       # 只截某个 slug
 *
 * 范围分级（见 docs/规划/任务G）：
 *   rain 本站全自动；IntelNexus/Shimmer/云枢需各自本地服务起来后用 --url 扩展；
 *   forge 无 Web UI，不设 shots。
 */
import { spawn } from 'node:child_process'
import { mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const OUT_BASE = join(root, 'public', 'screenshots')
const MAX_BYTES = 2 * 1024 * 1024 // 截图需求文档规格：单张 ≤2MB

// 网格缩略图：宽 1200px、高上限 1000px（超长截图只留顶部，Lightbox 仍取完整原图）、JPEG q85。
// 网格格子约 500px 宽 / 16:10 裁切 → 2 倍屏物理像素约 1000×625，1200px 宽与 1000px 高都留了余量，
// 绝不为提速牺牲清晰度；提速来自「不下载 1160/1600px PNG 原图 + 不解码 500 万像素」。
const THUMB_WIDTH = 1200
const THUMB_MAX_HEIGHT = 1000
const THUMB_QUALITY = 0.85

// ---- 截图目标（元素级截图：选择器须为页面稳定根区块）----
const TARGETS = [
  {
    slug: 'rain',
    shots: [
      { path: '/', selector: '.hero', name: 'rain-01', label: '首页 Hero 主视觉' },
      { path: '/', selector: '#work', name: 'rain-02', label: 'Works 项目列表' },
      { path: '/', selector: '#about', name: 'rain-03', label: '首页 Beyond / 摄影卡', optional: true },
      { path: '/photography', selector: '.gallery', name: 'rain-04', label: '摄影页对齐行排版', jpeg: true },
      { path: '/projects/yunshu', selector: '.detail', name: 'rain-05', label: '详情页档案式结构' },
      { path: '/projects/forge', selector: '.detail', name: 'rain-06', label: 'Forge 详情页' },
    ],
  },
]

// ---- 参数 ----
const argv = process.argv.slice(2)
const argOf = (k) => {
  const i = argv.indexOf(`--${k}`)
  return i >= 0 ? argv[i + 1] : undefined
}
const onlyTarget = argOf('target')
const externalUrl = argOf('url')
const thumbsOnly = argv.includes('--thumbs-only')
const VIEWPORT = { width: 1440, height: 900 }

const log = (...a) => console.log('[shots]', ...a)

/**
 * 幂等同步 public/screenshots/<slug>/thumbs/<stem>.jpg。
 * 只处理「缩略图缺失或比原图旧」的文件；用 Playwright 页面里的 canvas 缩放，
 * 不引入 sharp / jimp 等新依赖（截图脚本本来就依赖 Playwright）。
 * data: URI 画进 canvas 不会污染画布，toDataURL 可直接取回。
 */
async function syncThumbs(page) {
  let made = 0
  let skipped = 0
  const slugs = readdirSync(OUT_BASE, { withFileTypes: true })
    .filter((d) => d.isDirectory() && d.name !== 'thumbs')
    .map((d) => d.name)
  for (const slug of slugs) {
    const dir = join(OUT_BASE, slug)
    const thumbDir = join(dir, 'thumbs')
    for (const name of readdirSync(dir)) {
      if (!/\.(png|jpe?g)$/i.test(name)) continue
      const src = join(dir, name)
      const srcStat = statSync(src)
      if (!srcStat.isFile()) continue
      const stem = name.replace(/\.(png|jpe?g)$/i, '')
      const dst = join(thumbDir, `${stem}.jpg`)
      try {
        if (statSync(dst).mtimeMs >= srcStat.mtimeMs) { skipped++; continue }
      } catch { /* 不存在 → 生成 */ }

      const mime = /\.png$/i.test(name) ? 'png' : 'jpeg'
      const dataUrl = `data:image/${mime};base64,${readFileSync(src).toString('base64')}`
      const result = await page.evaluate(async ({ url, width, maxHeight, quality }) => {
        const img = new Image()
        img.src = url
        await img.decode()
        const scale = Math.min(1, width / img.naturalWidth)
        const w = Math.max(1, Math.round(img.naturalWidth * scale))
        const fullH = Math.round(img.naturalHeight * scale)
        const h = Math.min(fullH, maxHeight)
        const canvas = document.createElement('canvas')
        canvas.width = w
        canvas.height = h
        const ctx = canvas.getContext('2d')
        ctx.imageSmoothingEnabled = true
        ctx.imageSmoothingQuality = 'high'
        ctx.fillStyle = '#fff'
        ctx.fillRect(0, 0, w, h)
        // 按完整高度绘制，canvas 高度不足时自然裁掉底部 → 保留顶部内容
        ctx.drawImage(img, 0, 0, w, fullH)
        return canvas.toDataURL('image/jpeg', quality)
      }, { url: dataUrl, width: THUMB_WIDTH, maxHeight: THUMB_MAX_HEIGHT, quality: THUMB_QUALITY })

      mkdirSync(thumbDir, { recursive: true })
      const buf = Buffer.from(result.slice(result.indexOf(',') + 1), 'base64')
      writeFileSync(dst, buf)
      log(`  thumb ${slug}/${stem}.jpg — ${(buf.length / 1024).toFixed(0)}KB`)
      made++
    }
  }
  return { made, skipped }
}

/** 起 vite preview 并等待就绪（返回 kill 函数） */
async function startPreview() {
  log('starting vite preview …')
  const child = spawn('npm', ['run', 'preview', '--', '--port', '4174', '--strictPort'], {
    cwd: root,
    shell: true,
    stdio: 'pipe',
  })
  const base = 'http://localhost:4174'
  for (let i = 0; i < 60; i++) {
    try {
      const res = await fetch(`${base}/rain/`)
      if (res.ok) {
        log('preview ready at', base)
        return () => child.kill()
      }
    } catch { /* not ready yet */ }
    await new Promise((r) => setTimeout(r, 500))
  }
  child.kill()
  throw new Error('vite preview 90s 未就绪（先跑 npm run build）')
}

async function main() {
  const { chromium } = await import('playwright')
  const stopPreview = externalUrl || thumbsOnly ? null : await startPreview()
  const base = externalUrl || 'http://localhost:4174'
  let browser
  try {
    browser = await chromium.launch()
    const page = await browser.newPage({ viewport: VIEWPORT, deviceScaleFactor: 1 })

    if (!thumbsOnly) {
      const targets = TARGETS.filter((t) => !onlyTarget || t.slug === onlyTarget)
      if (!targets.length) throw new Error(`没有匹配的目标：${onlyTarget}`)

      let produced = 0
      for (const target of targets) {
        const outDir = join(OUT_BASE, target.slug)
        mkdirSync(outDir, { recursive: true })
        for (const shot of target.shots) {
          const url = `${base.replace(/\/$/, '')}${shot.path}`
          await page.goto(url, { waitUntil: 'networkidle', timeout: 30000 })
          await page.waitForTimeout(600) // 字体/懒加载收尾
          const el = page.locator(shot.selector).first()
          try {
            await el.waitFor({ state: 'visible', timeout: 8000 })
          } catch {
            if (shot.optional) { log(`skip ${shot.name}（${shot.selector} 不存在）`); continue }
            throw new Error(`${shot.path} 上找不到 ${shot.selector}（${shot.label}）`)
          }
          const out = join(outDir, `${shot.name}.${shot.jpeg ? 'jpg' : 'png'}`)
          // 大区块（照片墙等）用 jpeg q85 压规格（截图需求：单张 ≤2MB），其余 PNG 保文字锐度
          await el.screenshot({ path: out, type: shot.jpeg ? 'jpeg' : 'png', quality: shot.jpeg ? 85 : undefined })
          const size = statSync(out).size
          const sizeNote = size > MAX_BYTES ? ` ⚠️ ${(size / 1048576).toFixed(1)}MB 超 2MB，建议压缩` : ` ${(size / 1024).toFixed(0)}KB`
          log(`✓ ${shot.name}.${shot.jpeg ? 'jpg' : 'png'} — ${shot.label}${sizeNote}`)
          produced++
        }
      }
      log(`完成：${produced} 张 → public/screenshots/<slug>/`)
      log('接入方式：管理台「03 项目内容」→ shots 节取消 pending → datalist 选文件 + 写图注 → 04 发布')
    }

    // 截图与缩略图始终成对产出，避免新截图发布后网格回退到大图
    const { made, skipped } = await syncThumbs(page)
    log(`缩略图同步：新增 ${made} 张，已是最新 ${skipped} 张 → <slug>/thumbs/`)
  } finally {
    if (browser) await browser.close()
    if (stopPreview) stopPreview()
  }
}

main().catch((e) => {
  console.error('[shots] 失败：', e.message)
  process.exit(1)
})
