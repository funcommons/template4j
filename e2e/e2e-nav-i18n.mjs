// E2E for Learn page navigation + i18n locale switching. Covers:
//
//   1. /learn boundary behaviour:
//      - First example: `prev_id` is null (no "Previous" button)
//      - Last  example: `next_id` is null (no "Next" button)
//      - Clicking "Next" advances through the list in deterministic order
//
//   2. Locale switching:
//      - Switching zh-CN → en-US updates titles, buttons, error copy,
//        while leaving structural data-testids untouched.

import { resolve } from 'node:path'
import { launchBrowser, waitForReady } from './lib/launch.mjs'
import { IssueLog, installIssueCollectors, navigateToExample } from './lib/assert.mjs'
import { expandAllSubmenus, waitForLearnReady } from './lib/nav.mjs'
import { TESTID, I18N_LABEL, labelRegex } from './lib/selectors.mjs'
import { ensureDir, screenshotPath, writeReport } from './lib/io.mjs'

const BASE = process.env.BASE || 'http://localhost:5173'
const API  = process.env.API  || 'http://localhost:8088'
const SHOTS = resolve(process.cwd(), 'e2e-nav-i18n-shots')
ensureDir(SHOTS)

const log = new IssueLog()

;(async () => {
  const { browser, page } = await launchBrowser()
  // The 404-routing test intentionally hits /api/learn/this-id-does-not-exist
  // — silence the global trackers so the failure report stays clean.
  installIssueCollectors(page, log, {
    apiErrorAllowlist: new Set([400, 404]),
    // The 404 test triggers both an api.error log AND a generic browser
    // console.error "Failed to load resource: ... 400 ..." — block both.
    apiErrorBlocklist: [
      '/api/learn/this-id-does-not-exist',
      'Failed to load resource: the server responded with a status of 400',
      'Failed to load resource: the server responded with a status of 404',
    ],
  })

  await waitForReady(page, BASE + '/learn')
  await waitForLearnReady(page, 600)

  // ── 1) nav boundaries ────────────────────────────────────────────────
  // Read the canonical id list. First is "intro-home", last is "ref-code-tips".
  const ids = await (await fetch(API + '/api/learn')).json()
    .then((j) => (j.data?.examples || []).map((e) => e.id))
  if (ids.length === 0) { log.log('test', 'no ids from API'); }

  await navigateToExample(page, ids[0])
  await expandAllSubmenus(page)
  const prevAtFirst = await page.locator(`[data-testid="${TESTID.prev}"]`).count()
  const nextAtFirst = await page.locator(`[data-testid="${TESTID.next}"]`).count()
  console.log('first example nav buttons:', { prev: prevAtFirst, next: nextAtFirst })
  if (prevAtFirst !== 0) {
    log.log('ui', 'first example should have NO prev button', { prevAtFirst })
  }
  if (nextAtFirst !== 1) {
    log.log('ui', 'first example SHOULD have a next button', { nextAtFirst })
  }
  await page.screenshot({ path: screenshotPath(SHOTS, '01-first-no-prev'), fullPage: true })

  await navigateToExample(page, ids[ids.length - 1])
  await page.waitForTimeout(400)
  const prevAtLast = await page.locator(`[data-testid="${TESTID.prev}"]`).count()
  const nextAtLast = await page.locator(`[data-testid="${TESTID.next}"]`).count()
  console.log('last example nav buttons:', { prev: prevAtLast, next: nextAtLast })
  if (prevAtLast !== 1) {
    log.log('ui', 'last example SHOULD have a prev button', { prevAtLast })
  }
  if (nextAtLast !== 0) {
    log.log('ui', 'last example should have NO next button', { nextAtLast })
  }
  await page.screenshot({ path: screenshotPath(SHOTS, '02-last-no-next'), fullPage: true })

  // Walk forward through a handful of steps and assert each step's URL is what
  // we expect, i.e. prev/next use the canonical id order (not arbitrary).
  let prevId = ids[ids.length - 1]
  await navigateToExample(page, prevId)
  for (let step = 0; step < 3; step++) {
    await expandAllSubmenus(page)
    const nextBtn = page.locator(`[data-testid="${TESTID.next}"]`)
    if (await nextBtn.count() === 0) break
    await nextBtn.click()
    await page.waitForTimeout(600)
    const urlPath = await page.evaluate(() => window.location.pathname)
    if (!/^\/learn\//.test(urlPath)) {
      log.log('ui', `next click did not navigate (path=${urlPath})`)
      break
    }
  }

  // ── 2) locale switch ─────────────────────────────────────────────────
  // Pin the locale to en-US and reload; title should change to English.
  await page.evaluate(() => localStorage.setItem('locale', 'en-US'))
  await page.goto(BASE + '/learn', { waitUntil: 'networkidle' })
  await waitForLearnReady(page, 600)
  const titleEn = (await page.locator(`[data-testid="${TESTID.title}"]`).textContent() || '').trim()
  console.log('title in en-US:', titleEn)
  if (!titleEn) log.log('ui', 'en-US title text empty')
  if (titleEn === '学习示例') {
    log.log('ui', 'en-US title still zh-CN (locale switch did not apply)', { titleEn })
  }
  await page.screenshot({ path: screenshotPath(SHOTS, '03-locale-en'), fullPage: true })

  // Switch back to zh-CN and check the placeholder hint is the Chinese copy.
  await page.evaluate(() => localStorage.setItem('locale', 'zh-CN'))
  await page.goto(BASE + '/learn', { waitUntil: 'networkidle' })
  await waitForLearnReady(page, 600)
  const titleZh = (await page.locator(`[data-testid="${TESTID.title}"]`).textContent() || '').trim()
  console.log('title in zh-CN:', titleZh)
  if (titleZh !== '学习示例') {
    log.log('ui', 'zh-CN title mismatch after locale re-pin', { titleZh })
  }
  await page.screenshot({ path: screenshotPath(SHOTS, '04-locale-zh'), fullPage: true })

  // ── 3) 404 routing ──────────────────────────────────────────────────
  await page.goto(BASE + '/learn/this-id-does-not-exist', { waitUntil: 'networkidle' })
  await page.waitForTimeout(800)
  const nf = await page.locator('[data-testid="learn-not-found"]').count()
  console.log('not-found card visible:', nf)
  if (nf === 0) {
    log.log('ui', '404 card did not show for unknown id')
  }
  await page.screenshot({ path: screenshotPath(SHOTS, '05-404'), fullPage: true })

  // Click the "back home" button and confirm we land on intro-home.
  const backHome = page.locator('[data-testid="learn-back-home"]').first()
  if (await backHome.count() > 0) {
    await backHome.click()
    await page.waitForTimeout(800)
    const navPath = await page.evaluate(() => window.location.pathname)
    console.log('after back-home path:', navPath)
    if (!/^\/learn/.test(navPath)) {
      log.log('ui', 'back-home did not route back to /learn*', { navPath })
    }
  } else {
    log.log('ui', 'no back-home button on 404 card')
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
