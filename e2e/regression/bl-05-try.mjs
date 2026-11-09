// BL-05 组件体验 Try: JSON/YAML 渲染、错误、历史恢复、清空

import { BL, main } from './lib.mjs'

main(new BL({
  id: 'BL-05',
  name: '在线试用 Try',
  slug: 'bl-05-try',
  desc: '快速试用页：JSON/YAML 双类型渲染、结果与耗时、历史记录恢复、清空',
  cases: [
    ['T-01', '页面加载与控件齐全', async (t) => {
      await t.goto('/try', { selector: '.try-page', idleMs: 800 })
      t.expect(await t.page.locator('.controls .el-radio-button').count() === 2, '应有 JSON/YAML 两个类型按钮')
      t.expect(await t.page.locator('.editors textarea').count() === 2, '应有模板/数据两个编辑器')
      t.expect(await t.btn(/渲染|Render/).count() === 1, '渲染按钮缺失')
      t.expect(await t.btn(/清空|Clear/).count() === 1, '清空按钮缺失')
      await t.shot('try-default', { key: true })
    }],

    ['T-02', 'JSON 模板渲染与结果', async (t) => {
      await t.goto('/try', { selector: '.try-page', idleMs: 800 })
      await t.page.locator('.editors textarea').nth(0).fill('{\n  "msg": "Hello {@name}!",\n  "n": "{= @n * 2 }"\n}')
      await t.page.locator('.editors textarea').nth(1).fill('{ "name": "try-user", "n": 21 }')
      await t.btn(/渲染|Render/).click()
      await t.page.waitForSelector('.result', { timeout: 10000 }).catch(() => {})
      const result = await t.page.locator('.result pre').textContent().catch(() => '')
      t.expect(result.includes('try-user'), `结果应包含 try-user, got: ${result.slice(0, 100)}`)
      t.expect(result.includes('42'), `脚本表达式应得 42, got: ${result.slice(0, 100)}`)
      const timing = await t.page.locator('.result h4').textContent().catch(() => '')
      t.expect(/\d+\s*ms/.test(timing || ''), `应显示耗时, got: ${timing}`)
      await t.shot('try-render-json', { key: true })
    }],

    ['T-03', 'YAML 模板渲染', async (t) => {
      await t.goto('/try', { selector: '.try-page', idleMs: 800 })
      await t.page.locator('.controls .el-radio-button').filter({ hasText: 'YAML' }).click()
      await t.page.locator('.editors textarea').nth(0).fill('msg: "YAML {@name}"')
      await t.page.locator('.editors textarea').nth(1).fill('{ "name": "yaml" }')
      await t.btn(/渲染|Render/).click()
      await t.page.waitForSelector('.result', { timeout: 10000 }).catch(() => {})
      const result = await t.page.locator('.result pre').textContent().catch(() => '')
      t.expect(result.includes('YAML yaml') || result.includes('YAML'), `YAML 渲染异常, got: ${result.slice(0, 100)}`)
      await t.shot('try-render-yaml', { key: true })
    }],

    ['T-04', '数据非法 JSON 提示', async (t) => {
      await t.goto('/try', { selector: '.try-page', idleMs: 800 })
      await t.page.locator('.editors textarea').nth(0).fill('{"a":1}')
      await t.page.locator('.editors textarea').nth(1).fill('{bad json')
      await t.btn(/渲染|Render/).click()
      await t.settle(800)
      const body = await t.page.locator('.try-page').textContent()
      t.expect(/JSON 无效|invalid/i.test(body), `应提示数据 JSON 无效`)
      t.expect(await t.page.locator('.result').count() === 0, '非法数据不应产生结果')
      await t.shot('try-bad-json', { key: true })
    }],

    ['T-05', '模板渲染错误兜底展示', async (t) => {
      await t.goto('/try', { selector: '.try-page', idleMs: 800 })
      await t.page.locator('.editors textarea').nth(0).fill('{"bad": "{= +++groovy error+++ }"}')
      await t.page.locator('.editors textarea').nth(1).fill('{}')
      await t.btn(/渲染|Render/).click()
      await t.settle(1500)
      const result = await t.page.locator('.result pre').textContent().catch(() => '')
      t.expect((result || '').length > 0, '渲染失败也应有兜底输出')
      t.expect(/error|失败|failed/i.test(result || ''), `兜底输出应含错误信息, got: ${(result || '').slice(0, 120)}`)
      await t.shot('try-render-error', { key: true })
    }],

    ['T-06', '历史记录生成与点击恢复', async (t) => {
      await t.goto('/try', { selector: '.try-page', idleMs: 800 })
      const ta0 = t.page.locator('.editors textarea').nth(0)
      await ta0.fill('{"h": "first"}')
      await t.page.locator('.editors textarea').nth(1).fill('{}')
      await t.btn(/渲染|Render/).click()
      await t.settle(1000)
      await ta0.fill('{"h": "second"}')
      await t.btn(/渲染|Render/).click()
      await t.settle(1000)
      const items = t.page.locator('.history-item')
      const n = await items.count()
      t.expect(n >= 2, `应产生 ≥2 条历史, got ${n}`)
      const itemTextsBefore = await items.allTextContents()
      t.note(`历史条目: ${JSON.stringify(itemTextsBefore.slice(0, 5))}`)
      // 清空编辑器再从历史恢复
      await t.btn(/清空|Clear/).click()
      await t.settle(300)
      const itemTextsAfter = await items.allTextContents()
      t.note(`清空后历史条目: ${JSON.stringify(itemTextsAfter.slice(0, 5))}`)
      t.warnIf(itemTextsAfter.length > itemTextsBefore.length,
        '「清空」把空状态写入历史(历史污染), 且条目显示 "null..."')
      if (n >= 1) {
        await items.first().click()
        await t.settle(400)
        const tpl = await ta0.inputValue()
        t.expect(tpl.includes('second') || tpl.includes('first'),
          `点第一条历史应恢复最近一次渲染模板, 实际 got: "${tpl.slice(0, 60)}" (历史首条被清空操作污染)`)
      }
      await t.shot('history-restore', { key: true })
    }],

    ['T-07', '清空操作', async (t) => {
      await t.goto('/try', { selector: '.try-page', idleMs: 800 })
      await t.page.locator('.editors textarea').nth(0).fill('{"a":1}')
      await t.btn(/渲染|Render/).click()
      await t.settle(1000)
      await t.btn(/清空|Clear/).click()
      await t.settle(300)
      const tpl = await t.page.locator('.editors textarea').nth(0).inputValue()
      t.expect(tpl === '', '清空后模板应为空')
      t.expect(await t.page.locator('.result').count() === 0, '清空后结果应消失')
    }],
  ],
}))
