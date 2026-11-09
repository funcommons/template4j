<template>
  <div class="md-rendered" v-html="rendered" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'

// Module-level singleton: the parser holds no per-render mutable state.
// html: true lets raw HTML through the markdown parser; safety is delegated
// entirely to DOMPurify below.
const md = new MarkdownIt({ html: true, linkify: false, breaks: false })

// Tag markdown-it's table / code output with the legacy `md-table` / `md-code`
// classes so the existing scoped styles keep matching without changes.
md.renderer.rules.table_open = () => '<table class="md-table">\n'
md.renderer.rules.code_block = (tokens, idx) =>
  `<pre class="md-code"><code class="lang-">${md.utils.escapeHtml(tokens[idx].content)}</code></pre>\n`
md.renderer.rules.fence = (tokens, idx) => {
  const token = tokens[idx]
  const lang = (token.info || '').trim().split(/\s+/)[0] || ''
  return `<pre class="md-code"><code class="lang-${md.utils.escapeHtml(lang)}">${md.utils.escapeHtml(token.content)}</code></pre>\n`
}

// DOMPurify's default policy already strips on* handlers, <script>, and
// javascript: URIs. `target` is not in the default attr allow-list, and the
// default ALLOWED_ATTR set is not exported in v3, so ADD_ATTR is the way to
// append it without replacing the defaults.
const SANITIZE_CONFIG = { ADD_ATTR: ['target'] }

// Registered once at module level: every surviving <a href="..."> is treated
// as an external link and forced to a safe rel.
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A' && node.hasAttribute('href')) {
    node.setAttribute('rel', 'noopener noreferrer')
  }
})

const props = defineProps<{ content: string }>()

const rendered = computed(() => DOMPurify.sanitize(md.render(props.content ?? ''), SANITIZE_CONFIG))
</script>

<style scoped>
.md-rendered :deep(h1) {
  font-size: 1.5rem;
  margin: 16px 0 8px 0;
  color: #303133;
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 6px;
}
.md-rendered :deep(h2) {
  font-size: 1.2rem;
  margin: 14px 0 6px 0;
  color: #303133;
}
.md-rendered :deep(h3) {
  font-size: 1.05rem;
  margin: 12px 0 4px 0;
  color: #303133;
}
.md-rendered :deep(h4),
.md-rendered :deep(h5),
.md-rendered :deep(h6) {
  font-size: 1rem;
  margin: 10px 0 4px 0;
  color: #303133;
}
.md-rendered :deep(p) {
  margin: 6px 0;
  color: #303133;
  line-height: 1.7;
  white-space: pre-wrap;
}
.md-rendered :deep(ul),
.md-rendered :deep(ol) {
  margin: 6px 0 10px 0;
  padding-left: 22px;
}
.md-rendered :deep(li) {
  margin: 2px 0;
  line-height: 1.65;
}
.md-rendered :deep(code) {
  background: rgba(27, 31, 35, 0.06);
  padding: 1px 6px;
  border-radius: 4px;
  font-family: 'JetBrains Mono', 'SF Mono', Menlo, Consolas, monospace;
  font-size: 12px;
  color: #c7254e;
}
.md-rendered :deep(pre.md-code) {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px 14px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 10px 0;
  line-height: 1.6;
}
.md-rendered :deep(pre.md-code code) {
  background: transparent;
  color: inherit;
  padding: 0;
  font-size: 13px;
}
.md-rendered :deep(blockquote) {
  margin: 8px 0;
  padding: 6px 12px;
  border-left: 4px solid #dcdfe6;
  background: #fafbfc;
  color: #606266;
  border-radius: 0 4px 4px 0;
}
.md-rendered :deep(table.md-table) {
  border-collapse: collapse;
  margin: 10px 0;
  font-size: 13px;
  width: 100%;
}
.md-rendered :deep(table.md-table th),
.md-rendered :deep(table.md-table td) {
  border: 1px solid #ebeef5;
  padding: 6px 10px;
  text-align: left;
  vertical-align: top;
}
.md-rendered :deep(table.md-table th) {
  background: #fafbfc;
  font-weight: 600;
  color: #303133;
}
.md-rendered :deep(table.md-table tr:nth-child(2n) td) {
  background: #fcfcfd;
}
.md-rendered :deep(hr) {
  border: 0;
  border-top: 1px solid #ebeef5;
  margin: 16px 0;
}
.md-rendered :deep(a) {
  color: #409eff;
  text-decoration: none;
}
.md-rendered :deep(a:hover) {
  text-decoration: underline;
}
</style>
