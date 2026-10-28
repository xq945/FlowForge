import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * 开发期把 /api 代理到网关，避免浏览器跨域；
 * 生产环境由 nginx 承担同样的转发职责。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 默认只监听 [::1]，用 127.0.0.1 访问会失败；显式监听所有 IPv4 接口
    host: true,
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8083',
        changeOrigin: true,
      },
    },
  },
})
