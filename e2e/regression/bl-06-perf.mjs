// BL-06 性能基准 Perf: 静态图表、Live benchmark、数值一致性、i18n

import { BL, main } from './lib.mjs'

main(new BL({
  id: 'BL-06',
  name: '性能基准 Perf',
  slug: 'bl-06-perf',
  desc: '基准对比条形图、图例、live benchmark 实测、方法论说明',
  cases: [
    ['F-01', '页面加载与静态基准图', async (t) => {
      await t.goto('/perf', { selector: '.perf', idleMs: 800 })
      const rows = await t.page.locator('.chart .row').count()
      t.expect(rows === 4, `应有 4 组基准, got ${rows}`)
      const bars = await t.page.locator('.chart .bar').count()
      t.expect(bars === 8, `每组新旧两根条, 应 8 根, got ${bars}`)
      const legend = await t.page.locator('.legend').textContent()
      t.expect(/0\.4\.0/.test(legend) && /0\.5\.0/.test(legend), `图例应含 0.4.0/0.5.0, got: ${legend.slice(0, 60)}`)
      await t.shot('perf-static', { key: true })
    }],

    ['F-02', 'Live benchmark 运行与提速可信度', async (t) => {
      await t.goto('/perf', { selector: '.perf', idleMs: 800 })
      await t.btn(/跑 benchmark|Run Live|benchmark/i).click()
      await t.page
        .waitForFunction(() => (document.querySelector('.live-result')?.textContent || '').trim().length > 10, { timeout: 180000 })
        .catch(() => {})
      const txt = await t.page.locator('.live-result').textContent().catch(() => '')
      t.expect(txt.length > 0, 'live benchmark 无结果输出')
      t.expect(/\d+/.test(txt), `结果应含数字, got: ${txt.slice(0, 120)}`)
      const speedup = await t.page.locator('.live .speedup').textContent().catch(() => '')
      t.expect(/[\d.]+/.test(speedup || ''), `应显示提速倍数, got: ${speedup}`)
      const num = parseFloat((speedup || '0').replace(/[^\d.]/g, ''))
      const rows = await t.page.locator('.live-result .live-row').allTextContents()
      t.note(`live 结果: ${JSON.stringify(rows)}`)
      if (num < 1) {
        t.fail(`live benchmark 提速 ${speedup} (<1x, 编译渲染反而更慢), 与本页宣称的 5.7x 静态结论矛盾 — 可信度缺陷。明细: ${JSON.stringify(rows)}`)
      }
      await t.shot('perf-live', { key: true })
    }],

    ['F-03', '条形宽度与数值单调一致', async (t) => {
      await t.goto('/perf', { selector: '.perf', idleMs: 800 })
      const data = await t.page.evaluate(() => {
        return [...document.querySelectorAll('.chart .row')].map(row => ({
          name: row.querySelector('.row-name')?.textContent.trim(),
          bars: [...row.querySelectorAll('.bar')].map(b => ({
            w: parseFloat(b.style.width) || 0,
            txt: b.textContent.trim().slice(0, 30),
          })),
        }))
      })
      for (const row of data) {
        const [old_, new_] = row.bars
        // old(0.4.0) 应不快于 new(0.5.0) → old 宽度 ≥ new
        if (old_ && new_ && old_.w < new_.w) {
          t.fail(`「${row.name}」旧版条宽 < 新版 (${old_.w} < ${new_.w}), 与提速预期矛盾`)
        }
      }
      await t.shot('perf-bars')
    }],

    ['F-04', '方法论与英文文案', async (t) => {
      await t.goto('/perf', { selector: '.perf', idleMs: 800 })
      const li = await t.page.locator('.methodology li').count()
      t.expect(li >= 3, `方法论应 ≥3 条, got ${li}`)
      await t.setLang('en-US')
      const legend = await t.page.locator('.legend').textContent()
      t.expect(/before|after/i.test(legend), `英文图例缺失, got: ${legend.slice(0, 60)}`)
      await t.shot('perf-en', { key: true })
    }],
  ],
}))
