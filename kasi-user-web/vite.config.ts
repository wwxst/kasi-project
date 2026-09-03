import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import { loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.VITE_PROXY_TARGET || 'http://localhost:8080'

  return {
    plugins: [react()],
    server: {
      proxy: {
        '/api': apiTarget,
      },
    },
    test: {
      environment: 'jsdom',
      passWithNoTests: true,
      testTimeout: 15_000,
    },
  }
})
