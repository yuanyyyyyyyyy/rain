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
    proxy: {
      // dev 期埋点上报转到本地后端（公网上该请求静默失败）
      '/api': 'http://localhost:8081',
    },
  }
})
