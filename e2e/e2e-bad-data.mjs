// Verifies the Learn page's variable editors behave correctly when the user
// types invalid data and clicks Run. Covers user-facing error states that
// the engine / demo backend should surface, not regress.

import { resolve } from 'node:path'
import { launchBrowser, waitForReady } from './lib/launch.mjs'
import {
  IssueLog,
  installIssueCollectors,
  trackRenderRequests,
  navigateToExample,
} from './lib/assert.mjs'
import { expandAllSubmenus, waitForLearnReady } from './lib/nav.mjs'
import { ensureDir, screenshotPath, writeReport } from './lib/io.mjs'

const BASE = process.env.BASE || 'http://localhost:5173'
const SHOTS = resolve(process.cwd(), 'e2e-bad-data-shots')
ensureDir(SHOTS)

const log = new IssueLog()

/**
 * Statuses considered part of the contract under test — they're the unit,
 * not a regression. Anything outside this set still counts as a 4xx issue.
 */
const EXPECTED_BAD_INPUT_STATUSES = new Set([400, 10301, 10400])
const EXPECTED_BAD_INPUT_API_BAD_INPUT = false

/**
 * Wrap trackRenderRequests but allow calling sites to whitelist specific
 * statuses as "expected" without the global render.4xx log firing.
 */
function trackRenderRequestsWithAllowlist(page, log, allowlist) {
  const pending = new Map()
  page.on('request', (req) => {
    if (req.url().includes('/api/render') && req.method() === 'POST') {
      pending.set(req, { body: req.postData() || '' })
    }
  })
  page.on('response', async (resp) => {
    const req = resp.request()
    if (!req.url().includes('/api/render') || req.method() !== 'POST') return
    const meta = pending.get(req) || {}
    pending.delete(req)
    if (resp.status() < 400) return
    if (allowlist.has(resp.status())) return
    const text = await resp.text().catch(() => '')
    log.log('render.4xx', `${resp.status()} unexpected`, {
      status: resp.status(),
      bodyPreview: meta.body?.slice(0, 200),
      responsePreview: text.slice(0, 200),
    })
  })
  return pending
}

async function clickRunAndWait(page, b, { timeoutMs = 4000 } = {}) {
  const runBtn = page.locator('button[data-testid^="learn-run-"]').nth(b)
  // For client-side guard cases the request never fires; don't hang for 30s.
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/render') && r.request().method() === 'POST',
    { timeout: timeoutMs }
  ).catch((e) => null)
  await runBtn.click({ timeout: 4000 })
  const result = await resp
  await page.waitForTimeout(150)
  return result
}

async function getOutputTexts(page, b) {
  const s = page.locator('[data-testid^="learn-jr-section-"][data-runnable="true"]').nth(b)
  return {
    output:    (await s.locator('.output-body').textContent().catch(() => '')) || '',
    hasResult: await s.locator('.output-body .code-block').count().catch(() => 0),
    hasError:  await s.locator('.output-body .code-error').count().catch(() => 0),
  }
}

