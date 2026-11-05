import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'
export default defineConfig({
  plugins: [vue(), Components({ dts: false, resolvers: [ElementPlusResolver()] })],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: { proxy: { '/api': 'http://localhost:8080', '/ws': { target: 'ws://localhost:8080', ws: true } } },
  build: { chunkSizeWarningLimit: 650, rollupOptions: { output: { manualChunks(id) {
    if (id.includes('node_modules/echarts') || id.includes('node_modules/zrender')) return 'charts'
    if (id.includes('node_modules/@vue-flow') || id.includes('node_modules/yjs')) return 'flow'
    if (id.includes('node_modules/vue') || id.includes('node_modules/pinia') || id.includes('node_modules/vue-router')) return 'vue'
  } } } }
})
