// Regression suite shared framework.
// Evidence policy: PASS keeps only key-marked screenshots; FAIL keeps every
// screenshot taken during the case plus an automatic final full-page shot
// and the console / API error events captured while the case ran.

import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { launchBrowser, waitForReady } from '../lib/launch.mjs'
import { labelRegex } from '../lib/selectors.mjs'

export const SUITE_DIR = dirname(fileURLToPath(import.meta.url))
export const REPORT_DIR = resolve(SUITE_DIR, '../../test-report', process.env.T4J_REPORT_SEQ || '2026-09-20-001')
export const BASE = process.env.BASE || 'http://localhost:5173'

export { labelRegex }

// ---------------------------------------------------------------- CaseCtx --

class CaseCtx {
  constructor(bl, spec) {
    this.bl = bl
    this.spec = spec
    this.page = bl.page
    this.issues = []
    this.shotsTaken = []
    this.evStart = bl.events.length
    this.step = 0
  }

  id() { return this.spec.id }

  async shot(name, opts = {}) {
    const { fullPage = true, key = false } = opts
    this.step++
    const file = `${this.spec.id}-${String(this.step).padStart(2, '0')}-${name}.png`
    const path = resolve(this.bl.shotsDir, file)
    await this.page.screenshot({ path, fullPage })
    this.shotsTaken.push({ path, key, name: file })
    return path
  }

  issue(level, msg, extra = {}) {
    this.issues.push({ level, msg, ...extra })
    console.log(`  [${level.toUpperCase()}][${this.spec.id}] ${msg}`)
  }

  fail(msg, extra) { this.issue('fail', msg, extra) }
  warn(msg, extra) { this.issue('warn', msg, extra) }
  note(msg) { this.issue('info', msg) }

  expect(cond, msg, extra = {}) {
    if (!cond) this.fail(msg, extra)
    return !!cond
  }

  warnIf(cond, msg, extra = {}) {
    if (cond) this.warn(msg, extra)
    return !!cond
  }

  events() { return this.bl.events.slice(this.evStart) }
  consoleErrors() { return this.events().filter(e => e.type === 'console.error').map(e => e.text) }
  consoleWarns() { return this.events().filter(e => e.type === 'console.warn').map(e => e.text) }
  pageErrors() { return this.events().filter(e => e.type === 'pageerror') }
  apiErrors() { return this.events().filter(e => e.type === 'api4xx') }
  api4xxOk(statuses) { return this.apiErrors().filter(e => !statuses.includes(e.status)) }

  async goto(path, opts = {}) {
    await waitForReady(this.page, BASE + path, { url: BASE + path, ...opts })
  }

  // ---- shared UI helpers (Element Plus) ----

  async selectOption(triggerSel, text) {
    await this.page.locator(triggerSel).first().click()
    const dd = this.page.locator('.el-select-dropdown:visible .el-select-dropdown__item')
      .filter({ hasText: text }).first()
    await dd.waitFor({ state: 'visible', timeout: 5000 })
    await dd.click()
    await this.page.waitForTimeout(200)
  }

  async setLang(locale) {
    // 目标语言已激活时跳过(下拉中当前语言项为 disabled, 点击会超时)
    const cur = await this.page.evaluate(() => localStorage.getItem('locale') || 'zh-CN')
    if (cur === locale) return
    await this.page.locator('.nav-lang-trigger').click()
    const item = this.page.locator('.el-dropdown-menu__item')
      .filter({ hasText: locale === 'zh-CN' ? '中文' : 'English' }).first()
    await item.waitFor({ state: 'visible', timeout: 5000 })
    await item.click()
    // App 切语言会整页 reload
    await this.page.waitForLoadState('networkidle').catch(() => {})
    await this.page.waitForTimeout(600)
  }

  btn(textRe, scope = null) {
    const root = scope ? this.page.locator(scope) : this.page
    return root.locator('button').filter({ hasText: textRe }).first()
  }

  // Playground textareas: pane 0 template, 1 data, 2 output
  // (模板面板有一个 display:none 的 fallback textarea, 必须按可见性过滤)
  ta(paneIdx) {
    return this.page.locator('.pg-pane').nth(paneIdx).locator('textarea:visible').first()
  }

  async renders() {
    const txt = await this.page.locator('.pg-stat').first().textContent().catch(() => '')
    const m = (txt || '').match(/(\d+)/)
    return m ? parseInt(m[1], 10) : 0
  }

  async waitRenders(exceed, timeout = 10000) {
    await this.page.waitForFunction(
      (n) => {
        const el = document.querySelector('.pg-stat-val')
        return el && parseInt(el.textContent || '0', 10) > n
      },
      exceed,
      { timeout },
    ).catch(() => {})
    return this.renders()
  }

  outputText() {
    return this.page.locator('.pg-out').first().textContent().catch(() => '')
  }

  async settle(ms = 800) { await this.page.waitForTimeout(ms) }
}

// ------------------------------------------------------------------- BL ----

export class BL {
  constructor({ id, name, slug, desc = '', cases }) {
    this.id = id
    this.name = name
    this.slug = slug
    this.desc = desc
    this.cases = specs(cases)
    this.shotsDir = resolve(REPORT_DIR, slug, 'shots')
    this.events = []
  }

