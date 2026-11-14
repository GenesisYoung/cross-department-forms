import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import * as Icons from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router.js'
import './styles.css'

// Follow the OS light/dark preference.
const media = window.matchMedia('(prefers-color-scheme: dark)')
const applyTheme = () => document.documentElement.classList.toggle('dark', media.matches)
applyTheme()
media.addEventListener('change', applyTheme)

const app = createApp(App)
for (const [name, comp] of Object.entries(Icons)) app.component(name, comp)
app.use(createPinia()).use(router).use(ElementPlus)
app.mount('#app')
