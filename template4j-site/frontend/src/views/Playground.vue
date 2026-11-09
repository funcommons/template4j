<template>
  <div class="playground">
    <header class="pg-header">
      <h2>Playground</h2>
      <div class="pg-actions">
        <el-select v-model="preset" :placeholder="t('playground.loadPreset')" size="small" @change="loadPreset" clearable>
          <el-option v-for="p in presets" :key="p.name" :label="p.name" :value="p.name" />
        </el-select>
        <el-radio-group v-model="templateType" size="small">
          <el-radio-button value="json">JSON</el-radio-button>
          <el-radio-button value="yaml">YAML</el-radio-button>
          <el-radio-button value="string">String</el-radio-button>
        </el-radio-group>
        <el-button size="small" @click="toggleTrace">
          {{ t('playground.tabTrace') }}: {{ traceOn ? 'ON' : 'OFF' }}
        </el-button>
        <el-button size="small" @click="formatData" :disabled="templateType === 'string'">{{ t('playground.formatJson') }}</el-button>
        <el-button size="small" @click="clearAll">{{ t('playground.clear') }}</el-button>
        <el-button size="small" @click="copyShareUrl">{{ t('playground.shareUrl') }}</el-button>
        <el-button type="primary" size="small" @click="render" :loading="loading">
          ▶ Render
        </el-button>
      </div>
    </header>

    <div class="pg-status">
      <span class="pg-stat">
        <span class="pg-stat-label">{{ t('playground.renders') }}:</span>
        <span class="pg-stat-val">{{ renderCount }}</span>
      </span>
      <span v-if="lastDuration" class="pg-stat">
        <span class="pg-stat-label">{{ t('playground.last') }}:</span>
        <span class="pg-stat-val">{{ lastDuration }} {{ t('playground.ms') }}</span>
      </span>
      <span v-if="dirty" class="pg-stat pg-stat-warn">{{ t('playground.unsaved') }}</span>
      <span v-else class="pg-stat pg-stat-ok">{{ t('playground.saved') }}</span>
      <span class="pg-stat-spacer"></span>
      <span class="pg-stat-hint">{{ t('playground.shortcutHint') }}</span>
    </div>

    <div class="pg-grid">
      <!-- Template pane -->
      <div class="pg-pane">
        <div class="pg-pane-title">
          <span>{{ t('common.template') }}</span>
          <span class="pg-pane-meta">{{ template.length }} {{ t('playground.charsCount') }}</span>
        </div>
        <div class="pg-snippets">
          <span class="pg-snippet-label">{{ t('playground.snippetsLabel') }}:</span>
          <el-button v-for="s in snippets" :key="s.label" size="small" link @click="insertSnippet(s.value)">
            {{ s.label }}
          </el-button>
        </div>
        <textarea
          ref="templateRef"
          v-model="template"
          class="pg-code"
          spellcheck="false"
          @keydown.ctrl.enter.prevent="render"
          @keydown.meta.enter.prevent="render"
          @keydown.tab.prevent="onTabKey"
          style="display: none"
        />
        <CodeEditor
          v-model="template"
          :language="templateType === 'yaml' ? 'yaml' : (templateType === 'string' ? 'string' : 'json')"
        />
      </div>

      <!-- Data pane -->
      <div class="pg-pane">
        <div class="pg-pane-title">
          <span>{{ t('common.data') }} (JSON)</span>
          <span :class="['pg-pane-meta', dataValid ? '' : 'pg-pane-meta-err']">
            {{ dataValid ? t('playground.valid') : t('playground.invalid') + (dataError ? ': ' + dataError : '') }}
          </span>
        </div>
        <div
          class="pg-dropzone"
          :class="{ 'pg-dropzone-active': dragActive }"
          @dragenter.prevent="onDragEnter"
          @dragover.prevent="onDragOver"
          @dragleave.prevent="onDragLeave"
          @drop.prevent="onDrop"
        >
          <textarea
            v-model="data"
            class="pg-code"
            spellcheck="false"
            @keydown.ctrl.enter.prevent="render"
            @keydown.meta.enter.prevent="render"
            @input="validateData"
          />
          <div v-if="dragActive" class="pg-drop-overlay">
            <div class="pg-drop-msg">Drop {{ dropType }} file here</div>
          </div>
        </div>
        <div class="pg-file-bar">
          <input
            ref="fileInput"
            type="file"
            accept=".json,.yaml,.yml,.txt"
            style="display: none"
            @change="onFileSelected"
          />
          <el-button size="small" link @click="openFilePicker">📁 {{ t('playground.loadFile') }}</el-button>
          <span class="pg-file-hint">{{ t('playground.dragHint') }}</span>
        </div>
        <div v-if="dataError" class="pg-err">{{ dataError }}</div>
      </div>

      <!-- Output pane -->
      <div class="pg-pane">
        <div class="pg-pane-title">
          <el-radio-group v-model="outputTab" size="small">
            <el-radio-button value="output">{{ t('playground.tabOutput') }}</el-radio-button>
            <el-radio-button v-if="traceOn" value="trace">{{ t('playground.tabTrace') }} ({{ trace.length }})</el-radio-button>
            <el-radio-button value="raw">{{ t('playground.tabRaw') }}</el-radio-button>
          </el-radio-group>
          <span v-if="timing" class="pg-timing">{{ timing }} {{ t('playground.ms') }}</span>
        </div>

        <div v-show="outputTab === 'output'" class="pg-pane-body">
          <pre v-if="!errorMsg" class="pg-out" ref="outputRef">{{ output }}</pre>
          <div v-else class="pg-err-block">
            <div class="pg-err-title">{{ t('playground.renderError') }}</div>
            <pre class="pg-err-body">{{ errorMsg }}</pre>
          </div>
        </div>

        <div v-show="outputTab === 'trace'" class="pg-trace">
          <div v-if="!trace.length" class="pg-empty">{{ t('playground.noTrace') }}</div>
          <div v-for="(ev, i) in trace" :key="i" class="pg-trace-row" :class="'pg-trace-' + ev.type.toLowerCase()">
            <span class="pg-trace-type">[{{ ev.type }}]</span>
            <span class="pg-trace-path">{{ ev.path }}</span>
            <span class="pg-trace-msg">{{ ev.message }}</span>
            <span v-if="ev.durationMs > 0" class="pg-trace-dur">{{ ev.durationMs }} ms</span>
          </div>
        </div>

        <div v-show="outputTab === 'raw'" class="pg-pane-body">
          <pre class="pg-out">{{ rawOutput }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import CodeEditor from '@/components/CodeEditor.vue'

const { t } = useI18n()

const template = ref('')
const data = ref('{\n  "name": "alice",\n  "age": 30\n}')
const templateType = ref('json')
const output = ref('// Press ▶ Render or Ctrl+Enter')
const rawOutput = ref('')
const timing = ref(0)
const lastDuration = ref(0)
const renderCount = ref(0)
const trace = ref<any[]>([])
const traceOn = ref(false)
const loading = ref(false)
const dataError = ref('')
const dataValid = ref(true)
const errorMsg = ref('')
const preset = ref('')
const outputTab = ref<'output' | 'trace' | 'raw'>('output')
const dirty = ref(false)

const templateRef = ref<HTMLTextAreaElement | null>(null)
const outputRef = ref<HTMLPreElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const dragActive = ref(false)
const dropType = ref('JSON')

function onDragEnter(e: DragEvent) {
  dragActive.value = true
  const file = e.dataTransfer?.items?.[0]
  if (file) {
    dropType.value = file.type.includes('yaml') || file.type.includes('yml') ? 'YAML' : 'JSON'
  }
}
function onDragOver(e: DragEvent) {
  e.preventDefault()
  dragActive.value = true
}
function onDragLeave(e: DragEvent) {
  // only deactivate when leaving the dropzone itself (not child)
  if (e.relatedTarget && (e.currentTarget as HTMLElement).contains(e.relatedTarget as Node)) return
  dragActive.value = false
}

async function onDrop(e: DragEvent) {
  dragActive.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file) await loadFile(file)
}

