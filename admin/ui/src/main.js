import { createApp } from 'vue'
import App from './App.vue'
import router from './router/index.js'
import './styles/tokens.scss'
import './styles/base.scss'

createApp(App).use(router).mount('#app')
