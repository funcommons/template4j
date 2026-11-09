# 06 · 前端结构

> Vue 3 + Element Plus + Vite SPA。对齐 mc-webui-spec v1.0 全部铁律。

## 1. 目录结构

```
frontend/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── index.html
├── public/
├── src/
│   ├── main.ts
│   ├── App.vue
│   ├── auto-imports.d.ts              # unplugin-auto-import 生成
│   ├── components.d.ts                # unplugin-vue-components 生成
│   ├── api/
│   │   ├── client.ts                  # axios 实例 + 拦截器
│   │   ├── render.ts                  # render API 封装
│   │   ├── learn.ts                   # learn API 封装
│   │   └── try.ts                     # try API 封装
│   ├── router/
│   │   └── index.ts                   # createWebHistory + 路由守卫
│   ├── stores/
│   │   ├── preference.ts              # theme/brand/locale 偏好
│   │   └── try.ts                     # playground 状态（模板/数据/历史）
│   ├── locales/
│   │   ├── zh-CN.ts
│   │   └── en-US.ts
│   ├── styles/
│   │   ├── index.scss                 # 全局 + 响应式间距
│   │   ├── _variables.scss
│   │   ├── _themes.scss
│   │   └── brands/
│   │       ├── _mchuan.scss
│   │       └── _mixins.scss
│   ├── components/
│   │   ├── sdk/
│   │   │   ├── layout/
│   │   │   │   ├── AppHeader.vue
│   │   │   │   ├── AppSidebar.vue
│   │   │   │   └── AppMain.vue
│   │   │   └── common/
│   │   │       ├── AppDialog.vue
│   │   │       └── AppSection.vue
│   │   └── try/
│   │       ├── TemplateEditor.vue     # Monaco 风格代码编辑器
│   │       ├── DataEditor.vue
│   │       └── ResultPanel.vue
│   ├── composables/
│   │   ├── useLocale.ts
│   │   ├── useResponsive.ts
│   │   └── useBrandEmpty.ts
│   ├── config/
│   │   ├── brands.ts
│   │   └── nav-config.ts
│   ├── views/
│   │   ├── Home.vue                   # 首页（特性介绍 + 快速链接）
│   │   ├── Learn.vue                  # 学习（左侧示例列表 + 右侧详情）
│   │   ├── Try.vue                    # 试用（左编辑器 + 右结果）
│   │   └── Docs.vue                   # 文档（CLAUDE.md / docs/ 渲染）
│   ├── types/
│   │   ├── api.ts                     # ApiResponse<T>, ApiError, PageResponse
│   │   └── render.ts                  # RenderRequest, ExampleMeta 等
│   └── utils/
│       ├── idempotency.ts             # UUID v4 生成
│       └── format.ts
└── e2e/                               # Playwright 测试
    ├── home.spec.ts
    ├── learn.spec.ts
    └── try.spec.ts
```

## 2. 三大核心页面布局

### 2.1 Learn.vue（学习）

