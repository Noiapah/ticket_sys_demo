import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

const localPath = (path: string) => fileURLToPath(new URL(path, import.meta.url))
const appVersion = JSON.parse(readFileSync(localPath('../package.json'), 'utf8')).version as string

export default defineConfig(({ mode }) => ({
  root: localPath('./'),
  cacheDir: localPath('../target/cache/vite'),
  plugins: [vue()],
  define: {
    __APP_VERSION__: JSON.stringify(appVersion)
  },
  resolve: {
    alias: {
      '#ticket-gateway': localPath(mode === 'mock' ? './src/mocks/mockGateway.ts' : './src/gateway/httpGateway.ts')
    }
  },
  build: {
    outDir: localPath('../target/frontend'),
    emptyOutDir: true,
    target: 'es2020'
  },
  server: {
    port: 5173,
    proxy: { '/api': 'http://127.0.0.1:8080' }
  },
  test: {
    environment: 'node',
    coverage: { reportsDirectory: localPath('../target/coverage') },
    include: ['src/**/*.test.ts']
  }
}))
