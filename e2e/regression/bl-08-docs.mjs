// BL-08 文档中心 Docs: intro-home 加载、markdown/可运行 section、Open in Try

import { BL, main } from './lib.mjs'

main(new BL({
  id: 'BL-08',
  name: '文档中心 Docs',
  slug: 'bl-08-docs',
  desc: '文档详情展示（markdown section、可运行 json-renderer section、Java 调用代码）与 Try 联动',
  cases: [
    ['D-01', '页面加载 intro-home', async (t) => {
      await t.goto('/docs', { selector: '.docs', idleMs: 1500 })
      const loading = await t.page.locator('.docs .loading').count()
      t.expect(loading === 0, '文档仍在加载态')
      const err = await t.page.locator('.docs .error').count()
      t.expect(err === 0, '文档加载不应报错')
      const sections = await t.page.locator('.docs .sections > div').count()
      t.expect(sections >= 1, `文档 section 应 ≥1, got ${sections}`)
      await t.shot('docs-load', { key: true })
    }],

    ['D-02', 'Markdown section 内容', async (t) => {
      await t.goto('/docs', { selector: '.docs', idleMs: 1500 })
      const md = await t.page.locator('.docs .md-section').count()
      t.expect(md >= 1, `应有 markdown section, got ${md}`)
      const txt = await t.page.locator('.docs .md-section').first().textContent()
      t.expect(txt.trim().length > 20, `markdown 内容过短: ${txt.slice(0, 60)}`)
      await t.shot('docs-markdown')
    }],

    ['D-03', '可运行 section 与 Java 调用代码', async (t) => {
      await t.goto('/docs', { selector: '.docs', idleMs: 1500 })
      const jr = await t.page.locator('.docs .jr-section').count()
      if (jr === 0) {
        t.note('intro-home 无可运行 section（仅 markdown）— 记录为内容建议')
        return
      }
      const runBtn = t.page.locator('.docs .jr-section').first().locator('button').filter({ hasText: /运行|Run/ }).first()
      await runBtn.click()
      await t.settle(1800)
      const result = await t.page.locator('.docs .jr-result').first().textContent().catch(() => '')
      t.expect(result.length > 2, `运行 section 应有输出, got: ${result.slice(0, 80)}`)
      const code = await t.page.locator('.docs .jr-section pre').count()
      t.expect(code >= 1, '应展示 Java 调用代码')
      await t.shot('docs-run-section', { key: true })
    }],

    ['D-04', 'Open in Try 联动跳转', async (t) => {
      await t.goto('/docs', { selector: '.docs', idleMs: 1500 })
      const btn = t.page.locator('.docs button').filter({ hasText: /playground 中打开|Open in Try/i }).first()
      if (await btn.count() === 0) { t.note('无 Open in Try 入口（无可运行 section）'); return }
      await btn.click()
      await t.page.waitForURL('**/try', { timeout: 8000 })
      await t.settle(600)
      const tpl = await t.page.locator('.editors textarea').nth(0).inputValue()
      t.expect(tpl.trim().length > 0, '跳转 Try 后模板应被带入')
      await t.shot('docs-open-in-try', { key: true })
    }],
  ],
}))
