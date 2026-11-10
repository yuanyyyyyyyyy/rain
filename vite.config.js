import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// base 按环境切换：GitHub Pages 项目页需 /rain/ 前缀（Actions 构建时设置），
// 本地 dev 与管理台发布链路保持 '/' 不变
export default defineConfig({
  plugins: [vue()],
  base: process.env.DEPLOY_BASE || '/',
  server: {
    port: 5173,
    host: true,
    // dev 期埋点/表单经 content/site.json 的 apiBase 直连线上 Worker；
    // 如需本地 Spring 联调：把 apiBase 改为 ''，并在下方临时加 proxy '/api' → 'http://localhost:8081'
  }
})
