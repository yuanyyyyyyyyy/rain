import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5175,
    proxy: {
      // 开发期代理到 Spring Boot，生产期同域部署无需代理
      '/api': 'http://localhost:8081',
      // 照片文件走后端静态映射（WebConfig → 前台 public/photos）
      '/photos': 'http://localhost:8081',
    },
  },
})
