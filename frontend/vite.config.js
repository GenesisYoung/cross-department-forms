import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// In dev, the Vite server proxies REST and WebSocket traffic to the Spring Boot backend.
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true }
    }
  },
  test: { environment: 'node' }
})
