<template>
  <div class="ce-root" :class="{ 'ce-focused': focused }">
    <!-- highlighted overlay (behind the textarea) -->
    <pre
      ref="overlayRef"
      class="ce-overlay"
      :aria-hidden="true"
      v-html="overlayHtml"
    ></pre>
    <textarea
      ref="textareaRef"
      v-model="model"
      class="ce-textarea"
      spellcheck="false"
      :placeholder="placeholder"
      @input="onInput"
      @focus="focused = true"
      @blur="focused = false"
      @keydown="onKeyDown"
      @scroll="syncScroll"
    ></textarea>
    <div v-if="suggestions.length" class="ce-suggest" :style="suggestStyle">
      <div
        v-for="(s, i) in suggestions"
        :key="s.label"
        class="ce-suggest-item"
        :class="{ 'ce-suggest-active': i === activeIdx }"
        @mousedown.prevent="applySuggestion(s)"
      >
        <span class="ce-suggest-label">{{ s.label }}</span>
        <span class="ce-suggest-desc">{{ s.desc }}</span>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
// 模块级 Shiki 单例(<script setup> 是每实例作用域,单例必须放在普通 <script> 中)
import type { Highlighter } from 'shiki'

let highlighterPromise: Promise<Highlighter | null> | null = null

function getHighlighter(): Promise<Highlighter | null> {
  if (!highlighterPromise) {
    // 动态 import + 失败静默回退:jsdom / wasm 不可用时解析为 null,组件走正则 fallback
    highlighterPromise = import('shiki')
      .then(m => m.createHighlighter({ themes: ['github-light'], langs: ['json', 'yaml', 'groovy'] }))
      .catch(() => null)
  }
  return highlighterPromise
}

type ShikiLang = 'json' | 'yaml' | 'groovy'

// 按内容猜测语言:trim 后 {/[ 开头 → json;含 "key: value" 且无 { → yaml;
// 含 {= / {% / @ → groovy;默认 json(json 优先,避免模板里的 {@x} 被误判为 groovy)
function guessLang(code: string): ShikiLang {
  const t = code.trim()
  if (/^[{[]/.test(t)) return 'json'
  if (/:\s/.test(t) && !t.includes('{')) return 'yaml'
  if (/\{=|\{%|@/.test(t)) return 'groovy'
  return 'json'
}
</script>

<script setup lang="ts">
import { ref, computed, watch, watchEffect, onMounted, nextTick } from 'vue'

interface Suggestion {
  label: string
  insert: string
  desc: string
}

const props = defineProps<{
  modelValue: string
  language?: 'json' | 'yaml' | 'groovy' | 'string'
  placeholder?: string
}>()

const emit = defineEmits<{ 'update:modelValue': [string] }>()

const textareaRef = ref<HTMLTextAreaElement | null>(null)
const overlayRef = ref<HTMLPreElement | null>(null)
const focused = ref(false)
const suggestions = ref<Suggestion[]>([])
const activeIdx = ref(0)
const suggestStyle = ref<{ left: string; top: string }>({ left: '0px', top: '0px' })

const model = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v),
})

function syncScroll() {
  if (!overlayRef.value || !textareaRef.value) return
  overlayRef.value.scrollTop = textareaRef.value.scrollTop
  overlayRef.value.scrollLeft = textareaRef.value.scrollLeft
}

function onInput() {
  // 不需要主动更新 model,v-model 已经双向绑定
  updateSuggestions()
}

function onKeyDown(e: KeyboardEvent) {
  if (suggestions.value.length) {
    if (e.key === 'ArrowDown') { e.preventDefault(); activeIdx.value = (activeIdx.value + 1) % suggestions.value.length; return }
    if (e.key === 'ArrowUp') { e.preventDefault(); activeIdx.value = (activeIdx.value - 1 + suggestions.value.length) % suggestions.value.length; return }
    if (e.key === 'Tab' || (e.key === 'Enter' && !e.shiftKey)) { e.preventDefault(); applySuggestion(suggestions.value[activeIdx.value]); return }
    if (e.key === 'Escape') { e.preventDefault(); suggestions.value = []; return }
  }
  if (e.key === 'Tab') { e.preventDefault(); insertAtCursor('  '); return }
}

function insertAtCursor(text: string) {
  const ta = textareaRef.value
  if (!ta) {
    model.value += text
    return
  }
  const start = ta.selectionStart
  const end = ta.selectionEnd
  const v = model.value
  model.value = v.slice(0, start) + text + v.slice(end)
  nextTick(() => {
    ta.focus()
    ta.setSelectionRange(start + text.length, start + text.length)
  })
}

function applySuggestion(s: Suggestion) {
  if (!s) return
  insertAtCursor(s.insert)
  suggestions.value = []
}

// 高亮 JSON / YAML / Groovy / template4j 占位符
const highlighted = computed(() => {
  const escaped = escapeHtml(model.value) + '\n'
  return escaped
    // template4j placeholder: {@x} 或 {@...x} 或 {= ... } 或 {% ... %} 或 {$x}
    .replace(/\{([@$=])((?:[^}]*?))(\s*)\}/g, '<span class="hl-placeholder">{$1$2$3}</span>')
    .replace(/(\{%)([\s\S]*?)(%\})/g, '<span class="hl-placeholder">$1$2$3</span>')
    .replace(/(&quot;[^&]*?&quot;)(\s*:)/g, '<span class="hl-key">$1</span>$2')
    .replace(/:\s*(&quot;[^&]*?&quot;)/g, ': <span class="hl-string">$1</span>')
    .replace(/:\s*(-?\d+\.?\d*)/g, ': <span class="hl-number">$1</span>')
    .replace(/:\s*(true|false|null)/g, ': <span class="hl-bool">$1</span>')
})

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

