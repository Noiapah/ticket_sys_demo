import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  root: 'frontend',
  plugins: [vue()],
  build: {
    outDir: '../build/frontend',
    emptyOutDir: true,
    target: 'es2020'
  },
  server: {
    port: 5173,
    proxy: { '/api': 'http://127.0.0.1:8080' }
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts']
  }
})
