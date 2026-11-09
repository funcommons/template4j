<template>
  <div class="learn" data-testid="learn-page">
    <header class="hero" data-testid="learn-hero">
      <h1 data-testid="learn-title">{{ t('learn.title') }}</h1>
      <p class="subtitle" data-testid="learn-subtitle">{{ activeMeta ? activeMeta.title : '' }}</p>
      <div class="meta" v-if="activeMeta">
        <el-tag size="small" type="info" effect="plain">{{ activeMeta.category }}</el-tag>
        <el-tag
          size="small"
          :type="difficultyType(activeMeta.difficulty)"
          effect="dark"
        >
          Lv.{{ activeMeta.difficulty }} · {{ difficultyLabel(activeMeta.difficulty) }}
        </el-tag>
        <code class="meta-source">{{ activeMeta.id }}</code>
      </div>
    </header>

    <div class="learn-layout" v-if="examples.length > 0">
      <aside class="sidebar" data-testid="learn-sidebar">
        <el-input
          v-model="searchKey"
          size="small"
          :placeholder="t('learn.search') || '搜索'"
          clearable
          class="sidebar-search"
          data-testid="learn-search"
        >
          <template #prefix><span class="search-icon">⌕</span></template>
        </el-input>
        <el-menu
          :default-active="currentId"
          @select="selectExample"
          class="sidebar-menu"
          data-testid="learn-menu"
        >
          <el-sub-menu
            v-for="cat in grouped"
            :key="cat.name"
            :index="cat.name"
            :data-testid="`learn-cat-${cat.name}`"
          >
            <template #title>
              <span class="cat-name">{{ cat.name }}</span>
              <el-badge :value="cat.items.length" class="cat-badge" type="info" />
            </template>
            <el-menu-item
              v-for="ex in cat.items"
              :key="ex.id"
              :index="ex.id"
              :data-testid="`learn-item-${ex.id}`"
            >
              <span class="ex-id">{{ ex.id }}</span>
              <span class="ex-title">{{ ex.title }}</span>
              <el-tag
                size="small"
                :type="difficultyType(ex.difficulty)"
                effect="plain"
                class="ex-diff"
              >{{ ex.difficulty }}</el-tag>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </aside>

      <main class="detail" v-if="detail" data-testid="learn-detail">
        <nav class="detail-nav" data-testid="learn-detail-nav">
          <el-button
            v-if="detail.prev_id"
            size="small"
            data-testid="learn-prev"
            @click="selectExample(detail.prev_id)"
          >← {{ t('learn.prev') }}</el-button>
          <el-button
            v-if="detail.next_id"
            size="small"
            data-testid="learn-next"
            @click="selectExample(detail.next_id)"
          >{{ t('learn.next') }} →</el-button>
        </nav>

        <el-card class="intro-card" shadow="never" data-testid="learn-intro">
          <h2 class="intro-title">{{ detail.title }}</h2>
          <p class="intro-desc">{{ detail.description }}</p>
          <div v-if="detail.source" class="intro-source">
            <span class="intro-source-label">source</span>
            <code>{{ detail.source }}</code>
          </div>
        </el-card>

        <div class="sections" data-testid="learn-sections">
          <template v-for="(section, idx) in filteredSections" :key="idx">
            <section
              v-if="section.type === 'markdown'"
              class="section section-markdown"
              :data-testid="`learn-md-section-${idx}`"
            >
              <div class="section-body">
                <RenderedMarkdown :content="String(section.content || '')" />
              </div>
            </section>

            <section
              v-else-if="section.type === 'json-renderer'"
              class="section section-jr"
              :class="{ 'jr-code-only': !isRunnable(section) }"
              :data-testid="`learn-jr-section-${idx}`"
              :data-runnable="isRunnable(section)"
            >
              <header class="jr-header">
                <span class="jr-icon">{{ isRunnable(section) ? '▶' : '⌨' }}</span>
                <span class="jr-title">{{ isRunnable(section) ? (t('learn.interactive') || '交互示例') : (t('learn.codeOnly') || '代码演示') }}</span>
                <span class="jr-tag">{{ isRunnable(section) ? 'json-renderer' : 'code-only' }}</span>
              </header>
              <div v-if="isRunnable(section)" class="jr-grid">
                <div class="jr-editor">
                  <div
                    v-for="v in section.content.variables || []"
                    :key="v.name"
                    class="var-row"
                    :data-testid="`learn-var-${idx}-${v.name}`"
                  >
                    <div class="var-label">
                      {{ v.label || v.name }}
                      <span class="var-type">{{ v.type || 'json' }}</span>
                      <button
                        type="button"
                        class="copy-btn"
                        :data-testid="`learn-copy-${idx}-${v.name}`"
                        :title="t('learn.copy') || '复制'"
                        @click="copyVar(idx, v.name)"
                      >{{ copiedKey === copyKeyFor(idx, v.name) ? '✓' : '⧉' }}</button>
                    </div>
                    <el-input
                      v-model="varModels[idx][v.name]"
                      type="textarea"
                      :autosize="{ minRows: 4, maxRows: 16 }"
                      class="code-input"
                      spellcheck="false"
                      :data-testid="`learn-input-${idx}-${v.name}`"
                    />
                  </div>
                </div>
                <div class="jr-output">
                  <div class="output-header">
                    <span class="output-title">{{ t('learn.result') || '渲染结果' }}</span>
                    <div class="output-actions">
                      <el-button size="small" @click="resetSection(idx)">{{ t('learn.reset') || '重置' }}</el-button>
                      <el-button size="small" @click="openSectionInTry(idx)">{{ t('learn.openInTry') }}</el-button>
                      <el-button
                        type="primary"
                        size="small"
                        :loading="running[idx]"
                        :data-testid="`learn-run-${idx}`"
                        @click="runSection(idx)"
                      >{{ t('learn.run') || '▶ 运行' }}</el-button>
                    </div>
                  </div>
                  <div
                    class="output-body"
                    :class="{ 'output-empty': !results[idx] && !errors[idx] }"
                    :data-testid="`learn-output-${idx}`"
                  >
                    <pre v-if="results[idx]" class="code-block" :data-testid="`learn-result-${idx}`"><code>{{ results[idx] }}</code></pre>
                    <pre v-else-if="errors[idx]" class="code-block code-error" :data-testid="`learn-error-${idx}`"><code>{{ errors[idx] }}</code></pre>
                    <span v-else class="output-placeholder" :data-testid="`learn-placeholder-${idx}`">{{ t('learn.runHint') || '点击「运行」查看渲染结果' }}</span>
                  </div>
                  <details v-if="section.content.code" class="code-attached">
                    <summary>{{ t('learn.javaCallCode') }}</summary>
                    <pre class="code-block code-java"><code>{{ section.content.code.trim() }}</code></pre>
                  </details>
                </div>
              </div>
              <div v-else class="jr-code-pane">
                <p v-if="section.content.variables?.length" class="jr-code-hint">{{ t('learn.codeHint') || '此示例未提供独立模板/数据,演示 Java 代码调用方式。' }}</p>
                <p v-else class="jr-code-hint">{{ t('learn.codeOnlyHint') || '此示例只有代码演示,无可执行模板/数据。' }}</p>
                <pre v-if="section.content.code" class="code-block code-java"><code>{{ section.content.code.trim() }}</code></pre>
                <div v-else class="jr-code-empty">{{ t('learn.noCode') || '（未提供 Java 代码片段）' }}</div>
              </div>
            </section>
          </template>
        </div>
      </main>
    </div>
    <el-empty v-else :description="t('learn.title')" />

    <el-card v-if="loadError" class="not-found-card" shadow="never" data-testid="learn-not-found">
      <h2>404</h2>
      <p>{{ loadError }}</p>
      <p class="not-found-msg">{{ t('learn.notFound') || '请求的示例不存在。' }}</p>
      <el-button type="primary" data-testid="learn-back-home" @click="goHome">
        {{ t('learn.backHome') || '返回介绍' }}
      </el-button>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, watch, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { listExamples, getExample } from '@/api/learn'
