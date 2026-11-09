import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', name: 'home', component: () => import('@/views/Home.vue') },
  { path: '/playground', name: 'playground', component: () => import('@/views/Playground.vue') },
  { path: '/showcase', name: 'showcase', component: () => import('@/views/Showcase.vue') },
  { path: '/learn', name: 'learn', component: () => import('@/views/Learn.vue') },
  { path: '/learn/:id', name: 'learn-detail', component: () => import('@/views/Learn.vue') },
  { path: '/try', name: 'try', component: () => import('@/views/Try.vue') },
  { path: '/perf', name: 'perf', component: () => import('@/views/Perf.vue') },
  { path: '/mcp', name: 'mcp', component: () => import('@/views/Mcp.vue') },
  { path: '/docs', name: 'docs', component: () => import('@/views/Docs.vue') },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFound.vue') },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})
