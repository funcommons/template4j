// Playground smoke test: load page, render a preset, check output,
// test Ctrl+Enter shortcut, snippet insertion, format button.

import { resolve } from 'node:path'
import { launchBrowser, waitForReady } from './lib/launch.mjs'
import { IssueLog } from './lib/assert.mjs'
import { ensureDir, screenshotPath, writeReport } from './lib/io.mjs'

const BASE = process.env.BASE || 'http://localhost:5173'
const SHOTS = resolve(process.cwd(), 'e2e-shots')
ensureDir(SHOTS)

const log = new IssueLog()

;(async () => {
  const { browser, context, page } = await launchBrowser()

  await waitForReady(page, BASE + '/playground', { selector: '.playground', idleMs: 2000 })
  // SPA route → wait for component to mount
  await page.waitForFunction(() => !!document.querySelector('.pg-status'), { timeout: 15000 }).catch(() => {})
  await page.waitForTimeout(1500)
  await page.screenshot({ path: screenshotPath(SHOTS, 'pg-01-load'), fullPage: true })

  // 1) Status bar should show renders=0 and saved
  const status = (await page.locator('.pg-status').textContent().catch(() => '')) || ''
  console.log('status:', status.trim())
  // i18n: accept either "Renders" (en) or "渲染次数" (zh)
  if (!status.match(/Renders|渲染次数/)) log.log('ui', 'status bar missing Renders counter')

  // 2) Wait for auto-render (preset loaded in onMounted)
  await page.waitForTimeout(1500)
  const outputText = (await page.locator('.pg-out').first().textContent().catch(() => '')) || ''
  console.log('output length:', outputText.length)
  if (!outputText.includes('Hello')) log.log('render', 'preset render failed', { output: outputText.slice(0, 100) })
  await page.screenshot({ path: screenshotPath(SHOTS, 'pg-02-after-render'), fullPage: true })

  // 3) Click Format JSON button (data is already valid JSON, should format)
  const formatBtn = page.locator('button').filter({ hasText: /Format JSON|格式化/ }).first()
  if (await formatBtn.count() > 0) {
    await formatBtn.click()
    await page.waitForTimeout(400)
    await page.screenshot({ path: screenshotPath(SHOTS, 'pg-03-formatted'), fullPage: true })
  } else {
    log.log('ui', 'Format JSON button not found')
  }

  // 4) Switch to Trace tab and back (even if no events yet)
  const traceBtn = page.locator('button').filter({ hasText: /(Trace|追踪): (ON|OFF)/ }).first()
  if (await traceBtn.count() > 0) {
    await traceBtn.click()
    await page.waitForTimeout(1200) // auto-render after toggle
    await page.screenshot({ path: screenshotPath(SHOTS, 'pg-04-trace-on'), fullPage: true })
    const traceTab = page.locator('.pg-trace')
    if (await traceTab.count() === 0) log.log('ui', 'trace container not found')
    else {
      const traceRows = await page.locator('.pg-trace-row').count()
      console.log('trace rows:', traceRows)
    }
  } else {
    log.log('ui', 'Trace button not found')
  }

  // 5) Snippet buttons present
  const snippetButtons = await page.locator('.pg-snippets button').count()
  console.log('snippet buttons:', snippetButtons)
  if (snippetButtons < 4) log.log('ui', `expected >=4 snippet buttons, got ${snippetButtons}`)

  // 6) Ctrl+Enter triggers render — use data editor (template editor uses CodeEditor now)
  const dataArea = page.locator('.pg-dropzone textarea').first()
  await dataArea.click()
  await dataArea.press('Control+Enter')
  await page.waitForTimeout(800)
  await page.screenshot({ path: screenshotPath(SHOTS, 'pg-05-after-shortcut'), fullPage: true })

  // 7) Clear button resets state
  const clearBtn = page.locator('button').filter({ hasText: /^Clear$|^清空$/ }).first()
  if (await clearBtn.count() > 0) {
    await clearBtn.click()
    await page.waitForTimeout(500)
    // 验证 data 文本框被清空(恢复到 "{}")
    const dataAfterClear = await page.locator('.pg-dropzone textarea').first().inputValue()
    if (dataAfterClear !== '{}') log.log('ui', 'Clear did not reset data to {}', { dataAfterClear })
    await page.screenshot({ path: screenshotPath(SHOTS, 'pg-06-cleared'), fullPage: true })
  }

  // 8) Load from file via hidden input (use the file picker button)
  const loadFileBtn = page.locator('button').filter({ hasText: /Load File|加载文件/ }).first()
  if (await loadFileBtn.count() > 0) {
    // Reload preset via UI to restore template (Clear wiped it)
    const presetSelect = page.locator('.el-select').first()
    await presetSelect.click()
    await page.waitForTimeout(300)
    const firstPreset = page.locator('.el-select-dropdown__item').first()
    await firstPreset.click()
    await page.waitForTimeout(1500)
    // 直接调用 hidden input.change 模拟选择文件
    await page.locator('input[type="file"]').setInputFiles({
      name: 'test-data.json',
      mimeType: 'application/json',
      buffer: Buffer.from('{"name":"fromFile","age":99}')
    })
    await page.waitForTimeout(2500)
    // Force Output tab so .pg-out.first() matches the right element
    await page.locator('.el-radio-button').filter({ hasText: /Output|结果/ }).first().click().catch(() => {})
    await page.waitForTimeout(500)
    await page.screenshot({ path: screenshotPath(SHOTS, 'pg-07-file-loaded'), fullPage: true })
    const dataValue = await page.locator('.pg-code').nth(1).inputValue().catch(() => '')
    if (!dataValue.includes('fromFile')) {
      log.log('ui', 'file load did not populate data textarea', { dataValue: dataValue.slice(0, 60) })
    }
    const outAfter = (await page.locator('.pg-out').first().textContent().catch(() => '')) || ''
    if (!outAfter.includes('fromFile')) {
      log.log('render', 'render after file load did not pick up new data', { outAfter: outAfter.slice(0, 80) })
    }
  } else {
    log.log('ui', 'Load File button not found')
  }

  // 9) Drag-drop zone present and styled
  const dropzone = page.locator('.pg-dropzone')
  if (await dropzone.count() === 0) log.log('ui', 'dropzone element missing')
  const fileBar = page.locator('.pg-file-bar')
  if (await fileBar.count() === 0) log.log('ui', 'file bar element missing')
  else {
    const hint = await fileBar.textContent().catch(() => '')
    console.log('file bar hint:', hint?.slice(0, 60))
  }

  // 10) Code editor overlay (syntax highlight) exists in template pane
  const ceRoot = page.locator('.ce-root')
  if (await ceRoot.count() === 0) log.log('ui', 'CodeEditor component missing')
  else {
    // 类型为 json 时, 模板里有 "{@v}" placeholder 应被高亮
    const ceOverlay = page.locator('.ce-overlay').first()
    const overlayHtml = await ceOverlay.innerHTML().catch(() => '')
    if (!overlayHtml.includes('hl-placeholder')) {
      log.log('ui', 'placeholder syntax highlight not found in overlay', { overlayHtml: overlayHtml.slice(0, 100) })
    }
  }

  await browser.close()
  console.log('\n===== SUMMARY =====')
  console.log('total issues:', log.summary().length)
  writeReport(SHOTS, 'issues-playground', { errors: log.summary() })
  process.exit(log.hasFailures() ? 1 : 0)
})().catch((e) => {
  console.error('test crashed:', e)
  process.exit(2)
})