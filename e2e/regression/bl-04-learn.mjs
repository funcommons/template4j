// BL-04 学习中心 Learn: 列表/搜索/详情/运行/变量/复制/翻页/404/全量 36 例遍历

import { BL, main, BASE } from './lib.mjs'

async function allExampleIds() {
  const res = await fetch(`${BASE}/api/learn`)
  const body = await res.json()
  const list = body.data?.examples || body.examples || []
  return list.map(e => e.id)
}

main(new BL({
  id: 'BL-04',
  name: '学习中心 Learn',
  slug: 'bl-04-learn',
  desc: '示例导航、搜索、详情交互（运行/变量/复制/翻页）、404、全部示例遍历回归',
  cases: [
    ['L-01', '列表加载：分类侧栏与示例数量', async (t) => {
      await t.goto('/learn', { selector: '[data-testid="learn-page"]', idleMs: 1200 })
      const sidebar = t.page.locator('[data-testid="learn-sidebar"]')
      t.expect(await sidebar.count() === 1, '侧栏缺失')
      const cats = await sidebar.locator('.el-sub-menu__title, .el-menu-item').count()
      t.expect(cats >= 5, `分类数应 ≥5, got ${cats}`)
      const ids = await allExampleIds()
      t.expect(ids.length === 36, `API 示例应为 36 个, got ${ids.length}`)
      await t.shot('learn-list', { key: true })
    }],

    ['L-02', '搜索过滤', async (t) => {
      await t.goto('/learn', { selector: '[data-testid="learn-page"]', idleMs: 1200 })
      const before = await t.page.locator('[data-testid="learn-menu"] .el-menu-item').count()
      // el-input 把 data-testid 透传到内部 <input> 上
      await t.page.locator('input[data-testid="learn-search"]').fill('if')
      await t.settle(700)
      const after = await t.page.locator('[data-testid="learn-menu"] .el-menu-item').count()
      t.expect(after > 0 && after < before, `搜索 'if' 应收窄列表 (${before} → ${after})`)
      await t.shot('search-if', { key: true })
      await t.page.locator('input[data-testid="learn-search"]').fill('')
      await t.settle(600)
      const restored = await t.page.locator('[data-testid="learn-menu"] .el-menu-item').count()
      t.expect(restored === before, `清空搜索应恢复列表 (${restored} vs ${before})`)
    }],

    ['L-03', '默认详情加载（首个示例）', async (t) => {
      await t.goto('/learn', { selector: '[data-testid="learn-detail"]', idleMs: 1200 })
      const title = await t.page.locator('.intro-title').textContent().catch(() => '')
      t.expect(title.trim().length > 0, '详情标题为空')
      const sections = await t.page.locator('[data-testid="learn-sections"] > section').count()
      t.expect(sections >= 1, `详情 section 应 ≥1, got ${sections}`)
      await t.shot('detail-default', { key: true })
    }],

    ['L-04', '直达详情与上一个/下一个翻页', async (t) => {
      await t.goto('/learn/quick-start', { selector: '[data-testid="learn-detail"]', idleMs: 1000 })
      const t1 = await t.page.locator('.intro-title').textContent()
      await t.page.locator('[data-testid="learn-next"]').click()
      await t.settle(900)
      const t2 = await t.page.locator('.intro-title').textContent()
      t.expect(t1.trim() !== t2.trim(), `点「下一个」后标题应变化 (${t1} → ${t2})`)
      await t.page.locator('[data-testid="learn-prev"]').click()
      await t.settle(900)
      const t3 = await t.page.locator('.intro-title').textContent()
      t.expect(t3.trim() === t1.trim(), `点「上一个」应回到原示例 (${t3} vs ${t1})`)
      await t.shot('prev-next-nav', { key: true })
    }],

    ['L-05', '运行可执行示例', async (t) => {
      await t.goto('/learn/quick-start', { selector: '[data-testid="learn-detail"]', idleMs: 1000 })
      const runBtns = t.page.locator('[data-testid^="learn-run-"]')
      const n = await runBtns.count()
      t.expect(n >= 1, 'quick-start 应有可运行 section')
      if (n >= 1) {
        await runBtns.first().click()
        await t.settle(1500)
        const out = await t.page.locator('[data-testid^="learn-output-"]').first().textContent().catch(() => '')
        const result = await t.page.locator('[data-testid^="learn-result-"]').first().textContent().catch(() => '')
        const err = await t.page.locator('[data-testid^="learn-error-"]').first().textContent().catch(() => '')
        const combined = (result || '') + (out || '')
        t.expect(combined.trim().length > 0 || (err || '').length > 0,
          '运行后应有结果或错误输出')
        if ((err || '').trim()) t.fail(`运行报错: ${err.slice(0, 120)}`)
        await t.shot('run-quickstart', { key: true })
      }
    }],

    ['L-06', '修改变量后重新运行', async (t) => {
      await t.goto('/learn/text-data', { selector: '[data-testid="learn-detail"]', idleMs: 1000 })
      const inputs = t.page.locator('[data-testid^="learn-input-"]')
      const nVars = await inputs.count()
      if (nVars === 0) { t.note('该示例无变量输入, 跳过断言'); return }
      await inputs.first().fill('regression-var-42')
      const runBtns = t.page.locator('[data-testid^="learn-run-"]')
      if (await runBtns.count() >= 1) {
        await runBtns.first().click()
        await t.settle(1500)
        const result = await t.page.locator('[data-testid^="learn-result-"]').first().textContent().catch(() => '')
        t.expect((result || '').includes('regression-var-42') || (result || '').trim().length === 0,
          `结果应反映新变量值, got: ${(result || '').slice(0, 100)}`)
      }
      await t.shot('var-edit-run', { key: true })
    }],

    ['L-07', '复制按钮', async (t) => {
      await t.goto('/learn/quick-start', { selector: '[data-testid="learn-detail"]', idleMs: 1000 })
      const copies = t.page.locator('[data-testid^="learn-copy-"]')
      const n = await copies.count()
      t.expect(n >= 1, `应有复制按钮, got ${n}`)
      if (n >= 1) {
        await copies.first().click()
        await t.settle(300)
        const copied = await t.page.evaluate(() => window.__copied || [])
        t.expect(copied.length >= 1, '点击复制后剪贴板应有内容')
      }
    }],

    ['L-08', '未知示例 id → 404 提示页', async (t) => {
      await t.goto('/learn/definitely-not-exist', { waitUntil: 'networkidle', idleMs: 1200 })
      const nf = t.page.locator('[data-testid="learn-not-found"]')
      t.expect(await nf.count() === 1, '未知 id 应展示 not-found 提示')
      const back = t.page.locator('[data-testid="learn-back-home"]')
      t.expect(await back.count() === 1, '应有返回按钮')
      if (await back.count()) {
        await back.click()
        await t.settle(800)
        t.expect(t.page.url().includes('/learn'), '返回按钮应回到 /learn')
      }
      await t.shot('not-found', { key: true })
    }],

    ['L-09', '全量遍历 36 个示例详情（零报错）', async (t) => {
      const ids = await allExampleIds()
      t.note(`共 ${ids.length} 个示例`)
      const bad = []
      for (const id of ids) {
        await t.goto(`/learn/${id}`, { waitUntil: 'networkidle', idleMs: 350 })
        const nf = await t.page.locator('[data-testid="learn-not-found"]').count()
        const detail = await t.page.locator('[data-testid="learn-detail"]').count()
        const loadErr = await t.page.evaluate(() => document.querySelector('.error, [class*="load-error"]')?.textContent || '')
        const secCount = await t.page.locator('[data-testid="learn-sections"] > section').count()
        if (nf || !detail || secCount === 0 || /无法加载|load error/i.test(loadErr)) {
          bad.push({ id, nf, detail, secCount, loadErr: loadErr.slice(0, 80) })
          await t.shot(`traverse-${id}`, { fullPage: true })
        }
        const pe = t.pageErrors()
        if (pe.length) bad.push({ id, pageErrors: pe.map(e => e.text.slice(0, 100)) })
      }
      t.expect(bad.length === 0, `${bad.length} 个示例异常: ${JSON.stringify(bad.slice(0, 5))}`)
      await t.shot('traverse-last', { key: true })
    }],

    ['L-10', '侧栏分类展开与收起', async (t) => {
      await t.goto('/learn', { selector: '[data-testid="learn-page"]', idleMs: 1200 })
      const titles = t.page.locator('[data-testid="learn-sidebar"] .el-sub-menu__title')
      const subs = t.page.locator('[data-testid="learn-sidebar"] .el-sub-menu')
      const n = await titles.count()
      t.expect(n >= 5, `分类应 ≥5, got ${n}`)
      // aria-expanded 挂在 li.el-sub-menu 上
      const ariaOf = (i) => subs.nth(i).getAttribute('aria-expanded')
      const before = await ariaOf(0)
      await titles.first().click()
      await t.settle(400)
      const toggled = await ariaOf(0)
      t.expect(toggled !== before, `点击分类应切换展开态 (${before} → ${toggled})`)
      await titles.first().click()
      await t.settle(400)
      const restored = await ariaOf(0)
      t.expect(restored === before, `再点应恢复原状态 (${restored} vs ${before})`)
      await t.shot('cat-toggle')
    }],
  ],
}))