function openFilePicker() {
  fileInput.value?.click()
}

async function onFileSelected(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) await loadFile(file)
  // reset so picking same file again re-fires
  target.value = ''
}

async function loadFile(file: File) {
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error(t('playground.fileTooBig'))
    return
  }
  try {
    const text = await file.text()
    const isYaml = /\.(ya?ml)$/i.test(file.name) || /^---/.test(text.trim())
    if (isYaml) {
      // YAML → convert to JSON via a simple line-based parser (only handles flat mappings + sequences)
      try {
        data.value = yamlToJsonString(text)
      } catch (err: any) {
        ElMessage.error(t('playground.fileLoadFail') + ': ' + err.message)
        return
      }
    } else {
      data.value = text
    }
    validateData()
    ElMessage.success(`${t('playground.fileLoaded')}: ${file.name}`)
    render()
  } catch (e: any) {
    ElMessage.error(t('playground.fileLoadFail') + ': ' + (e.message || 'unknown'))
  }
}

/** 极简 YAML → JSON 转换 (只支持单层 mapping / sequence,够 Playground 试数据用)。 */
function yamlToJsonString(yaml: string): string {
  const lines = yaml.split(/\r?\n/).filter(l => l.trim() && !l.trim().startsWith('#'))
  // sequence?
  if (lines.every(l => /^\s*-\s/.test(l))) {
    const items = lines.map(l => {
      const m = l.match(/^\s*-\s*(.+)$/)!
      return parseScalar(m[1].trim())
    })
    return JSON.stringify(items, null, 2)
  }
  // mapping
  const obj: any = {}
  for (const l of lines) {
    const m = l.match(/^([A-Za-z_][\w\-]*)\s*:\s*(.*)$/)
    if (m) obj[m[1]] = parseScalar(m[2])
  }
  return JSON.stringify(obj, null, 2)
}

