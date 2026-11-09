// BL-10 异常与容错（横切）: 错误模板、非法数据、空模板、超限模板、断网、全站 pageerror 巡检

import { BL, main } from './lib.mjs'

const ALL_PAGES = ['/', '/playground', '/showcase', '/perf', '/mcp', '/learn', '/try', '/docs']

main(new BL({
  id: 'BL-10',
  name: '异常与容错',
  slug: 'bl-10-errors',
  desc: '错误输入、边界输入、网络故障下的 UI 兜底与恢复能力',
  cases: [
    ['E-01', '模板脚本错误 → 错误面板且页面不崩', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 1500 })
      await t.ta(0).fill('{ "bad": "{% return undefinedFn() %}" }')
      await t.waitRenders(0)
      await t.settle(1500)
      t.expect(await t.page.locator('.pg-err-block').count() >= 1, '应有错误面板')
      t.expect(t.pageErrors().length === 0, `不应有未捕获 pageerror: ${JSON.stringify(t.pageErrors().map(e => e.text.slice(0, 80)))}`)
      // 页面仍可交互
      await t.btn(/清空|Clear/).click()
      await t.settle(300)
      t.expect((await t.ta(0).inputValue()) === '', '错误后页面应仍可交互')
      await t.shot('err-template', { key: true })
    }],

    ['E-02', '非法 JSON 数据 → 行内提示不崩溃', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 1500 })
      await t.ta(1).fill('}{ broken')
      await t.settle(700)
      const err = await t.page.locator('.pg-err').textContent().catch(() => '')
      t.expect(/无效|invalid|Expected|Unexpected|JSON/i.test(err), `应有行内错误提示, got: "${err}"`)
      t.expect(await t.page.locator('.pg-err-block').count() === 0, '数据错误不应污染输出错误面板')
      t.expect(t.pageErrors().length === 0, '不应有 pageerror')
      await t.shot('err-bad-data', { key: true })
    }],

    ['E-03', '空模板点击渲染行为', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 1500 })
      await t.btn(/清空|Clear/).click()
      await t.settle(300)
      await t.btn(/渲染|Render/).click()
      await t.settle(1200)
      t.expect(t.pageErrors().length === 0, `空模板渲染不应 pageerror: ${JSON.stringify(t.pageErrors().map(e => e.text.slice(0, 80)))}`)
      const alive = await t.page.locator('.playground').count()
      t.expect(alive === 1, '页面应保持可用')
      await t.shot('err-empty-template', { key: true })
    }],

    ['E-04', '超限模板（>64KB）→ 服务端限制兜底', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 1500 })
      const big = JSON.stringify({ big: 'x'.repeat(70 * 1024) })
      await t.ta(0).fill(big)
      await t.waitRenders(0)
      await t.settle(2000)
      const apiErr = t.apiErrors()
      const errBlock = await t.page.locator('.pg-err-block').count()
      if (apiErr.length === 0 && errBlock === 0) {
        t.fail(`70KB 模板未被拦截(文档限制 65536): 无 4xx、无错误面板。api4xx=${JSON.stringify(apiErr)}`)
      } else {
        t.note(`服务端返回: ${apiErr.map(e => e.status).join(',')} / 错误面板=${errBlock}`)
      }
      t.expect(t.pageErrors().length === 0, '超限模板不应产生 pageerror')
      await t.shot('err-oversize', { key: true })
    }],

    ['E-05', '断网（API 不可达）→ 错误反馈且可恢复', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 1500 })
      await t.page.route('**/api/try**', route => route.abort('failed'))
      await t.ta(0).fill('{ "offline": true }')
      await t.waitRenders(0) // 不会成功, 等待超时兜底
      await t.settle(1500)
      const body = await t.page.locator('.playground').textContent()
      const feedback = /网络|失败|error|Network/i.test(body || '')
        || await t.page.locator('.el-message').count() > 0
        || await t.page.locator('.pg-err-block').count() > 0
      t.expect(feedback, '断网时应有可见错误反馈（消息/错误面板）')
      t.expect(await t.page.locator('.playground').count() === 1, '断网后页面不应崩溃')
      await t.shot('err-offline', { key: true })
      // 恢复网络后可正常渲染
      await t.page.unroute('**/api/try**')
      const before = await t.renders()
      await t.btn(/渲染|Render/).click()
      await t.waitRenders(before)
      const out = await t.outputText()
      t.expect(out.includes('offline'), `恢复网络后渲染应成功, got: ${out.slice(0, 80)}`)
      await t.shot('err-offline-recovered', { key: true })
    }],

    ['E-06', 'Learn 未知 id 容错（错误视角）', async (t) => {
      await t.goto('/learn/nope-404', { waitUntil: 'networkidle', idleMs: 1000 })
      t.expect(await t.page.locator('[data-testid="learn-not-found"]').count() === 1, '应展示 not-found')
      t.expect(t.pageErrors().length === 0, '不应有 pageerror')
      await t.shot('err-learn-404', { key: true })
    }],

    ['E-07', '全站 8 页巡检：零 pageerror / 零异常 4xx', async (t) => {
      const bad = []
      let consumed = t.events().length
      for (const p of ALL_PAGES) {
        await t.goto(p, { waitUntil: 'networkidle', idleMs: 900 })
        if (p === '/playground' || p === '/learn') await t.settle(1500)
        const fresh = t.events().slice(consumed)
        consumed = t.events().length
        for (const pe of fresh.filter(e => e.type === 'pageerror')) bad.push({ page: p, type: 'pageerror', text: pe.text.slice(0, 120) })
        for (const e of fresh.filter(e => e.type === 'api4xx')) bad.push({ page: p, type: 'api4xx', text: e.text })
        for (const rf of fresh.filter(e => e.type === 'requestfailed')) {
          bad.push({ page: p, type: 'requestfailed', text: rf.text.slice(0, 120) })
        }
      }
      t.expect(bad.length === 0, `巡检发现 ${bad.length} 个异常: ${JSON.stringify(bad.slice(0, 6), null, 1)}`)
      await t.shot('sweep-last', { key: true })
    }],
  ],
}))