import { render } from '@/api/render'
import { useTryStore } from '@/stores/try'
import RenderedMarkdown from '@/components/learn/RenderedMarkdown.vue'
import type { ExampleMeta, ExampleDetail } from '@/types/api'
import {
  copyKeyFor,
  isRunnable,
  difficultyType,
  difficultyLabelKey,
  groupExamplesByCategory,
} from '@/utils/learn-helpers'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const tryStore = useTryStore()

const examples = ref<ExampleMeta[]>([])
const detail = ref<ExampleDetail | null>(null)
const currentId = ref('')
const searchKey = ref('')
const copiedKey = ref('')
const loadError = ref('') // populated by selectExample on 4xx

const varModels = reactive<Record<number, Record<string, string>>>({})
const results = reactive<Record<number, string>>({})
const errors = reactive<Record<number, string>>({})
const running = reactive<Record<number, boolean>>({})

const grouped = computed(() => {
  const other = t('learn.others') || '其他'
  return groupExamplesByCategory(examples.value, searchKey.value, other)
})

const filteredSections = computed(() => detail.value?.sections || [])

const activeMeta = computed<ExampleMeta | undefined>(() =>
  examples.value.find((e) => e.id === currentId.value)
)

function difficultyLabel(d: number) {
  return t(difficultyLabelKey(d))
}

