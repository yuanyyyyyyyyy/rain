import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import { apiBase } from './data/site.js'
import './styles/base.scss'

import HomePage from './pages/HomePage.vue'
import ProjectDetailPage from './pages/ProjectDetailPage.vue'
import AboutPage from './pages/AboutPage.vue'
import PhotographyPage from './pages/PhotographyPage.vue'
import NotFoundPage from './pages/NotFoundPage.vue'

const router = createRouter({
  // BASE_URL 随 Vite base 切换：本地 '/'，GitHub Pages 构建时 '/rain/'
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: HomePage },
    { path: '/projects/:slug', name: 'project', component: ProjectDetailPage },
    { path: '/about', name: 'about', component: AboutPage },
    { path: '/photography', name: 'photography', component: PhotographyPage },
    { path: '/:pathMatch(.*)*', name: 'notfound', component: NotFoundPage },
  ],
  scrollBehavior(to) {
    if (to.hash) return { el: to.hash, top: 90 }
    return { top: 0 }
  },
})

// 1px 埋点：路由切换上报（无 Cookie；IP 在服务端哈希脱敏）。
// 静态托管上无后端接收时静默失败，后端公网化后自动生效。
router.afterEach((to) => {
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
