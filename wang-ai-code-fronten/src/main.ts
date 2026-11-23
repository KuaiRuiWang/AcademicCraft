import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import '@fontsource-variable/plus-jakarta-sans/wght.css'
import '@fontsource-variable/noto-serif-sc/wght.css'
import '@/assets/global.css'

import '@/access'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(Antd)

app.mount('#app')