;(async () => {
  const { browser, page } = await launchBrowser()
  installIssueCollectors(page, log, {
    apiErrorAllowlist: EXPECTED_BAD_INPUT_STATUSES,
    // Bad-input tests intentionally trigger 4xx on /api/render — silence them
    apiErrorBlocklist: ['/api/render'],
  })
  trackRenderRequestsWithAllowlist(page, log, EXPECTED_BAD_INPUT_STATUSES)

  await waitForReady(page, BASE + '/learn')
  await waitForLearnReady(page, 600)
  await expandAllSubmenus(page)
  await navigateToExample(page, 'component-each')
  await expandAllSubmenus(page)
  await page.waitForTimeout(400)

  // First RUNNABLE json-renderer section — section index 0 is usually a markdown
  // section (no inputs).
  const firstSection = page.locator('[data-testid^="learn-jr-section-"][data-runnable="true"]').first()
  const tmplInput = firstSection.locator('textarea[data-testid$="-template"]')
  const dataInput = firstSection.locator('textarea[data-testid$="-data"]')
  if (await tmplInput.count() === 0 || await dataInput.count() === 0) {
    log.log('ui', 'no template/data textarea in first runnable section')
  }

  // ── case 1: invalid JSON in data ────────────────────────────────────────
  {
    await dataInput.fill('this is not valid JSON {')
    const resp = await clickRunAndWait(page, 0, { timeoutMs: 1500 })
    const out = await getOutputTexts(page, 0)
    const statusOrNone = resp && typeof resp.status === 'function' ? resp.status() : 'no-req'
    console.log('case 1:', statusOrNone, '|', out.output.slice(0, 60))
    if (resp) {
      log.log('ui', 'bad data should be caught client-side, but request went through', { status: resp.status() })
    }
    if (out.hasError === 0 || !out.output.includes('data')) {
      log.log('ui', 'bad data did not surface an error', { outputPreview: out.output.slice(0, 80) })
    }
    await page.screenshot({ path: screenshotPath(SHOTS, '01-bad-data'), fullPage: true })
  }

  // ── case 2: blank template ──────────────────────────────────────────────
  // data must be a JSON object (RenderRequest.data is `Map<String,Object>` and
  // Jackson rejects arrays); use the demo default of component-each's first
  // section.
  {
    await dataInput.fill('{"friends":[{"name":"Bob","age":21}]}')
    await tmplInput.fill('')
    const resp = await clickRunAndWait(page, 0)
    const out = await getOutputTexts(page, 0)
    console.log('case 2:', resp.status?.() ?? resp._err, '|', out.output.slice(0, 60))
    if (resp.status && resp.status() !== 400) {
      log.log('ui', 'blank template should return 400', { status: resp.status() })
    }
    if (!out.output.includes('template')) {
      log.log('ui', 'blank template error message missing', { outputPreview: out.output.slice(0, 80) })
    }
    await page.screenshot({ path: screenshotPath(SHOTS, '02-blank-template'), fullPage: true })
  }

  // ── case 3: string-type template via raw text + JSONPath ────────────────
  // Engine must accept `template_type: string` and render {@name}.
  {
    await dataInput.fill('{"name":"Alice"}')
    await tmplInput.fill('hello {@name}')
    const resp = await clickRunAndWait(page, 0, { timeoutMs: 8000 })
    const out = await getOutputTexts(page, 0)
    console.log('case 3:', resp.status?.() ?? 'no-req', '|', out.output.slice(0, 60))
    if (resp && resp.status && resp.status() !== 200) {
      log.log('ui', 'string template should return 200', { status: resp.status() })
    }
    if (out.hasResult === 0 || !out.output.includes('hello Alice')) {
      log.log('ui', 'string template did not render hello Alice', { outputPreview: out.output.slice(0, 80) })
    }
    await page.screenshot({ path: screenshotPath(SHOTS, '03-string-template'), fullPage: true })
  }

  // ── case 4: section-1's editor must NOT carry the section-0's edits ──
  {
    const sec1 = page.locator('[data-testid^="learn-jr-section-"][data-runnable="true"]').nth(1)
    const sec1Tmpl = sec1.locator('textarea[data-testid$="-template"]').first()
    if (await sec1Tmpl.count() > 0) {
      const val = await sec1Tmpl.inputValue().catch(() => '')
      if (!val || val.trim() === '') {
        log.log('ui', 'section 1 template textarea empty (cross-section state bleed)', { val })
      }
      console.log('section 1 template preview:', val.slice(0, 60))
    }
  }

  await browser.close()

  console.log('\n===== SUMMARY =====')
  console.log('total issues:', log.summary().length)
  writeReport(SHOTS, 'issues', { errors: log.summary() })
  process.exit(log.hasFailures() ? 1 : 0)
})().catch((e) => {
  console.error('test crashed:', e)
  process.exit(2)
})
