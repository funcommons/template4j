<template>
  <div class="home">
    <section class="hero">
      <div class="hero-left">
        <h1>{{ t('home.heroTitle1') }}<br /><span class="accent">{{ t('home.heroTitle2') }}</span></h1>
        <p class="tagline">
          {{ t('home.heroSubtitle') }}
        </p>
        <div class="cta">
          <el-button type="primary" size="large" @click="$router.push('/playground')">
            {{ t('home.tryInPlayground') }}
          </el-button>
          <el-button size="large" @click="$router.push('/learn')">{{ t('home.readDocs') }}</el-button>
        </div>
        <div class="stats">
          <span><strong>9</strong> {{ t('home.statComponents') }}</span>
          <span><strong>5.7x</strong> {{ t('home.statPerf') }}</span>
          <span><strong>1441</strong> {{ t('home.statTests') }}</span>
          <span><strong>0</strong> {{ t('home.statSpringDeps') }}</span>
        </div>
      </div>
      <div class="hero-right">
        <div class="hero-tabs">
          <button v-for="ex in heroExamples" :key="ex.name"
                  :class="['hero-tab', { active: active === ex.name }]"
                  @click="active = ex.name">{{ ex.name }}</button>
        </div>
        <div class="hero-split">
          <div class="hero-pane">
            <div class="pane-label">{{ t('home.paneTemplate') }}</div>
            <pre>{{ activeEx.template }}</pre>
          </div>
          <div class="hero-pane">
            <div class="pane-label">{{ t('home.paneOutput') }}</div>
            <pre>{{ activeOutput || '...' }}</pre>
          </div>
        </div>
      </div>
    </section>

    <section class="features">
      <h2>{{ t('home.whatsInside') }}</h2>
      <div class="feature-grid">
        <div class="feature-card" v-for="f in features" :key="f.title">
          <div class="feature-icon">{{ f.icon }}</div>
          <h3>{{ f.title }}</h3>
          <p>{{ f.desc }}</p>
          <code v-if="f.code">{{ f.code }}</code>
        </div>
      </div>
    </section>

    <section class="cta-section">
      <h2>{{ t('home.readyToShip') }}</h2>
      <div class="install">
        <pre>{{ depsXml }}</pre>
        <el-button @click="copyDeps">{{ t('common.copy') }}</el-button>
      </div>
      <p class="hint">{{ t('home.mcpHint') }} <code>java -jar template4j-core-shaded.jar mcp</code></p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const { t } = useI18n()

const heroExamples = [
  {
    name: 'parallel',
    template: `{
  "@class": "parallel",
  "tasks": {
    "user":   { "@class": "rest", "url": "/users/{@id}" },
    "orders": { "@class": "rest", "url": "/users/{@id}/orders" }
  },
  "body": {
    "userName": "{$user.name}",
    "orderCount": "{$orders.size()}"
  }
}`,
    data: { id: 42 },
  },
  {
    name: 'try fallback',
    template: `{
  "@class": "try",
  "select": { "@class": "rest", "url": "/flaky" },
  "fallback": { "ok": false, "msg": "down" }
}`,
    data: {},
  },
  {
    name: 'script',
    template: `{
  "tier": "{= @n > 100 ? 'vip' : 'normal' }",
  "discount": "{= @n * 0.1 }"
}`,
    data: { n: 150 },
  },
]
const active = ref('parallel')
const activeEx = computed(() => heroExamples.find(e => e.name === active.value)!)
const activeOutput = ref('')

async function renderHero() {
  try {
    const res = await axios.post('/api/try', {
      template_type: 'json',
      template: activeEx.value.template,
      data: activeEx.value.data,
      options: {},
    })
    activeOutput.value = JSON.stringify(res.data?.data?.rendered, null, 2)
  } catch (e: any) {
    activeOutput.value = t('home.needsMock')
  }
}

