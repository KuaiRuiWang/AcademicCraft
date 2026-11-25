import { createApp } from 'vue'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import '@fontsource-variable/plus-jakarta-sans'
import App from '@/App.vue'
import router from '@/router'
import { clearSession } from '@/session'
import '@/styles.css'

window.addEventListener('admin-session-expired', () => {
  clearSession()
  if (router.currentRoute.value.path !== '/login') void router.replace('/login')
})

createApp(App).use(Antd).use(router).mount('#app')
