import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', name: 'home', component: () => import('@/views/Home.vue') },
  { path: '/learn', name: 'learn', component: () => import('@/views/Learn.vue') },
  { path: '/learn/:id', name: 'learn-detail', component: () => import('@/views/Learn.vue') },
  { path: '/try', name: 'try', component: () => import('@/views/Try.vue') },
  { path: '/docs', name: 'docs', component: () => import('@/views/Docs.vue') },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})
