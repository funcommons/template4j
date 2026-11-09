// Centralised issue tracking for e2e scripts.
// All scripts share the same shape (`{ type, msg, extra }`) so consumers and
// dashboards can rely on a single issues.json schema.

export class IssueLog {
  constructor() {
    this.issues = []
  }
  log(type, msg, extra = {}) {
    const item = { type, msg, ...extra }
    this.issues.push(item)
    console.log(`[ISSUE][${type}] ${msg}`, JSON.stringify(extra))
    return item
  }
  summary() { return this.issues }
  hasFailures() { return this.issues.length > 0 }
}

/**
 * Wire console / pageerror / requestfailed / api error collectors.
 * Returns the IssueLog plus helpers to label by example id.
 *
 * `apiErrorAllowlist` (optional) is a Set of HTTP statuses that are EXPECTED
 * by the calling test — `/api/render` returning 400 for "blank template"
 * is exactly the kind of status to allowlist. Anything else still counts.
 *
 * `apiErrorBlocklist` (optional) is a Set of URL substrings whose 4xx/5xx
 * responses should be ignored — useful when a test intentionally triggers a
 * 404 to verify routing. Anything matching is suppressed entirely.
 */
export function installIssueCollectors(page, log, opts = {}) {
  const { apiErrorAllowlist, apiErrorBlocklist } = opts || {}
  const blocklist = Array.isArray(apiErrorBlocklist) ? apiErrorBlocklist : []
  page.on('console', (m) => {
    if (m.type() === 'error') {
      if (blocklist.some((s) => m.text().includes(s))) return
      log.log('console.error', m.text())
    }
  })
  page.on('pageerror', (e) => log.log('pageerror', e.message))

  page.on('requestfailed', (req) => {
    const url = req.url()
    if (url.includes('/__vite_ping') || url.endsWith('.hot-update.json')) return
    const errText = req.failure()?.errorText || ''
    if (errText.includes('ERR_ABORTED')) return // navigation-cancelled, not a real failure
    log.log('requestfailed', url, { failure: errText })
  })

  page.on('response', (resp) => {
    const u = resp.url()
    if (u.includes('/api/') && resp.status() >= 400) {
      if (apiErrorAllowlist && apiErrorAllowlist.has(resp.status())) return
      if (blocklist.some((s) => u.includes(s))) return
      log.log('api.error', `${resp.status()} ${u}`)
    }
  })
}

/**
 * Capture failed /api/render POSTs alongside their body and response snippet,
 * so we can locate the exact template that triggered the regression.
 */
export function trackRenderRequests(page, log) {
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
    if (resp.status() >= 400) {
      const text = await resp.text().catch(() => '')
      log.log('render.4xx', `${resp.status()} for ${meta.body?.slice(0, 60)}`, {
        status: resp.status(),
        bodyPreview: meta.body?.slice(0, 200),
        responsePreview: text.slice(0, 200),
      })
    }
  })
  return pending
}

/**
 * Assert there is no horizontal scrollbar overflow on the current viewport.
 * Tolerates a 4px slack for sub-pixel rendering.
 */
export async function assertNoHorizontalOverflow(page, label = 'viewport') {
  const m = await page.evaluate(() => ({
    docW: document.documentElement.scrollWidth,
    viewW: window.innerWidth,
  }))
  if (m.docW > m.viewW + 4) {
    return { overflow: true, ...m, label }
  }
  return null
}

/**
 * Drive the page to a specific example via the router exposed on window.__app.
 * If unavailable, fall back to history.pushState + popstate.
 */
export async function navigateToExample(page, id) {
  await page.evaluate((to) => {
    if (window.__app?.router?.push) {
      return window.__app.router.push(to)
    }
    history.pushState({}, '', to)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }, `/learn/${id}`)
  await page.waitForTimeout(900)
}
