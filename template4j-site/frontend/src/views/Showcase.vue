<template>
  <div class="showcase">
    <h2>{{ t('showcase.title') }}: 6 live examples</h2>
    <p class="intro">{{ t('showcase.intro') }}</p>

    <div class="cards">
      <div v-for="c in cards" :key="c.id" class="card" :class="{ open: open === c.id }">
        <div class="card-head" @click="toggle(c.id)">
          <div class="card-title">
            <span class="card-num">{{ c.id }}</span>
            <strong>{{ c.title }}</strong>
          </div>
          <div class="card-meta">
            <el-tag v-for="tag in c.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
            <span class="card-toggle">{{ open === c.id ? '▼' : '▶' }}</span>
          </div>
        </div>
        <div v-if="open === c.id" class="card-body">
          <p class="card-desc">{{ c.desc }}</p>
          <div class="card-run">
            <el-button type="primary" size="small" :loading="loading[c.id]" @click="run(c)">
              ▶ {{ t('showcase.run') }}
            </el-button>
            <span v-if="elapsed[c.id]" class="card-time">{{ elapsed[c.id] }} ms</span>
          </div>
          <div class="card-split">
            <div class="card-pane">
              <div class="pane-label">{{ t('common.template') }}</div>
              <pre>{{ c.template }}</pre>
            </div>
            <div class="card-pane">
              <div class="pane-label">{{ t('common.output') }}</div>
              <pre>{{ outputs[c.id] || t('showcase.pressRun') }}</pre>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import axios from 'axios'

const { t } = useI18n()

const cards = [
  {
    id: 1,
    title: 'GitHub User Card',
    tags: ['parallel', 'rest'],
    desc: '并发拉 user + repos,each 推导 topRepos,spread 算总 star。',
    template: `{
  "@class": "parallel",
  "tasks": {
    "user":  { "@class": "rest", "url": "/demo/github/users/{@login}" },
    "repos": { "@class": "rest", "url": "/demo/github/users/{@login}/repos" }
  },
  "body": {
    "login": "{$user.login}",
    "name": "{$user.name}",
    "topRepos": {
      "@class": "each",
      "select": "{$repos}",
      "body": { "name": "{$item.name}", "stars": "{$item.stars}" }
    }
  }
}`,
    endpoint: '/api/demo/parallel',
    data: { userId: 'u-1' },
  },
  {
    id: 2,
    title: 'BFF User Profile',
    tags: ['parallel', 'try'],
    desc: '3 个微服务聚合,user 加熔断,payment 失败自动走缓存。',
    template: '(see /api/demo/parallel — same engine call)',
    endpoint: '/api/demo/parallel',
    data: {},
  },
  {
    id: 3,
    title: 'Circuit Breaker',
    tags: ['rest', 'circuitBreaker'],
    desc: '5xx 失败 3 次后熔断 30s,half-open 探测恢复。',
    template: '(see docs/06-components.md §8.1 circuitBreaker)',
    endpoint: null,
  },
  {
    id: 4,
    title: 'Cached + Auth',
    tags: ['rest', 'cache', 'auth'],
    desc: 'Bearer token 自动注入,60s 缓存去重。',
    template: `{
  "@class": "rest",
  "url": "/api/orders",
  "auth": { "type": "bearer", "token": "{@token}" },
  "cache": { "key": "orders:{@userId}", "ttl": 60000 }
}`,
    endpoint: null,
  },
  {
    id: 5,
    title: 'Schema Guard',
    tags: ['schema'],
    desc: 'withInputSchema 校验入参契约,fail-fast。',
    template: '(see /api/demo/schema)',
    endpoint: '/api/demo/schema',
    data: {},
  },
  {
    id: 6,
    title: 'MCP over HTTP',
    tags: ['mcp'],
    desc: 'JSON-RPC tools/call,调 render_template 工具。',
    template: '(see /api/demo/mcp)',
    endpoint: '/api/demo/mcp/try',
    data: { template: '{"v":"{@v}"}', data: { v: 42 } },
  },
]

const open = ref<number | null>(1)
const loading = reactive<Record<number, boolean>>({})
const elapsed = reactive<Record<number, number>>({})
const outputs = reactive<Record<number, string>>({})

function toggle(id: number) {
  open.value = open.value === id ? null : id
}

async function run(c: any) {
  if (!c.endpoint) {
    outputs[c.id] = t('showcase.noEndpoint')
    return
  }
  loading[c.id] = true
  try {
    let res
    if (c.endpoint.endsWith('/try')) {
      res = await axios.post(c.endpoint, c.data)
      outputs[c.id] = JSON.stringify(res.data?.data?.result?.content?.[0] || res.data, null, 2)
    } else {
      res = await axios.get(c.endpoint, { params: c.data })
      outputs[c.id] = JSON.stringify(res.data?.data, null, 2)
      elapsed[c.id] = res.data?.data?.elapsedMs || 0
    }
  } catch (e: any) {
    outputs[c.id] = 'ERROR: ' + (e.response?.data?.message || e.message)
  } finally {
    loading[c.id] = false
  }
}
</script>

<style scoped>
.showcase { max-width: 1100px; margin: 0 auto; padding: 20px 24px; }
.showcase h2 { font-size: 32px; margin-bottom: 8px; }
.intro { color: var(--el-text-color-secondary); margin-bottom: 24px; }
.cards { display: flex; flex-direction: column; gap: 12px; }
.card { border: 1px solid var(--el-border-color); border-radius: 6px; overflow: hidden; }
.card.open { border-color: var(--el-color-primary); }
.card-head { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; cursor: pointer; }
.card-head:hover { background: var(--el-fill-color-light); }
.card-title { display: flex; align-items: center; gap: 12px; }
.card-num { background: var(--el-color-primary); color: white; width: 24px; height: 24px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; font-size: 12px; }
.card-meta { display: flex; align-items: center; gap: 8px; }
.card-toggle { color: var(--el-text-color-secondary); }
.card-body { padding: 16px; border-top: 1px solid var(--el-border-color); background: var(--el-fill-color-light); }
.card-desc { margin-top: 0; color: var(--el-text-color-secondary); }
.card-run { display: flex; align-items: center; gap: 12px; margin: 12px 0; }
.card-time { color: #10b981; font-family: monospace; font-size: 13px; }
.card-split { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.card-pane { background: var(--el-bg-color); border-radius: 4px; padding: 8px; min-height: 200px; max-height: 400px; overflow: auto; }
.pane-label { font-size: 11px; color: var(--el-text-color-secondary); margin-bottom: 6px; text-transform: uppercase; }
.card-pane pre { margin: 0; font-size: 11px; font-family: monospace; white-space: pre-wrap; }
@media (max-width: 768px) { .card-split { grid-template-columns: 1fr; } }
</style>
