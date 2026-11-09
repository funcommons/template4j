// Browser launch helper shared by all e2e scripts.
// Uses the system Chrome to avoid downloading playwright's bundled chromium
// (TLS-restricted environment may block playwright's cdn downloads).

export const CHROMIUM_PATH_HINT = '/opt/homebrew/lib/node_modules/playwright/index.mjs'

export const DEFAULT_VIEWPORT = { width: 1440, height: 900 }

export async function launchBrowser(opts = {}) {
  const { chromium } = await import(CHROMIUM_PATH_HINT)
  const viewport = opts.viewport || DEFAULT_VIEWPORT
  return chromium.launch({
    headless: true,
    channel: 'chrome',
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  }).then(async (browser) => {
    const ctx = await browser.newContext({ viewport, ...opts.context })
    return { browser, context: ctx, page: await ctx.newPage() }
  })
}

export async function waitForReady(page, base, opts = {}) {
  const url = opts.url || base
  await page.goto(url, { waitUntil: 'networkidle', timeout: 30000 })
  if (opts.selector) {
    await page.waitForSelector(opts.selector, { timeout: 15000 })
  }
  await page.waitForTimeout(opts.idleMs ?? 800)
}