  async run() {
    mkdirSync(this.shotsDir, { recursive: true })
    const startedAt = new Date().toISOString()
    console.log(`\n================ ${this.id} ${this.name} ================`)
    const { browser } = await launchBrowser()
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
    // clipboard hijack on every navigation (app reads it via navigator.clipboard)
    await context.addInitScript(() => {
      window.__copied = []
      try {
        const w = (t) => { window.__copied.push(String(t)); return Promise.resolve() }
        if (navigator.clipboard) {
          Object.defineProperty(navigator.clipboard, 'writeText', { value: w, configurable: true })
        } else {
          Object.defineProperty(navigator, 'clipboard', { value: { writeText: w }, configurable: true })
        }
      } catch {}
    })
    const page = await context.newPage()
    this.page = page
    await this.installCollectors(page)

    // warm once so vite compiles deps before first real case
    try {
      await waitForReady(page, BASE + '/', { idleMs: 1200 })
    } catch (e) {
      console.log(`FATAL: cannot open ${BASE}: ${e.message}`)
      const results = this.cases.map(c => ({
        id: c.id, name: c.name, status: 'fail', durationMs: 0,
        evidence: [], issues: [{ level: 'fail', msg: `suite setup failed: ${e.message}` }], info: [],
      }))
      writeFileSync(this.resultPath(), JSON.stringify(this.report(results, startedAt), null, 2))
      await browser.close()
      return { id: this.id, pass: 0, fail: results.length, warn: 0 }
    }

    const results = []
    for (const spec of this.cases) results.push(await this.runCase(spec))

    const payload = this.report(results, startedAt)
    writeFileSync(this.resultPath(), JSON.stringify(payload, null, 2))
    await browser.close()

    const pass = results.filter(r => r.status === 'pass').length
    const fail = results.filter(r => r.status === 'fail').length
    const warns = results.reduce((n, r) => n + r.issues.filter(i => i.level === 'warn').length, 0)
    console.log(`---- ${this.id}: ${pass} pass / ${fail} fail / ${warns} warn (report: ${this.resultPath()})`)
    return { id: this.id, name: this.name, pass, fail, warns }
  }

  resultPath() { return resolve(REPORT_DIR, this.slug, 'result.json') }

  report(results, startedAt) {
    return {
      bl: this.id,
      name: this.name,
      desc: this.desc,
      startedAt,
      finishedAt: new Date().toISOString(),
      cases: results,
      summary: {
        total: results.length,
        pass: results.filter(r => r.status === 'pass').length,
        fail: results.filter(r => r.status === 'fail').length,
      },
    }
  }

  async installCollectors(page) {
    page.on('console', m => {
      if (m.type() === 'error') this.events.push({ type: 'console.error', text: m.text() })
      else if (m.type() === 'warning') this.events.push({ type: 'console.warn', text: m.text() })
    })
    page.on('pageerror', e => this.events.push({ type: 'pageerror', text: e.message }))
    page.on('requestfailed', req => {
      const url = req.url()
      if (url.includes('__vite_ping') || url.includes('.hot-update.')) return
      if ((req.failure()?.errorText || '').includes('ERR_ABORTED')) return
      this.events.push({ type: 'requestfailed', text: url, error: req.failure()?.errorText })
    })
    page.on('response', resp => {
      const u = resp.url()
      if (u.includes('/api/') && resp.status() >= 400) {
        this.events.push({ type: 'api4xx', status: resp.status(), text: `${resp.status()} ${u}` })
      }
    })
  }

  async freshPage() {
    // clean browser state between cases (keep same page object)
    await this.page.goto(BASE + '/', { waitUntil: 'domcontentloaded' }).catch(() => {})
    await this.page.evaluate(() => { localStorage.clear(); sessionStorage.clear() }).catch(() => {})
  }

  async runCase(spec) {
    console.log(`  -- ${spec.id} ${spec.name}`)
    const ctx = new CaseCtx(this, spec)
    const t0 = Date.now()
    try {
      await this.freshPage()
      await spec.fn(ctx)
    } catch (e) {
      ctx.fail(`case threw: ${e.message}`, { stack: String(e.stack || '').split('\n').slice(0, 4).join('\n') })
    }
    const durationMs = Date.now() - t0

    const failed = ctx.issues.some(i => i.level === 'fail')
    let status = failed ? 'fail' : 'pass'
    if (failed) {
      try {
        await ctx.shot('zz-final-fullpage', { key: true })
      } catch {}
      const ev = ctx.events()
      if (ev.length) {
        writeFileSync(
          resolve(this.shotsDir, `${spec.id}-events.json`),
          JSON.stringify(ev, null, 2),
        )
      }
    } else {
      for (const s of ctx.shotsTaken) if (!s.key) rmSync(s.path, { force: true })
    }

    const evidence = failed
      ? ctx.shotsTaken.map(s => s.name)
      : ctx.shotsTaken.filter(s => s.key).map(s => s.name)
    if (failed && ctx.events().length) evidence.push(`${spec.id}-events.json`)

    return {
      id: spec.id,
      name: spec.name,
      status,
      durationMs,
      evidence,
      issues: ctx.issues,
      info: ctx.issues.filter(i => i.level === 'info').map(i => i.msg),
    }
  }
}

function specs(cases) {
  return cases.map(([id, name, fn]) => ({ id, name, fn }))
}

export async function main(bl) {
  const r = await bl.run()
  process.exitCode = r.fail > 0 ? 1 : 0
}
