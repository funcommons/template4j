// BL-01 门户与首页: hero、示例 tab、CTA 跳转、功能卡片、依赖复制、统计区

import { BL, main, BASE } from './lib.mjs'

main(new BL({
  id: 'BL-01',
  name: '门户与首页',
  slug: 'bl-01-home',
  desc: '官网首页：hero 区、示例演示 tab、CTA、功能卡片、依赖坐标复制、统计数字',
  cases: [
    ['H-01', '首页加载整体渲染', async (t) => {
      await t.goto('/', { selector: '.home', idleMs: 1500 })
      const hero = await t.page.locator('.hero h1').textContent().catch(() => '')
      t.expect(hero.includes('JSON') || hero.includes('模板'), `hero 标题应包含 JSON/模板, got: ${hero}`)
      t.expect(await t.page.locator('.hero-tabs .hero-tab').count() > 0, 'hero 示例 tabs 缺失')
      t.expect(await t.page.locator('.feature-card').count() >= 4, '功能卡片应 ≥ 4 张')
      t.expect(await t.page.locator('.cta .el-button').count() >= 2, 'CTA 按钮缺失')
      await t.shot('home-full', { key: true })
    }],

    ['H-02', 'Hero 示例 Tab 切换联动', async (t) => {
      await t.goto('/', { selector: '.hero', idleMs: 1500 })
      const tabs = t.page.locator('.hero-tab')
      const n = await tabs.count()
      t.expect(n >= 2, `hero 示例 tab 应 ≥ 2 个, got ${n}`)
      const firstTpl = await t.page.locator('.hero-pane').first().textContent()
      await t.shot('tab-initial', { key: true })
      for (let i = 1; i < n; i++) {
        await tabs.nth(i).click()
        await t.settle(900) // renderHero 走 /api/try
        const tpl = await t.page.locator('.hero-pane').first().textContent()
        if (tpl.trim() === firstTpl.trim()) t.warn(`tab#${i} 切换后模板内容未变化`)
      }
      await t.shot('tab-switched', { key: true })
    }],

    ['H-03', 'CTA 跳转 Playground', async (t) => {
      await t.goto('/', { selector: '.cta', idleMs: 800 })
      await t.page.locator('.cta .el-button--primary').click()
      await t.page.waitForURL('**/playground', { timeout: 8000 })
      await t.page.waitForSelector('.playground', { timeout: 10000 })
      await t.settle(1200)
      t.expect(t.page.url().endsWith('/playground'), '应跳转到 /playground')
      await t.shot('cta-playground', { key: true })
    }],

    ['H-04', 'CTA 跳转 Learn', async (t) => {
      await t.goto('/', { selector: '.cta', idleMs: 800 })
      await t.btn(/阅读文档|Read/).click()
      await t.page.waitForURL('**/learn**', { timeout: 8000 })
      await t.page.waitForSelector('[data-testid="learn-page"]', { timeout: 10000 })
      t.expect(t.page.url().includes('/learn'), '应跳转到 /learn')
      await t.shot('cta-learn', { key: true })
    }],

    ['H-05', 'Maven 依赖坐标复制', async (t) => {
      await t.goto('/', { selector: '.install', idleMs: 800 })
      const code = await t.page.locator('.install code, .install pre').first().textContent().catch(() => '')
      t.expect(code.includes('template4j'), `安装代码块应包含 template4j, got: ${code.slice(0, 80)}`)
      await t.btn(/复制|Copy/, '.install').click()
      await t.settle(400)
      const copied = await t.page.evaluate(() => window.__copied || [])
      t.expect(copied.length > 0 && copied[0].includes('template4j'),
        `复制内容应包含 template4j, got: ${JSON.stringify(copied)}`)
      await t.shot('copy-deps', { key: true })
    }],

    ['H-06', '统计数字与 MCP 提示区', async (t) => {
      await t.goto('/', { selector: '.stats', idleMs: 800 })
      const stats = await t.page.locator('.stats').textContent()
      t.expect(/\d/.test(stats), `统计区应包含数字, got: ${stats.slice(0, 60)}`)
      const hint = await t.page.locator('.hint').textContent().catch(() => '')
      t.expect(hint.includes('mcp') || hint.includes('MCP'), `MCP 提示应存在, got: ${hint.slice(0, 80)}`)
      await t.shot('stats-mcp-hint')
    }],

    ['H-07', 'Hero 门面演示开箱可渲染', async (t) => {
      await t.goto('/', { selector: '.hero', idleMs: 2000 })
      const out = await t.page.locator('.hero-pane').nth(1).textContent().catch(() => '')
      t.expect(!/mock 端点|needsMock/i.test(out || ''),
        `首页 hero 演示应展示渲染结果, 实际显示兜底文案: "${(out || '').trim().slice(0, 80)}" (后端缺 /users /orders mock 端点, /api/try 返回 400)`)
      await t.shot('hero-demo-output', { key: true })
    }],
  ],
}))
