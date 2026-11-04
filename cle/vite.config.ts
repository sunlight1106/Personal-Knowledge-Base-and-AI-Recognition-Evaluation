import { fileURLToPath, URL } from 'node:url'
import { ServerResponse } from 'node:http'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  publicDir: 'static',
  plugins: [vue(), {
    name: 'public-build-info',
    generateBundle(_options, bundle) {
      const entry = Object.values(bundle).find(item => item.type === 'chunk' && item.isEntry)
      if (entry) this.emitFile({ type: 'asset', fileName: 'build-info.json', source: JSON.stringify({ entry: '/' + entry.fileName }) })
    },
  }],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080', changeOrigin: true,
        configure(proxy) {
          proxy.on('error', (_error, _request, response) => {
            if (response instanceof ServerResponse && !response.headersSent) {
              response.writeHead(503, { 'Content-Type': 'application/json; charset=utf-8' })
              response.end(JSON.stringify({ success: false, error: {
                code: 'BACKEND_UNAVAILABLE',
                message: '登录服务尚未启动，请启动本地后台服务后重试。',
              } }))
            }
          })
        },
      },
    },
  },
})
