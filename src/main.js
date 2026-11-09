import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import { apiBase } from './data/site.js'
import HomePage from './pages/HomePage.vue'
import ProjectDetailPage from './pages/ProjectDetailPage.vue'
import AboutPage from './pages/AboutPage.vue'
import PhotographyPage from './pages/PhotographyPage.vue'
import NotFoundPage from './pages/NotFoundPage.vue'
import './styles/base.scss'

// 自托管字体（@fontsource）：取代 Google Fonts 的 <style>@import——
// 国内 fonts.googleapis.com 不可达会挂起首屏，且 style→css2 双链渲染阻塞。
// 字重收敛到 CSS/SVG 实际使用的 400/500/600/700；CJK 按 unicode-range 分包按需下载。
import '@fontsource/manrope/400.css'
import '@fontsource/manrope/500.css'
import '@fontsource/manrope/600.css'
import '@fontsource/manrope/700.css'
import '@fontsource/noto-sans-sc/400.css'
import '@fontsource/noto-sans-sc/500.css'
import '@fontsource/noto-sans-sc/600.css'
import '@fontsource/noto-sans-sc/700.css'
import '@fontsource/dm-mono/400.css'
import '@fontsource/dm-mono/500.css'

const router = createRouter({
  // BASE_URL 随 Vite base 切换：本地 '/'，GitHub Pages 构建时 '/rain/'
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomePage,
      meta: { title: 'YUAN — Personal Archive' },
    },
    {
      path: '/projects/:slug',
      name: 'project',
      component: ProjectDetailPage, // title 由页面按数据动态设置
    },
    {
      path: '/about',
      name: 'about',
      component: AboutPage,
      meta: { title: 'About — YUAN', desc: '陈雨 — 软件工程专业 2027 届。关于我：方向、经历与正在做的事。' },
    },
    {
      path: '/photography',
      name: 'photography',
      component: PhotographyPage,
      meta: { title: 'Photography — YUAN', desc: 'Field Notes — 观察光线、街道与日常场景的摄影档案。' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'notfound',
      component: NotFoundPage,
      meta: { title: '404 — YUAN' },
    },
  ],
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition // 浏览器前进/后退保留滚动位置
    if (to.hash) return { el: to.hash, top: 90 }
    return { top: 0 }
  },
})

// ---- 每路由 SEO：title / description / canonical / og 同步（默认值取 index.html） ----
const SITE_ORIGIN = 'https://yuanyyyyyyyyy.github.io'
const DEFAULT_TITLE = 'YUAN — Personal Archive'
const DEFAULT_DESC = '陈雨 — 软件工程专业 2027 届。关注 Java 后端开发、AI 应用与系统工程。个人作品集与项目档案。'

function setMeta(selector, value) {
  const el = document.head.querySelector(selector)
  if (el) el.setAttribute('content', value)
}

router.afterEach((to) => {
  // SEO 注入
  const m = to.meta || {}
  document.title = m.title || DEFAULT_TITLE
  const desc = m.desc || DEFAULT_DESC
  setMeta('meta[name="description"]', desc)
  setMeta('meta[property="og:title"]', document.title)
  setMeta('meta[property="og:description"]', desc)
  const base = import.meta.env.BASE_URL.replace(/\/$/, '')
  const url = `${SITE_ORIGIN}${base}${to.path}`
  const canonical = document.head.querySelector('link[rel="canonical"]')
  if (canonical) canonical.setAttribute('href', url)
  setMeta('meta[property="og:url"]', url)

  // 1px 埋点：路由切换上报（无 Cookie；IP 在服务端哈希脱敏）。
  // Worker 不可达时静默失败，不影响前台。
  try {
    fetch(`${apiBase}/api/t`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ path: to.path, referrer: document.referrer }),
      keepalive: true,
    }).catch(() => {})
  } catch {
    /* 埋点永不影响前台 */
  }
})

createApp(App).use(router).mount('#app')
