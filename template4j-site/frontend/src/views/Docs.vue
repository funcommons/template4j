<template>
  <div class="docs">
    <h2>{{ t('docs.title') }}</h2>
    <el-card>
      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else-if="detail">
        <h3>{{ detail.title }}</h3>
        <p>{{ detail.description }}</p>

        <div class="sections">
          <template v-for="(section, idx) in detail.sections || []" :key="idx">
            <div v-if="section.type === 'markdown'" class="md-section">
              <pre class="markdown">{{ section.content }}</pre>
            </div>
            <div v-else-if="section.type === 'json-renderer'" class="jr-section">
              <div
                v-for="v in section.content.variables || []"
                :key="v.name"
                class="var-row"
              >
                <div class="var-label">{{ v.label || v.name }} <span class="var-type">({{ v.type || 'json' }})</span></div>
                <el-input
                  v-model="varModels[idx][v.name]"
                  type="textarea"
                  :rows="6"
                  class="code-input"
                />
              </div>
              <div class="jr-actions">
                <el-button
                  type="primary"
                  size="small"
                  :loading="running[idx]"
                  @click="runSection(idx)"
                >▶ 运行</el-button>
                <el-button size="small" @click="openSectionInTry(idx)">{{ t('learn.openInTry') }}</el-button>
              </div>
              <div v-if="results[idx]" class="jr-result">
                <div class="var-label">result</div>
                <pre class="code-block">{{ results[idx] }}</pre>
              </div>
              <div v-if="section.content.code" class="var-row">
                <div class="var-label">Java 调用代码</div>
                <pre class="code-block">{{ section.content.code.trim() }}</pre>
              </div>
            </div>
          </template>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getExample } from '@/api/learn'
import { render } from '@/api/render'
import { useTryStore } from '@/stores/try'
import type { ExampleDetail } from '@/types/api'

const { t } = useI18n()
const router = useRouter()
const tryStore = useTryStore()

const loading = ref(true)
const error = ref('')
const detail = ref<ExampleDetail | null>(null)
const varModels = reactive<Record<number, Record<string, string>>>({})
const results = reactive<Record<number, string>>({})
const running = reactive<Record<number, boolean>>({})

onMounted(async () => {
  try {
    detail.value = await getExample('intro-home')
    hydrateVars()
  } catch (e: any) {
    error.value = '无法加载首页文档: ' + (e?.message || String(e))
  } finally {
    loading.value = false
  }
})

function hydrateVars() {
  if (!detail.value || !detail.value.sections) return
  for (let i = 0; i < detail.value.sections.length; i++) {
    const sec = detail.value.sections[i]
    if (sec.type !== 'json-renderer') continue
    const vars = (sec.content?.variables || []) as Array<{ name: string; value?: any }>
    const m: Record<string, string> = {}
    for (const v of vars) m[v.name] = v.value == null ? '' : String(v.value)
    varModels[i] = m
    delete results[i]
  }
}

async function runSection(idx: number) {
  if (!detail.value) return
  const sec = detail.value.sections[idx]
  if (!sec || sec.type !== 'json-renderer') return
  const vars = (sec.content?.variables || []) as Array<{ name: string }>
  let template = ''
  let data = '{}'
  for (const v of vars) {
    const value = varModels[idx]?.[v.name] ?? ''
    if (v.name === 'template') template = value
    else if (v.name === 'data') data = value
  }
  running[idx] = true
  try {
    let dataObj: Record<string, any> = {}
    try { dataObj = JSON.parse(data || '{}') } catch { results[idx] = 'data 字段不是合法 JSON'; running[idx] = false; return }
    const r = await render({
      template_type: detail.value.template_type || 'json',
      template,
      data: dataObj
    })
    results[idx] = JSON.stringify(r.rendered, null, 2)
  } catch (e: any) {
    results[idx] = '渲染失败: ' + (e?.message || JSON.stringify(e))
  } finally {
    running[idx] = false
  }
}

function openSectionInTry(idx: number) {
  if (!detail.value) return
  const sec = detail.value.sections[idx]
  if (!sec || sec.type !== 'json-renderer') return
  const vars = (sec.content?.variables || []) as Array<{ name: string }>
  let template = ''
  let data = '{}'
  for (const v of vars) {
    const value = varModels[idx]?.[v.name] ?? ''
    if (v.name === 'template') template = value
    else if (v.name === 'data') data = value
  }
  tryStore.setTemplateType(detail.value.template_type as 'json' | 'yaml')
  tryStore.setTemplate(template)
  tryStore.setData(data)
  router.push('/try')
}
</script>

<style scoped>
.code-block {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 4px;
  font-size: 13px;
  overflow-x: auto;
}
.markdown {
  background: #fafafa;
  padding: 10px 12px;
  border-radius: 4px;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 8px 0;
  border-left: 3px solid #409eff;
}
.sections { display: flex; flex-direction: column; gap: 16px; margin-top: 12px; }
.jr-section { border: 1px solid #ebeef5; border-radius: 6px; padding: 12px; background: #fff; }
.var-row { margin-bottom: 8px; }
.var-label { font-weight: 600; margin-bottom: 4px; color: #303133; }
.var-type { color: #909399; font-weight: 400; font-size: 12px; }
.jr-actions { display: flex; gap: 8px; margin: 8px 0; }
.jr-result { margin-top: 8px; }
.loading, .error { padding: 24px; text-align: center; color: #606266; }
.error { color: #f56c6c; }
</style>
