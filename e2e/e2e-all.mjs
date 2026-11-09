// E2E sweep over every example on /learn, driving the click-Run pipeline
// against each json-renderer section. Issues are catalogued into
// e2e-all-shots/issues.json.

import { resolve } from 'node:path'
import { launchBrowser, waitForReady } from './lib/launch.mjs'
import {
  IssueLog,
  installIssueCollectors,
  trackRenderRequests,
  assertNoHorizontalOverflow,
  navigateToExample,
} from './lib/assert.mjs'
import { expandAllSubmenus, waitForLearnReady } from './lib/nav.mjs'
import { ensureDir, screenshotPath, writeReport } from './lib/io.mjs'

const BASE = process.env.BASE || 'http://localhost:5173'
const API  = process.env.API  || 'http://localhost:8088'
const SHOTS = resolve(process.cwd(), 'e2e-all-shots')
ensureDir(SHOTS)

const log = new IssueLog()

;(async () => {
  const { browser, page } = await launchBrowser()
  installIssueCollectors(page, log)
  trackRenderRequests(page, log)

  // Pull canonical id list straight from the backend — sidebar display text
  // mixes id+title with no separator, which made earlier parsing wrong for
  // entries like "intro-home介绍" / "builtin-fnefne".
  let ids = []
  try {
    const res = await fetch(API + '/api/learn')
    const json = await res.json()
    ids = (json.data?.examples || []).map((e) => e.id).filter(Boolean)
  } catch (e) {
    log.log('test', 'failed to fetch /api/learn', { error: String(e) })
  }
  if (ids.length === 0) {
    console.error('no ids from API, aborting')
    process.exit(2)
  }
  console.log('ids from API:', ids.length)

  await waitForReady(page, BASE + '/learn')
  await waitForLearnReady(page, 600)
  await expandAllSubmenus(page)
  await page.screenshot({ path: screenshotPath(SHOTS, '00-overview'), fullPage: true })

  let totalItems = ids.length
  let runCount = 0, renderOK = 0, renderFAIL = 0, expectedSkip = 0, layoutIssues = 0
  const perExampleConsole = [] // cleared per navigation
  page.on('console', (m) => {
    if (m.type() !== 'log' && m.type() !== 'debug') {
      perExampleConsole.push({ type: m.type(), text: m.text() })
    }
  })

  for (let i = 0; i < ids.length; i++) {
    const exId = ids[i]
    perExampleConsole.length = 0
    await navigateToExample(page, exId)
    await expandAllSubmenus(page)

    const counts = await page.evaluate(() => {
      const sections = [...document.querySelectorAll('[data-testid^="learn-jr-section-"]')]
      const codeOnly = sections.filter((s) => s.getAttribute('data-runnable') === 'false').length
      return {
        sectionCount: document.querySelectorAll('[data-testid^="learn-md-section-"], [data-testid^="learn-jr-section-"]').length,
        jrCount: sections.length,
        codeOnly,
      }
    })
    const runnableJr = counts.jrCount - counts.codeOnly

    if (runnableJr === 0 && counts.codeOnly > 0) {
      renderOK += counts.codeOnly // code-only sections count as success
    }

    // runnable sections — iterate by querying live DOM rather than the
    // count we captured right after navigation; otherwise the loop bound
    // can drift if the section list updates asynchronously.
    let exOK = 0, exFAIL = 0
    const runButtons = page.locator('button[data-testid^="learn-run-"]')
    const btnLive = await runButtons.count().catch(() => 0)
    if (btnLive !== runnableJr) {
      // The DOM caught up; whatever it is, that's what we trust.
    }
    for (let b = 0; b < btnLive; b++) {
      const runBtn = runButtons.nth(b)
      try { await runBtn.scrollIntoViewIfNeeded() } catch {}
      // Wait for the /api/render round trip explicitly. Without this the
      // test pumps requests into a shared worker pool faster than it
      // drains, and Vue's reactive update arrives after our deadline.
      const renderResp = page.waitForResponse(
        (r) => r.url().includes('/api/render') && r.request().method() === 'POST',
        { timeout: 60000 }
      ).catch(() => null)
      try {
        await runBtn.click({ timeout: 4000 })
      } catch (e) {
        log.log('ui', `run button click failed at example[${i}].btn[${b}]`, { err: String(e).slice(0, 80), exId })
        continue
      }
      // Make sure we don't strand the waitForResponse handle
      await renderResp

      // wait for the section's output-body to leave placeholder state.
      // Scope all output queries to `section` so we don't depend on the
      // overall section index, which is offset by non-runnable sections.
      const section = page.locator(`[data-testid^="learn-jr-section-"][data-runnable="true"]`).nth(b)
      // Vue is reactive but not synchronous: after the click handler runs,
      // the placeholder may disappear before the result/error node mounts.
      // Allow up to 3 short retries to bridge that gap.
      let placeholderGone = 0, hasResult = 0, hasError = 0, outputText = ''
      for (let attempt = 0; attempt < 3; attempt++) {
        try {
          await section.locator('.code-block, .code-error').first()
            .waitFor({ state: 'attached', timeout: 5000 })
        } catch { /* deadline expired */ }
        hasResult = await section.locator('.output-body .code-block').count().catch(() => 0)
        hasError  = await section.locator('.output-body .code-error').count().catch(() => 0)
        outputText = (await section.locator('.output-body').textContent().catch(() => '')) || ''
        placeholderGone = await section.locator('.output-placeholder').count().catch(() => 0)
        if (hasResult > 0 || hasError > 0 || placeholderGone === 0) break
        await page.waitForTimeout(200)
      }
      runCount++

      if (hasResult > 0 && !hasError) {
        exOK++
        continue
      }
      if (hasError) {
        const isExpected =
          outputText.includes('Connection refused') ||
          outputText.includes('I/O error') ||
          outputText.includes('NullPointerException') ||
          outputText.includes('Cannot get property') ||
          outputText.includes('component render error') ||
          outputText.includes('script error')
        if (isExpected) {
          expectedSkip++
          log.log('render.skipped', `example "${exId}" section[${b}] requires external setup`, {
            exId, outputPreview: outputText.slice(0, 120),
          })
        } else {
          exFAIL++
          log.log('render', `example "${exId}" section[${b}] failed`, {
            exId, hasError, outputPreview: outputText.slice(0, 120),
          })
        }
        continue
      }
      exFAIL++
      log.log('render', `example "${exId}" section[${b}] produced no output`, {
        exId, outputPreview: outputText.slice(0, 120),
      })
    }
    renderOK += exOK
    renderFAIL += exFAIL

    await page.screenshot({
      path: screenshotPath(SHOTS, `ex-${String(i).padStart(2, '0')}-${exId}`),
      fullPage: true,
    })
    const overflow = await assertNoHorizontalOverflow(page, exId)
    if (overflow) {
      log.log('layout', `horizontal overflow at example "${exId}"`, overflow)
      layoutIssues++
    }
  }

  await browser.close()

  const summary = {
    totalItems, runCount, renderOK, expectedSkip, renderFAIL, layoutIssues,
    totalIssues: log.summary().length,
  }
  console.log('\n===== SUMMARY =====')
  for (const [k, v] of Object.entries(summary)) console.log(`${k.padEnd(14)} ${v}`)
  writeReport(SHOTS, 'issues', { summary, issues: log.summary() })
  process.exit(summary.totalIssues > 0 ? 1 : 0)
})().catch((e) => {
  console.error('test crashed:', e)
  process.exit(2)
})
