import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from './stores/auth.js'

const routes = [
  { path: '/login', name: 'login', component: () => import('./views/LoginView.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('./views/AppLayout.vue'),
    children: [
      { path: '', name: 'dashboard', component: () => import('./views/DashboardView.vue') },
      { path: 'sheets', name: 'sheets', component: () => import('./views/SheetsView.vue') },
      { path: 'sheets/:id', name: 'sheet', component: () => import('./views/SheetEditor.vue'), props: true },
      { path: 'templates', name: 'templates', component: () => import('./views/TemplatesView.vue') },
      { path: 'notifications', name: 'notifications', component: () => import('./views/NotificationsView.vue') },
      { path: 'admin', name: 'admin', component: () => import('./views/AdminView.vue'), meta: { admin: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({ history: createWebHistory(), routes })

let restored = false
router.beforeEach(async (to) => {
  const auth = useAuth()
  if (!restored) {
    restored = true
    await auth.restore()
  }
  if (!to.meta.public && !auth.loggedIn) return { name: 'login', query: to.fullPath !== '/' ? { next: to.fullPath } : {} }
  if (to.name === 'login' && auth.loggedIn) return { name: 'dashboard' }
  if (to.meta.admin && !auth.isAdmin) return { name: 'dashboard' }
})

export default router
