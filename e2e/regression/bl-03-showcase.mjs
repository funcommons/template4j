// BL-03 精选示例 Showcase: 卡片列表、展开收起互斥、可运行卡片、无端点提示

import { BL, main } from './lib.mjs'

main(new BL({
  id: 'BL-03',
  name: '精选示例 Showcase',
  slug: 'bl-03-showcase',
  desc: '示例卡片展示、展开/收起、live 运行（parallel / schema / mcp）、无端点卡片提示',
  cases: [
    ['S-01', '页面加载与 6 张卡片', async (t) => {
      await t.goto('/showcase', { selector: '.showcase', idleMs: 1000 })
      const cards = await t.page.locator('.card').count()
      t.expect(cards === 6, `应有 6 张示例卡片, got ${cards}`)
      const firstOpen = await t.page.locator('.card.open').count()
      t.expect(firstOpen === 1, `默认应展开 1 张卡片, got ${firstOpen}`)
      await t.shot('showcase-default', { key: true })
    }],

    ['S-02', '卡片展开/收起与互斥', async (t) => {
      await t.goto('/showcase', { selector: '.showcase', idleMs: 1000 })
      const head3 = t.page.locator('.card-head').nth(2)
      await head3.click()
      await t.settle(400)
      let openIds = await t.page.locator('.card.open .card-num').allTextContents()
      t.expect(openIds.length === 1 && openIds[0] === '3', `点卡片3后应只展开卡片3, got ${JSON.stringify(openIds)}`)
      await t.page.locator('.card-head').nth(3).click()
      await t.settle(400)
      openIds = await t.page.locator('.card.open .card-num').allTextContents()
      t.expect(openIds.length === 1 && openIds[0] === '4', `点卡片4后应只展开卡片4(互斥), got ${JSON.stringify(openIds)}`)
      await t.page.locator('.card-head').nth(3).click()
      await t.settle(400)
      openIds = await t.page.locator('.card.open').count()
      t.expect(openIds === 0, `再点应收起, 仍展开 ${openIds} 张`)
      await t.shot('cards-toggle')
    }],

    ['S-03', '卡片1 Parallel Demo 运行', async (t) => {
      await t.goto('/showcase', { selector: '.showcase', idleMs: 1000 })
      await runCard(t, 1)
      const out = t.page.locator('.card.open .card-pane').nth(1).locator('pre')
      const txt = await out.textContent().catch(() => '')
      t.expect(txt.length > 10, `卡片1 运行后输出应非空, got: ${txt.slice(0, 80)}`)
      t.expect(!/^ERROR/.test(txt.trim()), `卡片1 输出不应为错误, got: ${txt.slice(0, 120)}`)
      const time = await t.page.locator('.card.open .card-time').textContent().catch(() => '')
      t.expect(/\d+\s*ms/.test(time || ''), `应显示耗时, got: ${time}`)
      await t.shot('card1-parallel', { key: true })
    }],

    ['S-04', '卡片2 BFF User Profile 运行', async (t) => {
      await t.goto('/showcase', { selector: '.showcase', idleMs: 1000 })
      await runCard(t, 2)
      const txt = await t.page.locator('.card.open .card-pane').nth(1).locator('pre')
        .textContent().catch(() => '')
      t.expect(txt.length > 10 && !/^ERROR/.test(txt.trim()),
        `卡片2 输出异常, got: ${txt.slice(0, 120)}`)
      await t.shot('card2-bff', { key: true })
    }],

    ['S-05', '卡片5 Schema Guard 运行', async (t) => {
      await t.goto('/showcase', { selector: '.showcase', idleMs: 1000 })
      await runCard(t, 5)
      const txt = await t.page.locator('.card.open .card-pane').nth(1).locator('pre')
        .textContent().catch(() => '')
      t.expect(txt.length > 5 && !/^ERROR/.test(txt.trim()),
        `卡片5 输出异常, got: ${txt.slice(0, 120)}`)
      await t.shot('card5-schema', { key: true })
    }],

    ['S-06', '卡片6 MCP over HTTP 运行', async (t) => {
      await t.goto('/showcase', { selector: '.showcase', idleMs: 1000 })
      await runCard(t, 6)
      const txt = await t.page.locator('.card.open .card-pane').nth(1).locator('pre')
        .textContent().catch(() => '')
      t.expect(!/^ERROR/.test(txt.trim()), `卡片6 输出不应为错误, got: ${txt.slice(0, 160)}`)
      t.expect(txt.includes('42'), `MCP 渲染 {v:{@v}}/v=42 应输出 42, got: ${txt.slice(0, 200)}`)
      await t.shot('card6-mcp', { key: true })
    }],

    ['S-07', '卡片3/4 无端点提示', async (t) => {
      await t.goto('/showcase', { selector: '.showcase', idleMs: 1000 })
      for (const [idx, id] of [[2, 3], [3, 4]]) {
        await t.page.locator('.card-head').nth(idx).click()
        await t.settle(300)
        await t.page.locator('.card.open .card-run button').click()
        await t.settle(400)
        const txt = await t.page.locator('.card.open .card-pane').nth(1).textContent().catch(() => '')
        t.expect(/无 live|no live|端点|endpoint/i.test(txt || ''),
          `卡片${id} 无端点提示缺失, got: ${(txt || '').slice(0, 80)}`)
      }
      await t.shot('cards-no-endpoint', { key: true })
    }],

    ['S-08', '卡片标签与描述完整', async (t) => {
      await t.goto('/showcase', { selector: '.showcase', idleMs: 1000 })
      const tags = await t.page.locator('.card .el-tag').count()
      t.expect(tags >= 6, `示例标签应 ≥6 个, got ${tags}`)
      for (let i = 0; i < 6; i++) {
        const head = await t.page.locator('.card-head').nth(i).textContent()
        if (!head.trim()) t.fail(`卡片${i + 1} 标题为空`)
      }
    }],
  ],
}))

async function runCard(t, id) {
  const idx = id - 1
  // 卡片 1 默认展开, 只有未展开时才点头部展开
  const openNum = await t.page.locator('.card.open .card-num').textContent().catch(() => null)
  if ((openNum || '').trim() !== String(id)) {
    await t.page.locator('.card-head').nth(idx).click()
    await t.settle(400)
  }
  await t.page.locator('.card.open .card-run button').filter({ hasText: /运行|Run/ }).click()
  // 等待 loading 结束且输出面板出现内容
  await t.page.waitForFunction(
    () => {
      const open = document.querySelector('.card.open')
      if (!open) return false
      const pre = open.querySelectorAll('.card-pane pre')[1]
      return pre && pre.textContent.trim().length > 2
    },
    { timeout: 12000 },
  ).catch(() => {})
  await t.settle(400)
}
