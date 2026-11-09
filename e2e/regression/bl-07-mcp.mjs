// BL-07 MCP 集成: 步骤引导、配置复制、tools/list、render_template

import { BL, main } from './lib.mjs'

main(new BL({
  id: 'BL-07',
  name: 'MCP 集成',
  slug: 'bl-07-mcp',
  desc: 'MCP 接入三步引导、Claude 配置复制、tools/list 与 render_template 实测',
  cases: [
    ['M-01', '页面加载与三步引导', async (t) => {
      await t.goto('/mcp', { selector: '.mcp', idleMs: 800 })
      const steps = await t.page.locator('.step').count()
      t.expect(steps === 3, `应 3 步上手, got ${steps}`)
      const security = await t.page.locator('.security').textContent().catch(() => '')
      t.expect(/安全|security/i.test(security), '安全提示区缺失')
      await t.shot('mcp-page', { key: true })
    }],

    ['M-02', '复制 Claude 配置', async (t) => {
      await t.goto('/mcp', { selector: '.mcp', idleMs: 800 })
      await t.page.locator('.step').nth(1).locator('button').filter({ hasText: /复制|Copy/ }).click()
      await t.settle(300)
      const copied = await t.page.evaluate(() => window.__copied || [])
      t.expect(copied.length >= 1, '配置应写入剪贴板')
      const cfg = copied[copied.length - 1] || ''
      t.expect(cfg.includes('template4j') && cfg.includes('command'), `配置内容异常: ${cfg.slice(0, 120)}`)
      await t.shot('mcp-copy-config', { key: true })
    }],

    ['M-03', 'tools/list 调用', async (t) => {
      await t.goto('/mcp', { selector: '.mcp', idleMs: 800 })
      await t.btn(/tools\/list/).click()
      await t.page.waitForSelector('.output', { timeout: 15000 }).catch(() => {})
      const out = await t.page.locator('.output').textContent().catch(() => '')
      t.expect(out.includes('render_template'), `tools/list 应列出 render_template, got: ${out.slice(0, 200)}`)
      t.expect(/tools/i.test(out), `输出应含 tools 数组, got: ${out.slice(0, 200)}`)
      await t.shot('mcp-tools-list', { key: true })
    }],

    ['M-04', 'render_template 调用', async (t) => {
      await t.goto('/mcp', { selector: '.mcp', idleMs: 800 })
      await t.btn(/render_template/).click()
      await t.page.waitForSelector('.output', { timeout: 15000 }).catch(() => {})
      const out = await t.page.locator('.output').textContent().catch(() => '')
      t.expect(out.length > 5, `render_template 无输出, got: ${out.slice(0, 120)}`)
      t.expect(/content/i.test(out), `输出应为 JSON-RPC content 结构, got: ${out.slice(0, 200)}`)
      await t.shot('mcp-render-call', { key: true })
    }],
  ],
}))