```
┌──────────────────────────────────────────────────────────────────┐
│  [< Home]  [< Prev]  示例 03 · Groovy 脚本 {=...}  [Next >]      │
├──────────────────────────────────────────────────────────────────┤
│ ┌──────────────┐ ┌────────────────────────────────────────────┐ │
│ │              │ │ 📖 Description                              │ │
│ │ 示例导航     │ │ {=expr} 是 Groovy 单行表达式...              │ │
│ │              │ │                                              │ │
│ │ ▶ 01 数据    │ │ 📋 模板 (read-only)                          │ │
│ │ ▶ 02 变量    │ │ ┌────────────────────────────────────────┐  │ │
│ │ ▶ 03 脚本★   │ │ │ { "sum": "{= 1 + 2 }" }                 │  │ │
│ │ ▶ 04 if      │ │ └────────────────────────────────────────┘  │ │
│ │ ▶ 05 each    │ │                                              │ │
│ │ ▶ 06 rest    │ │ 📋 数据 (read-only)                          │ │
│ │ ▶ 07 include │ │ { "user": { "name": "template4j" } }         │ │
│ │ ▶ 08 yaml    │ │                                              │ │
│ │              │ │ ▶ 预期渲染结果                                │ │
│ │              │ │ { "sum": 3 }                                 │ │
│ │              │ │                                              │ │
│ │              │ │ [在 playground 中打开 →]                     │ │
│ └──────────────┘ └────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 Try.vue（试用）

```
┌──────────────────────────────────────────────────────────────────┐
│ Try · playground                                                  │
├──────────────────────────────────────────────────────────────────┤
│ 模板类型: ( ) JSON  (•) YAML    数据格式: (•) JSON  ( ) YAML      │
│                                                                  │
│ ┌────────────────────────┐  ┌─────────────────────────────────┐ │
│ │ 模板编辑器             │  │ 数据编辑器                       │ │
│ │ ┌──────────────────┐   │  │ ┌─────────────────────────┐     │ │
│ │ │ msg: "{@user}"   │   │  │ │ {                       │     │ │
│ │ │ upper: "{= ...}" │   │  │ │   "user": "demo"        │     │ │
│ │ │                  │   │  │ │ }                       │     │ │
│ │ └──────────────────┘   │  │ └─────────────────────────┘     │ │
│ └────────────────────────┘  └─────────────────────────────────┘ │
│                                                                  │
│ [清空] [使用示例] [▶ 渲染]                                         │
│                                                                  │
│ ┌────────────────────────────────────────────────────────────┐  │
│ │ 结果 (12 ms)                                                │  │
│ │ ┌────────────────────────────────────────────────────────┐ │  │
│ │ │ {                                                       │ │  │
│ │ │   "msg": "demo",                                        │ │  │
│ │ │   "upper": "DEMO"                                       │ │  │
│ │ │ }                                                       │ │  │
│ │ └────────────────────────────────────────────────────────┘ │  │
│ │                                                              │  │
│ │ Trace ▼ (5 步)                                              │  │
│ │  • parse_template → 1 key                                   │  │
│ │  • render_root → dispatch map                              │  │
│ │  • render_string → {@user}                                 │  │
│ │  • eval_jsonpath → 'demo'                                  │  │
│ │  • render_string → {= data.user.toUpperCase() }            │  │
│ └────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

### 2.3 Home.vue（首页）

```
┌──────────────────────────────────────────────────────────────────┐
│ template4j-demo                                                   │
│ 一站式学习 template4j-core：示例 / playground / 文档              │
│                                                                  │
│ [▶ 开始学习]  [🔧 在线试用]  [📚 文档]                            │
└──────────────────────────────────────────────────────────────────┘
┌──────────────────────────────┬───────────────────────────────────┐
│ 🎯 系统学习                  │ 🔧 在线试用                       │
│ 8 个递进示例                  │ JSON / YAML 双输入                 │
│ 覆盖语法/组件/表达式全特性    │ 实时渲染 + trace                  │
│                              │                                   │
├──────────────────────────────┼───────────────────────────────────┤
│ 🧪 集成测试                  │ 📚 文档                           │
│ Spring Boot IT 覆盖关键路径   │ CLAUDE.md / docs/ 全文检索         │
│ ExamplesRenderingIT 自动回归  │ 学习路径 4 条                     │
└──────────────────────────────┴───────────────────────────────────┘
```

## 3. axios 客户端（对齐 mc-webui-spec 场景三）

