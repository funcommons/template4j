<template>
  <div class="try-page">
    <h2>{{ t('try.title') }}</h2>
    <div class="controls">
      <el-radio-group v-model="store.templateType" size="small">
        <el-radio-button value="json">JSON</el-radio-button>
        <el-radio-button value="yaml">YAML</el-radio-button>
      </el-radio-group>
      <el-button type="primary" @click="doRender" :loading="loading">{{ t('try.render') }}</el-button>
      <el-button @click="clear">{{ t('try.clear') }}</el-button>
    </div>
    <div class="editors">
      <div class="editor-pane">
        <h4>{{ t('try.template') }}</h4>
        <el-input
          v-model="store.template"
          type="textarea"
          :rows="15"
          placeholder='{"msg": "{@user.name}"}'
          class="code-input"
        />
      </div>
      <div class="editor-pane">
        <h4>{{ t('try.data') }}</h4>
        <el-input
          v-model="store.data"
          type="textarea"
          :rows="15"
          placeholder='{"user": {"name": "demo"}}'
          class="code-input"
        />
        <div v-if="dataError" style="color: #f56c6c; font-size: 12px; margin-top: 4px;">{{ dataError }}</div>
      </div>
    </div>
    <div v-if="store.lastResult" class="result">
      <h4>{{ t('try.result') }} ({{ store.lastTiming }} ms)</h4>
      <pre class="code-block">{{ prettyResult(store.lastResult) }}</pre>
      <div v-if="store.trace.length > 0">
        <h4>{{ t('try.trace') }}</h4>
        <ul>
          <li v-for="(step, i) in store.trace" :key="i">{{ step }}</li>
        </ul>
      </div>
    </div>
    <div v-if="store.history.length > 0" class="history">
      <h4>{{ t('try.history') }}</h4>
      <div v-for="(h, i) in store.history.slice(0, 5)" :key="i" class="history-item" @click="restoreHistory(h)">
        <span>{{ new Date(h.ts).toLocaleTimeString() }}</span>
        <span>{{ summarize(h.rendered) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTryStore } from '@/stores/try'
import { tryRender } from '@/api/try'
import { ElMessage } from 'element-plus'

const { t } = useI18n()
const store = useTryStore()
const loading = ref(false)
const dataError = ref('')

/** rendered 可能已是 JSON 字符串 (后端序列化产物), 解析后再美化, 避免双重转义 */
function prettyResult(r: any): string {
  if (typeof r === 'string') {
    try { return JSON.stringify(JSON.parse(r), null, 2) } catch { return r }
  }
  return JSON.stringify(r, null, 2)
}
function summarize(r: any): string {
  const s = typeof r === 'string' ? r : JSON.stringify(r)
  return s.slice(0, 60) + '...'
}

async function doRender() {
  loading.value = true
  try {
    let dataObj: Record<string, any> = {}
    dataError.value = ''
    try {
      dataObj = JSON.parse(store.data || '{}')
    } catch {
      dataError.value = t('try.invalidJsonData')
      loading.value = false
      return
    }

    const result = await tryRender({
      template_type: store.templateType,
      template: store.template,
      data: dataObj,
      options: { with_trace: true }
    })
    store.setResult(result.rendered, result.timing_ms, result.trace)
  } catch (e: any) {
    store.setResult({ error: e.message || t('try.renderFailed') }, 0, [])
  } finally {
    loading.value = false
  }
}

function clear() {
  store.setTemplate('')
  store.setData('{}')
  store.setResult(null, 0, [])
  dataError.value = ''
}

function restoreHistory(h: any) {
  store.setTemplate(h.template)
  store.setData(h.data)
}
</script>

<style scoped>
.controls { display: flex; gap: 12px; align-items: center; margin: 16px 0; }
.editors { display: flex; gap: 16px; }
.editor-pane { flex: 1; }
.code-input :deep(textarea) { font-family: monospace; font-size: 13px; }
.result { margin-top: 20px; }
.code-block {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
  font-size: 13px;
  max-height: 400px;
  overflow-y: auto;
}
h4 { margin: 12px 0 6px 0; }
.history-item {
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  display: flex;
  gap: 12px;
  font-size: 13px;
}
.history-item:hover { background: #f0f0f0; }
</style>
