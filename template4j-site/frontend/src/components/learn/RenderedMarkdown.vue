<template>
  <div class="md-rendered" v-html="rendered" />
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ content: string }>()

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function escapeAttr(s: string): string {
  return escapeHtml(s)
}

function inline(s: string): string {
  return s
    .replace(/`([^`]+)`/g, (_, code) => `<code>${escapeHtml(code)}</code>`)
    .replace(/\*\*([^*]+)\*\*/g, (_, t) => `<strong>${escapeHtml(t)}</strong>`)
    .replace(/(^|[^*])\*([^*\n]+)\*(?!\*)/g, (_, p, t) => `${p}<em>${escapeHtml(t)}</em>`)
    .replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (_, txt, url) => {
      const safe = /^(https?:|\/)/i.test(url) ? url : '#'
      return `<a href="${escapeAttr(safe)}" target="_blank" rel="noopener">${escapeHtml(txt)}</a>`
    })
}

function renderTable(rows: string[]): string {
  if (rows.length < 2) return rows.map((r) => `<p>${inline(escapeHtml(r))}</p>`).join('')
  const head = rows[0].split('|').slice(1, -1).map((c) => c.trim())
  // row[1] is the alignment separator; skip
  const body = rows.slice(2).map((r) => {
    const cells = r.split('|').slice(1, -1).map((c) => c.trim())
    return `<tr>${cells.map((c) => `<td>${inline(escapeHtml(c))}</td>`).join('')}</tr>`
  }).join('')
  return `<table class="md-table">
    <thead><tr>${head.map((c) => `<th>${inline(escapeHtml(c))}</th>`).join('')}</tr></thead>
    <tbody>${body}</tbody>
  </table>`
}

function renderCodeBlock(lang: string, body: string): string {
  return `<pre class="md-code"><code class="lang-${escapeAttr(lang || '')}">${escapeHtml(body)}</code></pre>`
}

const rendered = computed(() => {
  const lines = (props.content || '').replace(/\r\n?/g, '\n').split('\n')
  const out: string[] = []
  let i = 0
  let inList = false
  let inOrdered = false
  let inTable: string[] | null = null
  let paraBuf: string[] = []

  const flushPara = () => {
    if (paraBuf.length) {
      out.push(`<p>${inline(escapeHtml(paraBuf.join(' ').trim()))}</p>`)
      paraBuf = []
    }
  }
  const closeLists = () => {
    if (inList) { out.push('</ul>'); inList = false }
    if (inOrdered) { out.push('</ol>'); inOrdered = false }
  }
  const flushTable = () => {
    if (inTable) {
      out.push(renderTable(inTable))
      inTable = null
    }
  }

  while (i < lines.length) {
    const line = lines[i]
    const trimmed = line.trim()

    // fenced code block
    if (trimmed.startsWith('```')) {
      flushPara(); closeLists(); flushTable()
      const lang = trimmed.slice(3).trim()
      const body: string[] = []
      i++
      while (i < lines.length && !lines[i].trim().startsWith('```')) {
        body.push(lines[i])
        i++
      }
      i++
      out.push(renderCodeBlock(lang, body.join('\n')))
      continue
    }

    // table block (consecutive | lines)
    if (trimmed.startsWith('|') && trimmed.endsWith('|')) {
      flushPara(); closeLists()
      if (!inTable) inTable = []
      inTable.push(trimmed)
      i++
      // peek: if next line is not a table row, flush
      if (i >= lines.length || !(lines[i].trim().startsWith('|') && lines[i].trim().endsWith('|'))) {
        flushTable()
      }
      continue
    }
    flushTable()

    // headings
    const headingMatch = /^(#{1,6})\s+(.*)$/.exec(trimmed)
    if (headingMatch) {
      flushPara(); closeLists()
      const level = headingMatch[1].length
      const text = headingMatch[2]
      out.push(`<h${level}>${inline(escapeHtml(text))}</h${level}>`)
      i++
      continue
    }

    // blockquote
    if (trimmed.startsWith('> ')) {
      flushPara(); closeLists()
      const buf: string[] = [trimmed.slice(2)]
      i++
      while (i < lines.length && lines[i].trim().startsWith('> ')) {
        buf.push(lines[i].trim().slice(2))
        i++
      }
      out.push(`<blockquote>${inline(escapeHtml(buf.join(' ')))}</blockquote>`)
      continue
    }

    // horizontal rule
    if (/^(-{3,}|\*{3,}|_{3,})\s*$/.test(trimmed)) {
      flushPara(); closeLists()
      out.push('<hr/>')
      i++
      continue
    }

    // unordered list
    if (/^[-*+]\s+/.test(trimmed)) {
      flushPara()
      if (!inList) { out.push('<ul>'); inList = true }
      const item = trimmed.replace(/^[-*+]\s+/, '')
      out.push(`<li>${inline(escapeHtml(item))}</li>`)
      i++
      continue
    }
    if (inList) { out.push('</ul>'); inList = false }

    // ordered list
    if (/^\d+\.\s+/.test(trimmed)) {
      flushPara()
      if (!inOrdered) { out.push('<ol>'); inOrdered = true }
      const item = trimmed.replace(/^\d+\.\s+/, '')
      out.push(`<li>${inline(escapeHtml(item))}</li>`)
      i++
      continue
    }
    if (inOrdered) { out.push('</ol>'); inOrdered = false }

    // blank line: paragraph break
    if (trimmed === '') {
      flushPara()
      i++
      continue
    }

    // accumulating paragraph text
    paraBuf.push(trimmed)
    i++
  }
  flushPara(); closeLists(); flushTable()
  return out.join('\n')
})
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
