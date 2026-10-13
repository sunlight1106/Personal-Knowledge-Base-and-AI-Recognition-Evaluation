import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { themeStore } from './stores/theme'
import './styles/main.css'
import './styles/portal.css'
import './styles/themes.css'
import './styles/research.css'
import './styles/workspace.css'
import './styles/workspace-pages.css'
import './styles/motion.css'

themeStore.init()

createApp(App).use(router).mount('#app')
