import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from '../api.js'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', component: () => import('../pages/LoginPage.vue') },
    {
      path: '/',
      component: () => import('../pages/LayoutPage.vue'),
      children: [
        { path: '', component: () => import('../pages/DashboardPage.vue') },
        { path: 'photos', component: () => import('../pages/PhotosPage.vue') },
        { path: 'content', component: () => import('../pages/ContentPage.vue') },
        { path: 'publish', component: () => import('../pages/PublishPage.vue') },
        { path: 'inbox', component: () => import('../pages/InboxPage.vue') },
      ],
    },
  ],
})

router.beforeEach((to) => {
  if (to.path !== '/login' && !getToken()) return '/login'
})

export default router