const features = [
  { icon: '🔀', title: 'parallel', desc: '并发编排多个子模板,整体 deadline,容忍单任务失败。', code: '"@class": "parallel"' },
  { icon: '🛡️', title: 'try', desc: '错误隔离 + fallback / onError / rethrow。', code: '"@class": "try"' },
  { icon: '🌐', title: 'rest', desc: 'JDK HttpClient,带 timeout / retry / cache / auth / circuitBreaker。', code: '"@class": "rest"' },
  { icon: '⚡', title: 'compile + trace + schema', desc: 'engine.compile() 5.7x 提速 / withTraceConsumer / withInputSchema。', code: 'engine.compile(tpl)' },
]

const depsXml = `<dependency>
  <groupId>fun.commons</groupId>
  <artifactId>template4j-core</artifactId>
  <version>0.5.0</version>
</dependency>`

function copyDeps() {
  navigator.clipboard.writeText(depsXml)
  ElMessage.success(t('home.copiedDeps'))
}

onMounted(renderHero)
watch(active, renderHero)
</script>

<style scoped>
.home { max-width: 1200px; margin: 0 auto; padding: 20px; }
.hero { display: grid; grid-template-columns: 1fr 1fr; gap: 40px; padding: 60px 0 80px; align-items: center; }
.hero-left h1 { font-size: 56px; font-weight: 800; line-height: 1.05; margin: 0 0 16px; letter-spacing: -0.02em; }
.accent { background: linear-gradient(135deg, #10b981, #a78bfa); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
.tagline { font-size: 18px; color: var(--el-text-color-secondary); margin-bottom: 28px; }
.cta { display: flex; gap: 12px; margin-bottom: 28px; }
.stats { display: flex; gap: 24px; font-size: 14px; color: var(--el-text-color-secondary); flex-wrap: wrap; }
.stats strong { color: var(--el-text-color-primary); font-size: 18px; margin-right: 4px; }
.hero-right { background: var(--el-fill-color-light); border-radius: 8px; padding: 12px; border: 1px solid var(--el-border-color); }
.hero-tabs { display: flex; gap: 4px; margin-bottom: 12px; }
.hero-tab { padding: 6px 12px; border: 0; background: transparent; cursor: pointer; border-radius: 4px; font-family: monospace; font-size: 12px; }
.hero-tab.active { background: var(--el-color-primary); color: white; }
.hero-split { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.hero-pane { background: var(--el-bg-color); border-radius: 4px; padding: 8px; min-height: 280px; overflow: auto; }
.pane-label { font-size: 11px; color: var(--el-text-color-secondary); margin-bottom: 6px; text-transform: uppercase; letter-spacing: 0.5px; }
.hero-pane pre { margin: 0; font-size: 11px; font-family: 'JetBrains Mono', monospace; white-space: pre-wrap; }

.features { padding: 60px 0; border-top: 1px solid var(--el-border-color); }
.features h2 { font-size: 32px; margin-bottom: 24px; }
.feature-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 20px; }
.feature-card { padding: 24px; border: 1px solid var(--el-border-color); border-radius: 8px; transition: transform 0.2s, border-color 0.2s; }
.feature-card:hover { transform: translateY(-2px); border-color: #10b981; }
.feature-icon { font-size: 32px; margin-bottom: 8px; }
.feature-card h3 { margin: 0 0 8px; font-family: monospace; }
.feature-card p { color: var(--el-text-color-secondary); font-size: 14px; margin: 0 0 12px; }
.feature-card code { display: inline-block; padding: 2px 8px; background: var(--el-fill-color); border-radius: 3px; font-size: 11px; color: #a78bfa; }

.cta-section { padding: 80px 0; text-align: center; border-top: 1px solid var(--el-border-color); }
.cta-section h2 { font-size: 36px; margin-bottom: 24px; }
.install { position: relative; max-width: 500px; margin: 0 auto; text-align: left; }
.install pre { display: block; background: #1a1a1a; color: #e5e7eb; padding: 16px; border-radius: 6px; font-family: monospace; font-size: 13px; margin: 0; }
.install .el-button { position: absolute; top: 8px; right: 8px; }
.hint { margin-top: 16px; color: var(--el-text-color-secondary); font-size: 14px; }

@media (max-width: 768px) {
  .hero { grid-template-columns: 1fr; padding: 30px 0; }
  .hero-left h1 { font-size: 36px; }
  .hero-split { grid-template-columns: 1fr; }
}
</style>
