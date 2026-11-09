// Smoke test for the /learn page: hero, sidebar groupings, first run, copy
// button, search filter, responsive viewports.

import { resolve } from 'node:path'
import { launchBrowser, waitForReady } from './lib/launch.mjs'
import {
  IssueLog,
  installIssueCollectors,
  assertNoHorizontalOverflow,
} from './lib/assert.mjs'
import { expandAllSubmenus, waitForLearnReady } from './lib/nav.mjs'
import { TESTID, I18N_LABEL, labelRegex } from './lib/selectors.mjs'
import { ensureDir, screenshotPath, writeReport } from './lib/io.mjs'

const BASE = process.env.BASE || 'http://localhost:5173'
const SHOTS = resolve(process.cwd(), 'e2e-shots')
ensureDir(SHOTS)

const log = new IssueLog()

;(async () => {
  const { browser, page } = await launchBrowser()
  installIssueCollectors(page, log)

  await waitForReady(page, BASE + '/learn')
  await waitForLearnReady(page, 600)
  await page.screenshot({ path: screenshotPath(SHOTS, '01-learn-default'), fullPage: true })

  // 1) hero sanity
  const heroTitle = await page.locator(`[data-testid="${TESTID.title}"]`).first().textContent().catch(() => null)
  console.log('hero title:', heroTitle)
  if (!heroTitle || heroTitle.trim() === '') log.log('layout', '.hero h1 missing/empty')

  // 2) categories present
  const catCount = await page.locator(`[data-testid^="${TESTID.cat('')}"]`).count()
  console.log('categories:', catCount)
  if (catCount === 0) log.log('layout', 'no learn-cat-* in sidebar')

  // 3) expand all so .el-menu-item is interactable
  const initialCat = catCount
  await expandAllSubmenus(page)
  const itemCount = await page.locator(`[data-testid^="${TESTID.item('')}"]`).count()
  console.log('menu items visible after expand:', itemCount)
  if (itemCount === 0) log.log('layout', 'no learn-item-* visible even after expanding')
  await page.screenshot({ path: screenshotPath(SHOTS, '02-submenus-expanded'), fullPage: true })

  // 4) navigate by data-testid (no fragile regex on display text)
  const quickItem = page.locator(`[data-testid="${TESTID.item('quick-start')}"]`)
  if (await quickItem.count() > 0) {
    await quickItem.click()
    await page.waitForTimeout(1200)
    await page.screenshot({ path: screenshotPath(SHOTS, '03-quick-start'), fullPage: true })
  } else {
    log.log('ui', 'no quick-start item visible')
  }

  // 5) component-each
  const each = page.locator(`[data-testid="${TESTID.item('component-each')}"]`)
  if (await each.count() > 0) {
    await each.click()
    await page.waitForTimeout(1500)
    await page.screenshot({ path: screenshotPath(SHOTS, '04-component-each'), fullPage: true })
  }

  // 6) click first runnable section's Run button
  const runBtn = page.locator('button').filter({ hasText: labelRegex(...I18N_LABEL.run) }).first()
  if (await runBtn.count() > 0) {
    await runBtn.click()
    await page.waitForTimeout(1500)
    await page.screenshot({ path: screenshotPath(SHOTS, '05-after-run'), fullPage: true })
    const firstSection = page.locator('[data-testid^="learn-jr-section-"]').first()
    const placeholder = await firstSection.locator('.output-placeholder').count()
    const outText = (await firstSection.locator('.output-body').textContent().catch(() => '')) || ''
    if (placeholder > 0 || outText.includes('点击「运行」') || outText.toLowerCase().includes('click')) {
      log.log('render', 'first section did not produce a result', { placeholder, outText: outText.slice(0, 60) })
    }
  } else {
    log.log('ui', 'no Run button found')
  }

  // 7) search filter
  const search = page.locator(`[data-testid="${TESTID.search}"]`).first()
  if (await search.count() > 0) {
    await search.fill('each')
    await page.waitForTimeout(400)
    const left = await page.locator(`[data-testid^="${TESTID.item('')}"]`).count()
    console.log('after search "each" visible:', left)
    await page.screenshot({ path: screenshotPath(SHOTS, '06-search-each'), fullPage: true })
    await search.fill('')
    await page.waitForTimeout(200)
    await expandAllSubmenus(page)
  } else {
    log.log('ui', 'no search input')
  }

  // 8) copy button flip
  const copyBtns = page.locator('[data-testid^="learn-copy-"]')
  if (await copyBtns.count() > 0) {
    await copyBtns.first().click().catch(() => {})
    await page.waitForTimeout(800)
    const allText = await page.evaluate(() =>
      [...document.querySelectorAll('[data-testid^="learn-copy-"]')].map((b) => b.textContent?.trim())
    )
    const flipped = allText.some((t) => t && t.includes('✓'))
    if (!flipped) log.log('ui', 'no copy button flipped to ✓', { all: allText })
    if (!flipped) console.log('copy btn status:', allText)
  } else {
    log.log('ui', 'no copy button found')
  }

  // 9) responsive viewports
  for (const [name, w, h] of [
    ['07-narrow', 600, 900],
    ['08-tablet', 800, 1100],
    ['09-phone',  380, 800],
  ]) {
    await page.setViewportSize({ width: w, height: h })
    await page.waitForTimeout(500)
    await page.screenshot({ path: screenshotPath(SHOTS, name), fullPage: true })
    if (w >= 600) {
      const overflow = await assertNoHorizontalOverflow(page, `${w}px`)
      if (overflow) log.log('layout', `horizontal overflow @ ${w}px`, overflow)
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
