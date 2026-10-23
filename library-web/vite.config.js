import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Vite 配置
// 关键点：开发时通过 proxy 把 /api 前缀剥掉转发给后端 Tomcat(8081)，
// 这样浏览器眼里所有请求都是同源的，不会触发跨域(CORS)。
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      // 用 @ 代表 src 目录，import 路径更短
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    open: false,
    proxy: {
      '/api': {
        target: 'http://localhost:8081', // 后端 Spring Boot 地址
        changeOrigin: true,
        // 把请求路径里的 /api 去掉再转发：
        // 前端请求 /api/books  ->  实际打到后端 /books
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      // 图书封面图：后端上传接口返回 /uploads/xxx.jpg 这种地址，
      // 浏览器直接请求这个地址时也要转发给后端，
      // 否则会被 Vite 当成前端路由，图片显示不出来。
      '/uploads': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    // 打包后交给 nginx 托管，资源用相对路径，避免部署到子目录时白屏
    assetsDir: 'assets'
  }
})