onMounted(async () => {
  const data = await listExamples()
  examples.value = data.examples
  const id = (route.params.id as string) || examples.value[0]?.id
  if (id) selectExample(id)
})

watch(() => route.params.id, (id) => {
  if (id) selectExample(id as string)
})

watch(currentId, () => {
  results.value = {}
  errors.value = {}
  Object.keys(running).forEach((k) => delete running[+k])
})

async function selectExample(id: string) {
  currentId.value = id
  loadError.value = ''
  router.push(`/learn/${id}`)
  try {
    detail.value = await getExample(id)
    hydrateVars()
  } catch (e: any) {
    // Backend returned 4xx (typically 10404 "Example not found"). Show an
    // inline not-found state instead of leaving the user staring at the
    // previous example's content.
    detail.value = null
    loadError.value = e?.response?.data?.message || e?.message || 'Example not found'
  }
}

function goHome() {
  loadError.value = ''
  detail.value = null
  router.push('/learn')
  // fall back to intro-home in case the user was already on /learn landing
  examples.value.length > 0 && selectExample(examples.value[0].id)
}

function hydrateVars() {
  if (!detail.value || !detail.value.sections) return
  for (let i = 0; i < detail.value.sections.length; i++) {
    const sec = detail.value.sections[i]
    if (sec.type !== 'json-renderer') continue
    const vars = (sec.content?.variables || []) as Array<{ name: string; value?: any }>
    const m: Record<string, string> = {}
    for (const v of vars) m[v.name] = v.value == null ? '' : String(v.value)
    varModels[i] = m
  }
}

async function runSection(idx: number) {
  if (!detail.value) return
  const sec = detail.value.sections[idx]
  if (!sec || sec.type !== 'json-renderer') return
  const vars = (sec.content?.variables || []) as Array<{ name: string; type?: string }>
  let template = ''
  let data = '{}'
  let templateType: 'json' | 'yaml' | 'string' = (detail.value.template_type as any) || 'json'
  for (const v of vars) {
    const value = varModels[idx]?.[v.name] ?? ''
    if (v.name === 'template') {
      template = value
      if (v.type === 'string') templateType = 'string'
    } else if (v.name === 'data') {
      data = value
    }
  }
  running[idx] = true
  errors[idx] = ''
  try {
    let dataObj: Record<string, any> = {}
    // when template is a raw string, data is also a raw JSON literal — still parse it
    try {
      dataObj = JSON.parse(data || '{}')
    } catch {
      errors[idx] = t('learn.invalidJsonData')
      delete results[idx]
      running[idx] = false
      return
    }
    const r = await render({
      template_type: templateType,
      template,
      data: dataObj
    })
    // For 'string' template types, the engine returns a rendered String — show as-is.
    // For 'json' / 'yaml', pretty-print.
    if (typeof r.rendered === 'string') {
      results[idx] = r.rendered
    } else {
      results[idx] = JSON.stringify(r.rendered, null, 2)
    }
  } catch (e: any) {
    errors[idx] = e?.response?.data?.message || e?.message || t('learn.renderFailed')
    delete results[idx]
  } finally {
    running[idx] = false
  }
}

function openSectionInTry(idx: number) {
  if (!detail.value) return
  const sec = detail.value.sections[idx]
  if (!sec || sec.type !== 'json-renderer') return
  const vars = (sec.content?.variables || []) as Array<{ name: string; type?: string }>
  let template = ''
  let data = '{}'
  let templateType: 'json' | 'yaml' | 'string' = (detail.value.template_type as any) || 'json'
  for (const v of vars) {
    const value = varModels[idx]?.[v.name] ?? ''
    if (v.name === 'template') {
      template = value
      if (v.type === 'string') templateType = 'string'
    } else if (v.name === 'data') {
      data = value
    }
  }
  tryStore.setTemplateType(templateType)
  tryStore.setTemplate(template)
  tryStore.setData(data)
  router.push('/try')
}

