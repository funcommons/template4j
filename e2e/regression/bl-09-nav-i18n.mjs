// BL-09 全局导航与国际化: 8 路由、未知路径、中英切换、持久化、i18n 缺 key 巡检

import { BL, main, BASE } from './lib.mjs'

const ROUTES = [
  ['/', '.home'],
  ['/playground', '.playground'],
  ['/showcase', '.showcase'],
  ['/perf', '.perf'],
  ['/mcp', '.mcp'],
  ['/learn', '[data-testid="learn-page"]'],
  ['/try', '.try-page'],
  ['/docs', '.docs'],
]

main(new BL({
  id: 'BL-09',
  name: '全局导航与国际化',
  slug: 'bl-09-nav-i18n',
  desc: '主导航全路由可达、未知路径兜底、中英文切换、语言持久化、i18n 缺失 key 巡检',
  cases: [
    ['N-01', '8 个主路由全部可达', async (t) => {
      const bad = []
      for (const [path, sel] of ROUTES) {
        await t.goto(path, { waitUntil: 'networkidle', idleMs: 700 })
        const ok = await t.page.locator(sel).count()
        if (!ok) { bad.push({ path, sel }); await t.shot(`route-${path.replace(/\W+/g, '_')}`) }
        // 导航菜单始终可见
        if (!await t.page.locator('.nav-menu').count()) bad.push({ path, issue: 'nav missing' })
      }
      t.expect(bad.length === 0, `异常路由: ${JSON.stringify(bad)}`)
      await t.shot('routes-all-last', { key: true })
    }],

    ['N-02', '未知路径兜底（404 页）', async (t) => {
      await t.goto('/no-such-page-xyz', { waitUntil: 'networkidle', idleMs: 900 })
      const nav = await t.page.locator('.nav-menu').count()
      t.expect(nav === 1, '未知路径下导航应仍可见')
      const main = await t.page.locator('.main-content').textContent().catch(() => '')
      const has404 = /404|不存在|not found|未找到/i.test(main || '')
      t.expect(has404, `未知路径应展示 404 提示, 实际内容区为空/无提示: "${(main || '').trim().slice(0, 60)}"`)
      await t.shot('unknown-route', { key: true })
    }],

    ['N-03', '中英切换即时生效', async (t) => {
      await t.goto('/', { selector: '.hero', idleMs: 800 })
      const zhTitle = await t.page.locator('.hero h1').textContent()
      t.expect(/模板/.test(zhTitle), `默认应为中文, got: ${zhTitle}`)
      await t.setLang('en-US')
      const enTitle = await t.page.locator('.hero h1').textContent()
      t.expect(!/模板/.test(enTitle) && /[A-Za-z]/.test(enTitle), `切英文后标题应变化, got: ${enTitle}`)
      await t.shot('lang-en', { key: true })
      await t.setLang('zh-CN')
      const backTitle = await t.page.locator('.hero h1').textContent()
      t.expect(/模板/.test(backTitle), '切回中文应恢复')
    }],

    ['N-04', '语言选择刷新持久化', async (t) => {
      await t.goto('/', { selector: '.hero', idleMs: 800 })
      await t.setLang('en-US')
      await t.page.reload({ waitUntil: 'networkidle' })
      await t.page.waitForSelector('.hero', { timeout: 10000 })
      await t.settle(600)
      const title = await t.page.locator('.hero h1').textContent()
      t.expect(!/模板/.test(title), `刷新后应保持英文, got: ${title}`)
      const stored = await t.page.evaluate(() => localStorage.getItem('locale'))
      t.expect(stored === 'en-US', `localStorage.locale 应为 en-US, got: ${stored}`)
      await t.shot('lang-persist', { key: true })
    }],

    ['N-05', '导航菜单硬编码检查（中文态下菜单语言）', async (t) => {
      await t.goto('/', { selector: '.nav-menu', idleMs: 600 })
      const menu = await t.page.locator('.nav-menu').textContent()
      // 大厂标准: 导航应跟随语言切换。当前实现为硬编码英文菜单项。
      const items = ['Home', 'Playground', 'Showcase', 'Perf', 'MCP', 'Learn', 'Docs']
      const allEn = items.every(x => menu.includes(x))
      if (allEn) {
        t.warn(`导航菜单为硬编码英文, 中文用户无法感知语言切换: "${menu.trim().slice(0, 80)}"`)
      }
      await t.shot('nav-hardcoded', { key: true })
    }],

    ['N-06', 'i18n 健康度巡检（缺 key / 消息内嵌 HTML，全页面 × 双语言）', async (t) => {
      const missing = []
      const htmlInMsg = []
      for (const locale of ['zh-CN', 'en-US']) {
        for (const [path] of ROUTES) {
          await t.goto(path, { waitUntil: 'networkidle', idleMs: 500 })
          if (path === '/playground' || path === '/learn') await t.settle(1200)
          await t.setLang(locale)
          await t.settle(300)
          const warns = t.consoleWarns().filter(x => /\[intlify\]/i.test(x))
          for (const w of warns) {
            if (/Detected HTML in/i.test(w)) {
              if (!htmlInMsg.some(h => h.sample === w)) htmlInMsg.push({ locale, path, sample: w.slice(0, 140) })
            } else if (/not found/i.test(w)) {
              if (!missing.some(h => h.sample === w)) missing.push({ locale, path, sample: w.slice(0, 140) })
            }
          }
        }
      }
      t.note(`缺失 key: ${missing.length} 处; 消息内嵌 HTML 警告: ${htmlInMsg.length} 处`)
      if (missing.length) t.fail(`发现 ${missing.length} 处 i18n 缺失 key: ${JSON.stringify(missing.slice(0, 5))}`)
      if (htmlInMsg.length) t.fail(`发现 ${htmlInMsg.length} 处 i18n 消息内嵌 HTML(vue-i18n XSS 风险告警, 触发自 perf.intro/liveHint/method2、mcp.step2Hint 等 v-html 用法): ${JSON.stringify(htmlInMsg.slice(0, 4), null, 1)}`)
      await t.shot('i18n-sweep', { key: true })
    }],

    ['N-07', 'GitHub 外链可见可用', async (t) => {
      await t.goto('/', { selector: '.nav-menu', idleMs: 800 })
      const gh = t.page.locator('a.nav-gh')
      const count = await gh.count()
      const info = await t.page.evaluate(() => {
        const a = document.querySelector('a[href*="github.com"]')
        if (!a) return { found: false }
        const r = a.getBoundingClientRect()
        return { found: true, w: r.width, h: r.height, visible: r.width > 0 && r.height > 0,
          parent: a.closest('ul')?.className || '' }
      })
      if (!info.found) {
        t.fail('GitHub 外链完全缺失')
      } else if (!info.visible) {
        t.fail(`GitHub 外链存在但不可见(${info.w}x${info.h}), 被吞进隐藏容器: ${info.parent} — App.vue 在 el-menu 内使用原生 <li>,被 Element Plus 解析为空 el-sub-menu,导航栏只显示一个「…」`)
      }
      t.expect(count >= 1, '.nav-gh 选择器未命中(链接不在预期位置)')
      await t.shot('nav-github-corner', { key: true, fullPage: false })
    }],
  ],
}))
