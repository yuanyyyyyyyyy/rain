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
import { mkdirSync, statSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const OUT_BASE = join(root, 'public', 'screenshots')
const MAX_BYTES = 2 * 1024 * 1024 // 截图需求文档规格：单张 ≤2MB

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
const VIEWPORT = { width: 1440, height: 900 }

const log = (...a) => console.log('[shots]', ...a)

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
  const stopPreview = externalUrl ? null : await startPreview()
  const base = externalUrl || 'http://localhost:4174'
  let browser
  try {
    browser = await chromium.launch()
    const page = await browser.newPage({ viewport: VIEWPORT, deviceScaleFactor: 1 })
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
        log(`✓ ${shot.name}.png — ${shot.label}${sizeNote}`)
        produced++
      }
    }
    log(`完成：${produced} 张 → public/screenshots/<slug>/`)
    log('接入方式：管理台「03 项目内容」→ shots 节取消 pending → datalist 选文件 + 写图注 → 04 发布')
  } finally {
    if (browser) await browser.close()
    if (stopPreview) stopPreview()
  }
}

main().catch((e) => {
  console.error('[shots] 失败：', e.message)
  process.exit(1)
})