function resetSection(idx: number) {
  hydrateVars()
  delete results[idx]
  delete errors[idx]
}

function copyVar(idx: number, name: string) {
  const text = varModels[idx]?.[name] ?? ''
  return copyText(text, copyKeyFor(idx, name))
}

async function copyText(text: string, key = '') {
  if (!text) return
  // flip UI immediately so users see feedback even if clipboard write fails
  copiedKey.value = key
  setTimeout(() => {
    if (copiedKey.value === key) copiedKey.value = ''
  }, 1200)
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    ElMessage.warning(t('learn.copyFailed'))
  }
}
</script>

<style scoped>
.learn { max-width: 1280px; margin: 0 auto; }
.hero { padding: 32px 16px 16px; text-align: left; border-bottom: 1px solid #ebeef5; margin-bottom: 16px; }
.hero h1 { margin: 0 0 4px 0; font-size: 1.6rem; color: #303133; }
.hero .subtitle { color: #606266; margin: 0 0 12px 0; font-size: 1rem; }
.hero .meta { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.meta-source { background: #f5f7fa; padding: 2px 8px; border-radius: 4px; font-size: 12px; color: #606266; }

.learn-layout { display: flex; gap: 24px; align-items: flex-start; padding: 0 16px 32px; }
.sidebar { width: 260px; flex-shrink: 0; align-self: flex-start; position: sticky; top: 16px; max-height: calc(100vh - 32px); overflow-y: auto; background: #fafbfc; border: 1px solid #ebeef5; border-radius: 8px; padding: 12px; }
.sidebar-search { margin-bottom: 8px; }
.sidebar-search :deep(input) { border-radius: 16px; }
.search-icon { color: #909399; font-size: 14px; }
.sidebar-menu { border-right: 0; background: transparent; }
.cat-name { flex: 1; font-weight: 600; font-size: 13px; }
.cat-badge { margin-left: 4px; }
.cat-badge :deep(sup) { background: #e8f3ff; color: #409eff; font-size: 10px; height: 16px; line-height: 16px; padding: 0 5px; border-radius: 8px; }
:deep(.el-sub-menu__title:hover), :deep(.el-menu-item:hover) { background-color: #f0f7ff !important; }
:deep(.el-menu-item.is-active) { background-color: #ecf5ff !important; color: #409eff !important; font-weight: 600; }
.ex-id { font-family: monospace; font-size: 11px; color: #909399; margin-right: 6px; padding: 1px 4px; background: #f5f7fa; border-radius: 3px; }
.ex-title { flex: 1; }
.ex-diff { margin-left: auto !important; }

.detail { flex: 1; min-width: 0; }
.detail-nav { display: flex; gap: 8px; margin-bottom: 16px; }

.intro-card { margin-bottom: 20px; border-radius: 10px; }
:deep(.intro-card .el-card__body) { padding: 20px 24px; }
.intro-title { margin: 0 0 8px 0; font-size: 1.25rem; color: #303133; }
.intro-desc { margin: 0 0 12px 0; color: #606266; line-height: 1.7; }
.intro-source { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #909399; }
.intro-source-label { text-transform: uppercase; letter-spacing: 0.05em; }
.intro-source code { background: #f5f7fa; padding: 2px 8px; border-radius: 4px; }

.sections { display: flex; flex-direction: column; gap: 16px; }

.section {
  border-radius: 10px;
  overflow: hidden;
}
.section-markdown {
  border: 1px solid #ebeef5;
  background: linear-gradient(to right, #fafbfc 0%, #ffffff 100%);
}
.section-markdown .section-body { padding: 14px 18px; }

.section-jr {
  border: 1px solid #dcdfe6;
  background: #fff;
}
.jr-header {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 16px;
  background: linear-gradient(90deg, #ecf5ff 0%, #f5f7fa 100%);
  border-bottom: 1px solid #ebeef5;
}
.jr-icon { color: #409eff; font-weight: 700; }
.jr-title { font-weight: 600; color: #303133; }
.jr-tag {
  margin-left: auto;
  font-family: monospace;
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 10px;
  background: #409eff;
  color: #fff;
}

.jr-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0;
}
.jr-editor {
  padding: 14px 16px;
  border-right: 1px solid #ebeef5;
  background: #fcfcfd;
}
.jr-output {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
}
.var-row { margin-bottom: 10px; }
.var-label {
  font-weight: 600;
  font-size: 12px;
  color: #606266;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.var-type {
  color: #909399;
  font-weight: 400;
  font-family: monospace;
}
.copy-btn {
  margin-left: auto;
  border: 1px solid #dcdfe6;
  background: #fff;
  border-radius: 4px;
  width: 22px;
  height: 22px;
  cursor: pointer;
  font-size: 12px;
  color: #606266;
  line-height: 1;
}
.copy-btn:hover { background: #ecf5ff; color: #409eff; border-color: #b3d8ff; }

.code-input :deep(textarea) {
  font-family: 'JetBrains Mono', 'SF Mono', Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.55;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 6px;
  padding: 10px 12px;
  tab-size: 2;
}
.code-input :deep(textarea)::selection { background: #264f78; }

.output-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 8px;
}
.output-title { font-weight: 600; font-size: 12px; color: #606266; text-transform: uppercase; letter-spacing: 0.05em; }
.output-actions { display: flex; gap: 6px; }
.output-body {
  min-height: 96px;
  max-height: 360px;
  overflow: auto;
  background: #1e1e1e;
  border-radius: 6px;
  padding: 10px 12px;
  flex: 1;
}
.output-empty { display: flex; align-items: center; justify-content: center; }
.output-placeholder { color: #6c6c6c; font-size: 13px; font-style: italic; }

.code-block {
  margin: 0;
  white-space: pre;
  font-family: 'JetBrains Mono', 'SF Mono', Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.55;
  color: #d4d4d4;
  counter-reset: lineno;
}
.code-block code { display: block; }
.code-block code::before {
  counter-increment: lineno;
  content: counter(lineno);
  display: inline-block;
  width: 2.5em;
  margin-right: 1em;
  text-align: right;
  color: #5a5a5a;
  border-right: 1px solid #3a3a3a;
  padding-right: 0.6em;
  user-select: none;
}
.code-error code { color: #f48771; }

.not-found-card { max-width: 480px; margin: 60px auto; text-align: center; border-radius: 10px; }
.not-found-card :deep(.el-card__body) { padding: 48px 32px; }
.not-found-card h2 { font-size: 4em; margin: 0 0 12px; color: #909399; font-weight: 200; }
.not-found-card p { color: #606266; margin: 8px 0; }
.not-found-msg { font-size: 14px; color: #909399; }
.code-java { background: #1e1e1e; border-radius: 6px; padding: 10px 12px; max-height: 280px; overflow: auto; }

.code-attached {
  margin-top: 10px;
  border-top: 1px dashed #ebeef5;
  padding-top: 8px;
}
.code-attached summary {
  cursor: pointer;
  font-size: 12px;
  color: #606266;
  padding: 4px 0;
  user-select: none;
}
.code-attached summary:hover { color: #409eff; }

.section-jr.jr-code-only { background: #fffbef; }
.section-jr.jr-code-only .jr-header {
  background: linear-gradient(90deg, #fdf6ec 0%, #f5f7fa 100%);
}
.section-jr.jr-code-only .jr-tag { background: #e6a23c; }
.jr-code-pane { padding: 14px 16px; }
.jr-code-hint {
  margin: 0 0 10px 0;
  color: #606266;
  font-size: 13px;
}
.jr-code-empty {
  padding: 12px;
  background: #fafafa;
  border-radius: 4px;
  color: #909399;
  font-size: 13px;
  text-align: center;
}

@media (max-width: 960px) {
  .learn-layout { flex-direction: column; padding: 0 12px 24px; gap: 16px; }
  /* 无全局 reset, content-box 下 padding/border 会额外撑出 +6px, 溢出修复 (e2e smoke @600) */
  .sidebar { width: 100%; max-height: none; position: static; box-sizing: border-box; }
  .jr-grid { grid-template-columns: 1fr; }
  .jr-editor { border-right: 0; border-bottom: 1px solid #ebeef5; }
  .hero { padding: 20px 12px 12px; }
  .hero h1 { font-size: 1.35rem; }
  .section-jr .jr-grid { grid-template-columns: 1fr; }
  .code-block { font-size: 12px; }
}
@media (max-width: 640px) {
  .hero { padding: 16px 8px 8px; }
  .hero h1 { font-size: 1.2rem; }
  .intro-card :deep(.el-card__body) { padding: 14px 16px; }
  .intro-title { font-size: 1.1rem; }
  .section-jr .jr-header { padding: 8px 12px; }
  .jr-editor, .jr-output { padding: 12px; }
  .output-actions { flex-wrap: wrap; }
}
</style>
