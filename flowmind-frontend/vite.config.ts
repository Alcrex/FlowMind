import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
export default defineConfig({
  plugins: [vue()], resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: { proxy: { '/api': 'http://localhost:8080', '/ws': { target: 'ws://localhost:8080', ws: true } } },
  build: { chunkSizeWarningLimit: 1000, rollupOptions: { output: { manualChunks(id) {
    if (id.includes('node_modules/element-plus')) return 'element'
    if (id.includes('node_modules/echarts') || id.includes('node_modules/zrender')) return 'charts'
    if (id.includes('node_modules/@vue-flow') || id.includes('node_modules/yjs')) return 'flow'
    if (id.includes('node_modules/vue') || id.includes('node_modules/pinia') || id.includes('node_modules/vue-router')) return 'vue'
  } } } }
})