// Shiki 高亮管线:highlighter 未就绪 / 内容为空 / 高亮出错时 shikiHtml 保持 null,
// 模板回退到上面的 escapeHtml+正则同步管线(首帧与测试环境均走 fallback)
const highlighter = ref<Highlighter | null>(null)
const shikiHtml = ref<string | null>(null)

watchEffect(() => {
  const code = props.modelValue
  const hl = highlighter.value
  if (!hl || !code) { shikiHtml.value = null; return }
  try {
    shikiHtml.value = hl
      // Shiki 输出自带 HTML 转义,满足 XSS 契约
      .codeToHtml(code, { lang: guessLang(code), theme: 'github-light' })
      // 容器自身有背景,shiki 的内联 pre 背景必须透明,避免与 overlay 叠加冲突
      .replace(/background-color:[^;"}]+/g, 'background-color:transparent')
  } catch {
    shikiHtml.value = null
  }
})

const overlayHtml = computed(() => shikiHtml.value ?? highlighted.value)

// 占位符自动补全
function updateSuggestions() {
  const ta = textareaRef.value
  if (!ta) { suggestions.value = []; return }
  const pos = ta.selectionStart
  const before = model.value.slice(0, pos)
  // 检测到 {@ / {$ / {= / {% 时弹出补全
  const m = before.match(/\{([@$=])(\w*)$/)
  if (!m) { suggestions.value = []; return }
  const prefix = m[1]
  const partial = m[2]
  const all: Suggestion[] = prefix === '@' ? [
    { label: '{@path}', insert: '{@' + (partial || 'path') + '}', desc: 'JSONPath 读取 data' },
    { label: '{@..field}', insert: '{@..' + (partial || 'field') + '}', desc: 'deepScan 字段' },
  ] : prefix === '$' ? [
    { label: '{$var}', insert: '{$' + (partial || 'var') + '}', desc: '读取变量' },
  ] : prefix === '=' ? [
    { label: '{= expr}', insert: '{= ' + (partial || '@a + @b') + ' }', desc: 'Groovy 表达式' },
    { label: '{= if}', insert: '{= ' + (partial || '@x > 0 ? "yes" : "no"') + ' }', desc: '三元' },
  ] : []
  // 过滤已部分输入的
  suggestions.value = all.filter(s => s.label.toLowerCase().includes(partial.toLowerCase())).slice(0, 5)
  activeIdx.value = 0
  // 定位
  if (all.length) {
    suggestStyle.value = { left: '20px', top: '20px' }
  }
}

onMounted(() => {
  // 初始化一次
  updateSuggestions()
  // 异步加载 Shiki 单例,就绪后赋值触发 watchEffect 重算接管高亮;失败保持 null
  getHighlighter().then(h => { highlighter.value = h })
})

watch(() => props.modelValue, () => {
  // v-model 触发后也更新 suggestions
  updateSuggestions()
})
</script>

<style scoped>
.ce-root {
  position: relative; flex: 1; display: flex; flex-direction: column;
  background: var(--el-bg-color); overflow: hidden;
  border-radius: 4px;
}
.ce-root.ce-focused { outline: 1px solid var(--el-color-primary); }
.ce-overlay {
  position: absolute; inset: 0;
  margin: 0; padding: 8px;
  font-family: 'JetBrains Mono', 'Menlo', monospace; font-size: 12px;
  white-space: pre-wrap; word-break: break-word;
  pointer-events: none; overflow: auto;
  color: transparent;
  background: transparent;
  tab-size: 2;
  z-index: 1;
}
.ce-overlay :deep(.hl-placeholder) { color: #a78bfa; font-weight: 600; background: rgba(167, 139, 250, 0.1); border-radius: 2px; }
.ce-overlay :deep(.hl-key) { color: #60a5fa; }
.ce-overlay :deep(.hl-string) { color: #34d399; }
.ce-overlay :deep(.hl-number) { color: #f59e0b; }
.ce-overlay :deep(.hl-bool) { color: #ec4899; }
/* shiki 注入的内联 pre/code:重置 UA 默认,保持与 textarea 相同的排版度量 */
.ce-overlay :deep(pre.shiki) {
  margin: 0; padding: 0; background: transparent !important;
  font-family: inherit; font-size: inherit; line-height: inherit;
  white-space: pre-wrap; word-break: break-word; tab-size: 2;
}
.ce-overlay :deep(pre.shiki code) {
  font-family: inherit; font-size: inherit; line-height: inherit;
  white-space: pre-wrap; word-break: break-word; tab-size: 2;
}
.ce-textarea {
  position: relative; flex: 1; width: 100%; border: 0; outline: 0; resize: none;
  font-family: 'JetBrains Mono', 'Menlo', monospace; font-size: 12px;
  padding: 8px; background: transparent;
  color: var(--el-text-color-primary);
  caret-color: var(--el-text-color-primary);
  z-index: 2; tab-size: 2;
  white-space: pre-wrap; word-break: break-word;
}
.ce-suggest {
  position: absolute; background: var(--el-bg-color-overlay); border: 1px solid var(--el-border-color);
  border-radius: 4px; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15); z-index: 100;
  min-width: 240px; max-width: 360px; overflow: hidden;
}
.ce-suggest-item {
  display: flex; gap: 8px; padding: 6px 10px; cursor: pointer; font-size: 11px;
}
.ce-suggest-item:hover, .ce-suggest-active { background: var(--el-color-primary-light-9); }
.ce-suggest-label { font-family: monospace; font-weight: 600; color: var(--el-color-primary); }
.ce-suggest-desc { color: var(--el-text-color-secondary); }
</style>