function parseScalar(s: string): any {
  if (s === undefined || s === '') return ''
  s = s.trim().replace(/^["']|["']$/g, '')
  if (s === 'true') return true
  if (s === 'false') return false
  if (s === 'null' || s === '~') return null
  if (/^-?\d+$/.test(s)) return parseInt(s, 10)
  if (/^-?\d+\.\d+$/.test(s)) return parseFloat(s)
  return s
}

const STORAGE_KEY = 'template4j.playground.v2'

const presets = [
  {
    name: 'Basic placeholder',
    template: '{\n  "msg": "Hello, {@name}!",\n  "age": {@age}\n}',
    data: '{ "name": "alice", "age": 30 }',
    type: 'json',
  },
  {
    name: 'if / each',
    template: `{
  "@class": "if",
  "select": "{@user.vip}",
  "is": "true",
  "then": {
    "msg": "Welcome back, {$user.name}",
    "perks": {
      "@class": "each",
      "select": "{$user.perks}",
      "body": "{{$item}}"
    }
  },
  "else": { "msg": "Hi, {$user.name}" }
}`,
    data: '{ "user": { "name": "alice", "vip": true, "perks": ["free-shipping", "discount"] } }',
    type: 'json',
  },
  {
    name: 'Script {=...}',
    template: '{\n  "doubled": "{= @n * 2 }",\n  "tier": "{= @n > 100 ? \\"vip\\" : \\"normal\\" }"\n}',
    data: '{ "n": 42 }',
    type: 'json',
  },
  {
    name: 'Parallel mock',
    template: `{
  "@class": "parallel",
  "tasks": {
    "user":   { "@class": "rest", "url": "/api/user?id={@userId}" },
    "orders": { "@class": "rest", "url": "/api/orders?id={@userId}" }
  },
  "body": {
    "userId": "{$user.id}",
    "orderCount": "{= $orders.size() }"
  }
}`,
    data: '{ "userId": "u-1" }',
    type: 'json',
  },
]

const snippets = [
  { label: '{@path}', value: '{@path}' },
  { label: '{$var}', value: '{$var}' },
  { label: '{= expr}', value: '{= @a + @b }' },
  { label: '{% %}', value: '{% return @x %}' },
  { label: 'if', value: '"@class": "if",\n  "select": "...",\n  "then": {...}' },
  { label: 'each', value: '"@class": "each",\n  "select": "...",\n  "body": "..."' },
  { label: 'rest', value: '"@class": "rest",\n  "url": "..."' },
  { label: 'try', value: '"@class": "try",\n  "select": {...},\n  "fallback": {...}' },
]

/** rendered 可能是 JSON 字符串或对象, 统一转为可读展示文本 */
function prettyRendered(r: any): string {
  if (typeof r === 'string') return r
  try { return JSON.stringify(r, null, 2) } catch { return String(r) }
}
function rawRendered(r: any): string {
  if (typeof r === 'string') return r
  try { return JSON.stringify(r) } catch { return String(r) }
}

async function render() {
  loading.value = true
  dataError.value = ''
  errorMsg.value = ''
  let dataObj: any = {}
  if (templateType.value !== 'string') {
    try {
      dataObj = data.value.trim() ? JSON.parse(data.value) : {}
      dataValid.value = true
    } catch (e: any) {
      dataError.value = t('playground.invalidJson') + e.message
      dataValid.value = false
      loading.value = false
      return
    }
  }
  const t0 = performance.now()
  // 优先用 SSE 流式接口 (/api/try/stream): 边渲染边更新 trace
  // 后端在简单模板上可能与 /api/try 等价,这里默认走流式以提供"实时进度"反馈
  const useStream = true
  try {
    if (useStream) {
      await renderStream(dataObj, t0)
    } else {
      const res = await axios.post('/api/try', {
        template_type: templateType.value,
        template: template.value,
        data: dataObj,
        options: { with_trace: traceOn.value },
      })
      const r = res.data?.data || {}
      output.value = prettyRendered(r.rendered)
      rawOutput.value = rawRendered(r.rendered)
      timing.value = r.timing_ms || 0
      lastDuration.value = Math.round(performance.now() - t0)
      trace.value = r.trace || []
      renderCount.value++
      if (traceOn.value && trace.value.length) outputTab.value = 'trace'
    }
    dirty.value = false
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || e.message || 'Unknown error'
    output.value = ''
    rawOutput.value = ''
    trace.value = []
  } finally {
    loading.value = false
  }
}

/** SSE 流式渲染: 实时接收 trace 事件,完成后写最终结果。 */
async function renderStream(dataObj: any, t0: number) {
  trace.value = []
  // 走 fetch + ReadableStream 而不是 EventSource (POST 不能用 EventSource)
  const resp = await fetch('/api/try/stream', {
    method: 'POST',
    // 接受 JSON: 服务端 4xx 错误信封需要以 application/json 协商返回, 否则落到 500
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream, application/json' },
    body: JSON.stringify({
      template_type: templateType.value,
      template: template.value,
      data: dataObj,
      options: { with_trace: traceOn.value },
    }),
  })
  if (!resp.ok) {
    // 服务端 4xx (如模板超限): body 是 JSON 信封而非 SSE, 提取 message 抛给错误面板
    const text = await resp.text().catch(() => '')
    let msg = `HTTP ${resp.status}`
    try { msg = JSON.parse(text)?.message || msg } catch { /* keep */ }
    throw new Error(msg)
  }
  if (!resp.body) throw new Error('No response body')
  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buf = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buf += decoder.decode(value, { stream: true })
    // SSE 消息以 "\n\n" 分隔
    let idx
    while ((idx = buf.indexOf('\n\n')) >= 0) {
      const msg = buf.slice(0, idx)
      buf = buf.slice(idx + 2)
      const lines = msg.split('\n')
      let eventName = ''
      let dataLine = ''
      for (const line of lines) {
        if (line.startsWith('event:')) eventName = line.slice(6).trim()
        else if (line.startsWith('data:')) dataLine += line.slice(5).trim()
      }
      if (!dataLine) continue
      const payload = JSON.parse(dataLine)
      if (eventName === 'done') {
        // rendered 可能已是 JSON 字符串 (后端序列化产物), 直接展示, 避免双重转义
        output.value = prettyRendered(payload.rendered)
        rawOutput.value = rawRendered(payload.rendered)
        timing.value = 0
        lastDuration.value = Math.round(performance.now() - t0)
        renderCount.value++
        if (traceOn.value && trace.value.length) outputTab.value = 'trace'
      } else if (eventName === 'error') {
        errorMsg.value = payload.message || JSON.stringify(payload)
      } else if (eventName === 'hello') {
        // ignore
      } else {
        // 普通 trace 事件
        trace.value.push(payload)
      }
    }
  }
}

function loadPreset(name: string) {
  if (!name) return
  const p = presets.find(p => p.name === name)
  if (!p) return
  template.value = p.template
  data.value = p.data
  templateType.value = p.type
  preset.value = name
  validateData()
  render()
}

function toggleTrace() {
  traceOn.value = !traceOn.value
  if (!traceOn.value) outputTab.value = 'output'
  render()
}

function clearAll() {
  template.value = ''
  data.value = '{}'
  output.value = '// Press ▶ Render'
  rawOutput.value = ''
  trace.value = []
  errorMsg.value = ''
  dataError.value = ''
  dataValid.value = true
  timing.value = 0
  preset.value = ''
  templateRef.value?.focus()
}

function formatData() {
  if (templateType.value === 'string') return
  try {
    const parsed = JSON.parse(data.value || '{}')
    data.value = JSON.stringify(parsed, null, 2)
    dataValid.value = true
    dataError.value = ''
    ElMessage.success(t('playground.formatSuccess'))
  } catch (e: any) {
    dataError.value = t('playground.cannotFormat') + e.message
    ElMessage.error(t('playground.formatInvalid'))
  }
}

function validateData() {
  if (templateType.value === 'string') {
    dataValid.value = true
    return
  }
  try {
    JSON.parse(data.value || '{}')
    dataValid.value = true
    dataError.value = ''
  } catch (e: any) {
    dataValid.value = false
    dataError.value = e.message
  }
}

function insertSnippet(value: string) {
  const ta = templateRef.value
  if (!ta) {
    template.value += '\n' + value
    return
  }
  const start = ta.selectionStart
  const end = ta.selectionEnd
  template.value = template.value.slice(0, start) + value + template.value.slice(end)
  // restore cursor
  setTimeout(() => {
    ta.focus()
    const pos = start + value.length
    ta.setSelectionRange(pos, pos)
  }, 0)
}

function onTabKey(e: KeyboardEvent) {
  const ta = e.target as HTMLTextAreaElement
  const start = ta.selectionStart
  const end = ta.selectionEnd
  // insert 2 spaces
  ta.value = ta.value.slice(0, start) + '  ' + ta.value.slice(end)
  template.value = ta.value
  setTimeout(() => {
    ta.focus()
    ta.setSelectionRange(start + 2, start + 2)
  }, 0)
}

function copyShareUrl() {
  const state = JSON.stringify({ t: template.value, d: data.value, y: templateType.value })
  const hash = btoa(unescape(encodeURIComponent(state)))
  // pathname 已含当前路径, 直接拼 origin + 绝对路径, 避免 /playgroundplayground
  const url = `${location.origin}/playground?state=${hash}`
  navigator.clipboard.writeText(url).then(
    () => ElMessage.success(t('playground.shareSuccess')),
    () => ElMessage.warning(t('playground.shareFail') + ' - URL: ' + url)
  )
}

function loadFromUrl() {
  const m = location.search.match(/state=([^&]+)/)
  if (!m) return
  try {
    const s = JSON.parse(decodeURIComponent(escape(atob(m[1]))))
    if (s.t) template.value = s.t
    if (s.d) data.value = s.d
    if (s.y) templateType.value = s.y
  } catch {}
}

function saveLocal() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      template: template.value,
      data: data.value,
      type: templateType.value,
      traceOn: traceOn.value,
      ts: Date.now(),
    }))
  } catch {}
}