```ts
// src/api/client.ts
import axios from 'axios'
import { v4 as uuidv4 } from 'uuid'

export const client = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

client.interceptors.request.use(cfg => {
  if (['post', 'put', 'patch', 'delete'].includes(cfg.method || '')) {
    cfg.headers['Idempotency-Key'] = uuidv4()
  }
  cfg.headers['X-Trace-Id'] = sessionStorage.getItem('trace_id') || uuidv4().replace(/-/g, '')
  return cfg
})

client.interceptors.response.use(
  res => {
    const body = res.data
    const traceId = res.headers['x-trace-id'] || body.trace_id
    if (traceId) sessionStorage.setItem('trace_id', traceId)
    if (body.code === 0) return body.data
    ElMessage.error(body.message)
    throw new BizError(body.code, body.message, body.error)
  },
  err => {
    if (err.response?.status === 429 || err.response?.status === 503) {
      throw new RateLimitError(err.response.headers['retry-after'])
    }
    ElMessage.error('网络异常')
    throw err
  }
)
```

## 4. 路由（createWebHistory）

```ts
// src/router/index.ts
import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/',         name: 'home',  component: () => import('@/views/Home.vue') },
  { path: '/learn',    name: 'learn', component: () => import('@/views/Learn.vue') },
  { path: '/learn/:id', name: 'learn-detail', component: () => import('@/views/Learn.vue') },
  { path: '/try',      name: 'try',   component: () => import('@/views/Try.vue') },
  { path: '/docs',     name: 'docs',  component: () => import('@/views/Docs.vue') },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})
```

## 5. Pinia 状态（preference + try）

```ts
// stores/preference.ts
export const usePreference = defineStore('preference', {
  state: () => ({
    theme: localStorage.getItem('theme') || 'light',
    brand: localStorage.getItem('brand') || 'mchuan',
    locale: localStorage.getItem('locale') || 'zh-CN',
  }),
  actions: {
    setTheme(v) { this.theme = v; localStorage.setItem('theme', v) },
    setBrand(v) { this.brand = v; localStorage.setItem('brand', v) },
    setLocale(v) { this.locale = v; localStorage.setItem('locale', v); i18n.global.locale.value = v },
  },
})

// stores/try.ts
export const useTry = defineStore('try', {
  state: () => ({
    templateType: 'json' as 'json' | 'yaml',
    template: '',
    dataFormat: 'json' as 'json' | 'yaml',
    data: '{}',
    history: [] as { ts: number; template: string; data: string; rendered: unknown }[],
    lastResult: null as null | { rendered: unknown; timing_ms: number; trace: unknown[] },
  }),
})
```

## 6. 国际化（zh-CN / en-US）

```ts
// locales/zh-CN.ts
export default {
  app: { title: 'template4j-demo' },
  nav: { home: '首页', learn: '学习', try: '试用', docs: '文档' },
  home: { subtitle: '一站式学习 template4j-core：示例 / playground / 文档' },
  learn: { examples: '示例导航', description: '说明', template: '模板', data: '数据', expected: '预期渲染', openInTry: '在 playground 中打开' },
  try: { templateType: '模板类型', dataFormat: '数据格式', render: '渲染', clear: '清空', useExample: '使用示例', result: '结果', trace: 'Trace', history: '历史' },
  error: { render: '渲染失败', network: '网络异常' },
}
```

## 7. 与后端的端口约定

| 模式 | 后端 | 前端 | 备注 |
|------|------|------|------|
| 开发（dev） | 8088 | 5173 | Vite 代理 `/api` → 8088 |
| 一体化打包 | 8088 | (内嵌) | 前端 build → `src/main/resources/static/` |

`vite.config.ts`：

```ts
server: {
  proxy: {
    '/api': { target: 'http://localhost:8088', changeOrigin: true }
  }
}
```

## 8. P0 必查 5 项（mc-webui-spec 场景六）

| # | 项 | 实现位置 |
|---|----|---------|
| 1 | createWebHistory | `router/index.ts` |
| 2 | 无硬编码中文 | 全部 `t('key')` |
| 3 | axios 拦截器 | `api/client.ts` |
| 4 | ID/金额字段 string | `types/api.ts` 实体类型 |
| 5 | Idempotency-Key | `api/client.ts` 请求拦截 |
