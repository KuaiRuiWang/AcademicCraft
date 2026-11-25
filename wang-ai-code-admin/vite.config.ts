import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_ADMIN_')
  return {
    base: env.VITE_ADMIN_BASE || '/',
    plugins: [vue()],
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    server: {
      port: 5181,
      proxy: { '/api': env.VITE_ADMIN_API_PROXY || 'http://127.0.0.1:8123' },
    },
  }
})