function loadLocal() {
  try {
    const s = localStorage.getItem(STORAGE_KEY)
    if (!s) return
    const obj = JSON.parse(s)
    if (obj.template) template.value = obj.template
    if (obj.data) data.value = obj.data
    if (obj.type) templateType.value = obj.type
    if (typeof obj.traceOn === 'boolean') traceOn.value = obj.traceOn
  } catch {}
}

// Global key handler: Ctrl+S to save preset, Ctrl+Enter to render
function onGlobalKey(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault()
    saveLocal()
    dirty.value = false
    ElMessage.success(t('playground.savedToStorage'))
  }
  // 快捷键必须挂在 window 上: 可见的 CodeEditor 不会冒泡到隐藏的 fallback textarea
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
    e.preventDefault()
    if (!loading.value) render()
  }
}

onMounted(() => {
  loadFromUrl()
  loadLocal()
  validateData()
  if (!template.value) loadPreset(presets[0].name)
  window.addEventListener('keydown', onGlobalKey)
})

watch([template, data, templateType], () => {
  dirty.value = true
  validateData()
  // auto-render debounce (simple, no leading render)
  clearTimeout((window as any)._pgTimer)
  ;(window as any)._pgTimer = setTimeout(() => {
    if (dataValid.value && template.value.trim()) render()
  }, 600)
})
</script>

