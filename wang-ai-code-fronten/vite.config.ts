import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // 后端代理地址：默认指向生产后端，保持原有行为不变。
  // 本地联调时在 .env.development.local 里设
  //   VITE_API_PROXY_TARGET=http://localhost:8124
  // 即可切到本地后端（该文件不随代码走，删除即恢复默认）。
  // 注意：server.proxy 只作用于 dev server，不影响 npm run build 的产物。
  const env = loadEnv(mode, process.cwd(), '')
  const apiProxyTarget = env.VITE_API_PROXY_TARGET || 'http://8.134.185.186:58123'
  // 已部署全栈应用的访问地址 /apps/{deployKey}/（预览 iframe 用）由 nginx 提供，与 API 不同源，
  // dev 下需单独代理：默认指向生产入口（80 端口 nginx），本地联调在 .env.development.local 里设
  //   VITE_APPS_PROXY_TARGET=http://127.0.0.1
  const appsProxyTarget = env.VITE_APPS_PROXY_TARGET || 'http://8.134.185.186'

  return {
    plugins: [vue(), vueDevTools()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      proxy: {
        '/api': {
          target: apiProxyTarget,
          changeOrigin: true,
          secure: false,
        },
        '/apps': {
          target: appsProxyTarget,
          changeOrigin: true,
          secure: false,
        },
      },
    },
  }
})
