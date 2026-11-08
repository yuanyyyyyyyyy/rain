import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import './styles/base.scss'

import HomePage from './pages/HomePage.vue'
import ProjectDetailPage from './pages/ProjectDetailPage.vue'
import AboutPage from './pages/AboutPage.vue'
import PhotographyPage from './pages/PhotographyPage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomePage },
    { path: '/projects/:slug', name: 'project', component: ProjectDetailPage },
    { path: '/about', name: 'about', component: AboutPage },
    { path: '/photography', name: 'photography', component: PhotographyPage },
  ],
  scrollBehavior(to) {
    if (to.hash) return { el: to.hash, top: 90 }
    return { top: 0 }
  },
})

createApp(App).use(router).mount('#app')