<style scoped>
.playground { padding: 16px 24px; height: 100%; display: flex; flex-direction: column; }
.pg-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; flex-wrap: wrap; gap: 12px; }
.pg-header h2 { margin: 0; }
.pg-actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.pg-status {
  display: flex; align-items: center; gap: 16px; padding: 4px 8px;
  font-size: 11px; color: var(--el-text-color-secondary);
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin-bottom: 8px;
}
.pg-stat { display: flex; gap: 4px; align-items: center; }
.pg-stat-label { color: var(--el-text-color-secondary); }
.pg-stat-val { font-family: monospace; font-weight: 600; color: var(--el-text-color-primary); }
.pg-stat-warn { color: #f59e0b; }
.pg-stat-ok { color: #10b981; }
.pg-stat-spacer { flex: 1; }
.pg-stat-hint { color: var(--el-text-color-placeholder); font-style: italic; }
.pg-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 12px; flex: 1; min-height: 500px; }
.pg-pane { display: flex; flex-direction: column; border: 1px solid var(--el-border-color); border-radius: 4px; overflow: hidden; background: var(--el-bg-color); }
.pg-pane-title { padding: 6px 10px; background: var(--el-fill-color-light); font-size: 12px; font-weight: 600; border-bottom: 1px solid var(--el-border-color); display: flex; justify-content: space-between; align-items: center; }
.pg-pane-meta { font-weight: normal; font-family: monospace; color: var(--el-text-color-secondary); }
.pg-pane-meta-err { color: #ef4444; }
.pg-pane-body { flex: 1; overflow: auto; display: flex; flex-direction: column; }
.pg-snippets {
  display: flex; gap: 4px; align-items: center; padding: 4px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 11px; flex-wrap: wrap;
}
.pg-snippet-label { color: var(--el-text-color-secondary); margin-right: 4px; }
.pg-timing { color: #10b981; font-family: monospace; font-weight: 600; }
.pg-code {
  flex: 1; width: 100%; border: 0; outline: 0; resize: none;
  font-family: 'JetBrains Mono', 'Menlo', monospace; font-size: 12px;
  padding: 8px; background: var(--el-bg-color); tab-size: 2;
}
.pg-dropzone {
  flex: 1; position: relative; display: flex; flex-direction: column;
  border: 2px dashed transparent; transition: border-color 0.15s ease;
}
.pg-dropzone-active {
  border-color: var(--el-color-primary);
  background: rgba(64, 158, 255, 0.05);
}
.pg-drop-overlay {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  background: rgba(64, 158, 255, 0.92); color: white;
  pointer-events: none; z-index: 10;
}
.pg-drop-msg { font-size: 16px; font-weight: 600; }
.pg-file-bar {
  display: flex; align-items: center; gap: 8px;
  padding: 4px 8px;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 11px;
  background: var(--el-fill-color-light);
}
.pg-file-hint { color: var(--el-text-color-placeholder); }
.pg-out { flex: 1; margin: 0; padding: 8px; overflow: auto; font-family: 'JetBrains Mono', 'Menlo', monospace; font-size: 12px; background: var(--el-bg-color); white-space: pre-wrap; word-break: break-word; }
.pg-err { padding: 4px 8px; color: #ef4444; font-size: 11px; background: #fef2f2; border-top: 1px solid var(--el-border-color); }
.pg-err-block { padding: 12px; background: #fef2f2; height: 100%; overflow: auto; }
.pg-err-title { color: #dc2626; font-weight: 600; font-size: 13px; margin-bottom: 8px; }
.pg-err-body { font-family: monospace; font-size: 11px; color: #7f1d1d; white-space: pre-wrap; word-break: break-word; margin: 0; }
.pg-trace { flex: 1; overflow: auto; }
.pg-trace-row { padding: 3px 10px; font-size: 11px; font-family: monospace; display: flex; gap: 8px; border-bottom: 1px dashed var(--el-border-color-lighter); align-items: baseline; }
.pg-trace-type { color: #a78bfa; font-weight: 600; min-width: 110px; }
.pg-trace-path { color: #6b7280; min-width: 80px; font-size: 10px; }
.pg-trace-msg { flex: 1; color: var(--el-text-color-regular); word-break: break-all; }
.pg-trace-dur { color: #10b981; font-weight: 600; }
.pg-trace-rest { background: rgba(59, 130, 246, 0.05); }
.pg-trace-component { background: rgba(168, 85, 247, 0.05); }
.pg-trace-script { background: rgba(245, 158, 11, 0.05); }
.pg-trace-render_string { background: rgba(16, 185, 129, 0.05); }
.pg-empty { padding: 24px; text-align: center; color: var(--el-text-color-placeholder); font-style: italic; }
@media (max-width: 1024px) {
  .pg-grid { grid-template-columns: 1fr; }
  .pg-pane { min-height: 300px; }
}
</style>