// Sidebar nav helpers — Element Plus sub-menu default-collapsed, so most
// tests have to expand every category first. Centralise that here.

/**
 * Expand every sub-menu in the sidebar so .el-menu-item is interactable.
 * Idempotent: only clicks titles that report aria-expanded=false.
 */
export async function expandAllSubmenus(page) {
  const titles = page.locator('[data-testid="learn-sidebar"] .el-sub-menu__title')
  const n = await titles.count()
  for (let i = 0; i < n; i++) {
    const t = titles.nth(i)
    try {
      const expanded = (await t.getAttribute('aria-expanded')) === 'true'
      if (!expanded) await t.click({ timeout: 2000 })
    } catch {}
    await page.waitForTimeout(80)
  }
}

/**
 * Wait for the .learn-page data-testid to attach, then wait for `idleMs`
 * to let any lazy side-effects settle.
 */
export async function waitForLearnReady(page, idleMs = 800) {
  await page.waitForSelector('[data-testid="learn-page"]', { timeout: 15000 })
  await page.waitForTimeout(idleMs)
}
