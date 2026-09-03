import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

const apiTarget = process.env.VITE_PROXY_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  build: {
    chunkSizeWarningLimit: 2_000,
  },
  server: {
    proxy: {
      '/api': apiTarget,
      '/uploads': apiTarget,
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
    maxWorkers: 2,
    setupFiles: './src/test/setup.ts',
    testTimeout: 15_000,
  },
})
