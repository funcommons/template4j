// BL-02 模板演练场 Playground（核心业务线）: 预设、三种类型、渲染、格式化、
// 清空、分享链接、片段插入、快捷键、Trace、错误展示、持久化、文件加载、布局

import { resolve } from 'node:path'
import { writeFileSync, mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { BL, main, BASE, labelRegex } from './lib.mjs'

const PRESETS = ['Basic placeholder', 'if / each', 'Script {=...}', 'Parallel mock']

main(new BL({
  id: 'BL-02',
  name: '模板演练场 Playground',
  slug: 'bl-02-playground',
  desc: '核心交互演练场：预设加载、JSON/YAML/String、手动渲染、快捷键、片段、格式化、分享、Trace、错误、持久化、文件加载',
  cases: [
    ['P-01', '加载与默认预设自动渲染', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 2500 })
      const status = await t.page.locator('.pg-status').textContent()
      t.expect(/渲染次数|Renders/.test(status), `状态栏缺渲染计数, got: ${status.trim().slice(0, 60)}`)
      const renders = await t.renders()
      t.expect(renders >= 1, `默认预设应自动渲染 ≥1 次, got ${renders}`)
      const out = await t.outputText()
      t.expect(out.includes('alice'), `默认预设输出应包含 alice, got: ${out.slice(0, 80)}`)
      t.expect(!out.includes('\\"'), `结果面板应显示格式化 JSON 树而非转义字符串(疑似双重序列化), got: ${out.slice(0, 100)}`)
      await t.shot('default-render', { key: true })
    }],

    ['P-02', '四个预设逐一加载渲染', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 2000 })
      const sel = '.pg-header .el-select'
      let before = await t.renders()
      for (const name of PRESETS) {
        await t.selectOption(sel, name)
        before = await t.waitRenders(before)
        await t.settle(600)
        const err = await t.page.locator('.pg-err-block').count()
        if (err) {
          const msg = await t.page.locator('.pg-err-body').textContent().catch(() => '')
          t.fail(`预设「${name}」渲染出现错误面板: ${msg.slice(0, 160)}`)
        }
        const out = await t.outputText()
        if (!out.trim() || out.includes('Press ▶')) t.fail(`预设「${name}」输出为空`)
        // Parallel mock 走 rest 组件 mock 端点, 输出应含 userId
        if (name === 'Parallel mock' && !out.includes('u-1')) {
          t.fail(`Parallel mock 输出应包含 u-1, got: ${out.slice(0, 100)}`)
        }
      }
      await t.shot('last-preset', { key: true })
    }],

    ['P-03', '模板类型 JSON/YAML/String 切换', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 1500 })
      for (const tp of ['JSON', 'YAML', 'String']) {
        await t.page.locator('.pg-header .el-radio-button').filter({ hasText: tp }).click()
        await t.settle(300)
        const checked = await t.page.locator('.pg-header .el-radio-button.is-active').textContent()
        t.expect(checked.trim() === tp, `切换到 ${tp} 后激活态不符, got: ${checked}`)
      }
      // String 类型下格式化按钮应禁用
      const disabled = await t.btn(/格式化|Format/).evaluate(el => el.classList.contains('is-disabled'))
      t.expect(disabled, 'String 类型下「格式化 JSON」应禁用')
      await t.shot('type-string', { key: true })
    }],

    ['P-04', '手动编辑模板并点击渲染', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 1500 })
      const before = await t.renders()
      await t.ta(0).fill('{\n  "answer": "{= 6 * 7 }"\n}')
      await t.ta(1).fill('{ "x": 1 }')
      await t.settle(500) // watch debounce 600ms 内点按钮, 验证手动渲染
      await t.btn(/渲染|Render/).click()
      await t.waitRenders(before)
      const out = await t.outputText()
      t.expect(out.includes('42'), `输出应包含 42, got: ${out.slice(0, 80)}`)
      await t.shot('manual-render', { key: true })
    }],

    ['P-05', 'Ctrl+Enter 快捷键渲染', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 1500 })
      const before = await t.renders()
      await t.ta(0).click()
      await t.ta(0).press('Control+Enter')
      await t.settle(300)
      await t.waitRenders(before)
      const after = await t.renders()
      t.expect(after > before, `Ctrl+Enter 应触发渲染 (${before} → ${after})`)
    }],

    ['P-06', '代码片段插入', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 1500 })
      const tplBefore = await t.ta(0).inputValue()
      await t.btn(labelRegex('{= expr}', '{= expr}'), '.pg-snippets').click()
      await t.settle(400)
      const tplAfter = await t.ta(0).inputValue()
      t.expect(tplAfter.includes('@a + @b'), `模板应包含插入的片段, got: ${tplAfter.slice(0, 80)}`)
      t.expect(tplAfter.length > tplBefore.length, '片段应插入而非替换')
      await t.shot('snippet-inserted')
    }],

    ['P-07', '格式化 JSON 数据', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 1500 })
      await t.ta(1).fill('{"b":1,"a":[1,2]}')
      await t.settle(300)
      await t.btn(/格式化|Format/).click()
      await t.settle(400)
      const val = await t.ta(1).inputValue()
      t.expect(val.includes('\n  "a"'), `数据应被格式化为多行, got: ${val.slice(0, 60)}`)
      await t.shot('formatted', { key: true })
    }],

    ['P-08', '清空按钮', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 2000 })
      await t.btn(/清空|Clear/).click()
      await t.settle(300)
      const tpl = await t.ta(0).inputValue()
      const out = await t.outputText()
      t.expect(tpl.trim() === '', `清空后模板应为空, got: ${tpl.slice(0, 40)}`)
      t.expect(/Press|渲染/.test(out), `清空后输出应恢复占位文案, got: ${out.slice(0, 40)}`)
      await t.shot('cleared')
    }],

    ['P-09', '分享链接生成与状态回填', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 1500 })
      // 自定义模板便于识别
      await t.ta(0).fill('{\n  "share": "marker-919"\n}')
      await t.settle(300)
      await t.btn(/分享|Share/).click()
      await t.settle(400)
      const copied = await t.page.evaluate(() => window.__copied || [])
      t.expect(copied.length > 0, '分享链接应写入剪贴板')
      const url = copied[copied.length - 1] || ''
      const expected = `${BASE}/playground?state=`
      if (!url.startsWith(expected)) {
        t.fail(`分享链接应以 ${expected} 开头, got: ${url}`, )
        t.note(`实际生成: ${url}`)
      }
      await t.shot('share-clicked', { key: true })
      // 状态回填：用正确的 base 手工拼 URL 访问（分离「复制错链」与「回填」两个问题）
      const m = url.match(/state=([^&]+)/)
      if (m) {
        await t.goto(`/playground?state=${m[1]}`, { selector: '.playground', idleMs: 1500 })
        const tpl = await t.ta(0).inputValue()
        t.expect(tpl.includes('marker-919'), `通过 state 参数应回填模板, got: ${tpl.slice(0, 60)}`)
        await t.shot('share-restore', { key: true })
      }
    }],

    ['P-10', 'Trace 开关与事件面板', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 1500 })
      await t.btn(/Trace: OFF|追踪: OFF|Trace/).click()
      await t.settle(1000)
      await t.waitRenders(0)
      await t.settle(1200)
      const traceBtnTxt = await t.page.locator('.pg-header button', { hasText: /Trace|追踪/ }).textContent()
      t.expect(/ON/.test(traceBtnTxt), `Trace 按钮应变为 ON, got: ${traceBtnTxt}`)
      const rows = await t.page.locator('.pg-trace-row').count()
      t.expect(rows > 0, `Trace 事件应 ≥1 行, got ${rows}`)
      await t.shot('trace-events', { key: true })
    }],

    ['P-11', 'Raw 原始输出 Tab', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 2500 })
      await t.page.locator('.pg-pane').nth(2).locator('.el-radio-button').filter({ hasText: /原始|Raw/ }).click()
      await t.settle(300)
      const raw = await t.page.locator('.pg-pane').nth(2).locator('.pg-out:visible').textContent()
      // 「原始」= 后端返回什么就显示什么: 核心是不可出现双重序列化(带引号包裹与转义)
      t.expect(!raw.trim().startsWith('"'), `Raw 不应以引号开头(双重序列化), got: ${raw.slice(0, 60)}`)
      t.expect(!raw.includes('\\"'), `Raw 不应包含 \\" 转义(双重序列化), got: ${raw.slice(0, 80)}`)
      t.expect(raw.trim().length > 0, 'Raw 输出为空')
    }],

    ['P-12', '脚本错误 → 友好错误面板', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 1500 })
      await t.ta(0).fill('{\n  "bad": "{= this is +++ not valid groovy }"\n}')
      await t.waitRenders(0)
      await t.settle(1500)
      const errBlock = t.page.locator('.pg-err-block')
      t.expect(await errBlock.count() > 0, '渲染错误应展示错误面板 .pg-err-block')
      const title = await errBlock.locator('.pg-err-title').textContent().catch(() => '')
      t.expect(/渲染错误|Error/i.test(title), `错误面板标题异常, got: ${title}`)
      await t.shot('render-error', { key: true })
    }],

    ['P-13', '数据 JSON 非法提示', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 1500 })
      await t.ta(1).fill('{invalid json')
      await t.settle(600)
      const err = await t.page.locator('.pg-err').textContent().catch(() => '')
      t.expect(/无效|invalid|Expected|JSON/i.test(err), `应展示 JSON 无效提示, got: ${err}`)
      const metaCls = await t.page.locator('.pg-pane').nth(1).locator('.pg-pane-meta').getAttribute('class')
      t.expect(/pg-pane-meta-err/.test(metaCls || ''), '数据面板元信息应标红')
      await t.shot('invalid-data', { key: true })
    }],

    ['P-14', 'Ctrl+S 保存与刷新持久化', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 1500 })
      await t.ta(0).fill('{\n  "persist": "marker-p14"\n}')
      await t.settle(500)
      await t.ta(0).press('Control+s')
      await t.settle(400)
      await t.page.reload({ waitUntil: 'networkidle' })
      await t.page.waitForSelector('.playground', { timeout: 10000 })
      await t.settle(1500)
      const tpl = await t.ta(0).inputValue()
      t.expect(tpl.includes('marker-p14'), `刷新后模板应从 localStorage 恢复, got: ${tpl.slice(0, 60)}`)
      await t.shot('persisted-after-reload', { key: true })
    }],

    ['P-15', 'YAML 模板渲染', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 1500 })
      await t.page.locator('.pg-header .el-radio-button').filter({ hasText: 'YAML' }).click()
      const before = await t.renders()
      await t.ta(0).fill('msg: "Hello {@name}"\ncount: "{= @n + 1 }"')
      await t.ta(1).fill('{ "name": "yaml-user", "n": 9 }')
      await t.settle(400)
      await t.btn(/渲染|Render/).click()
      await t.waitRenders(before)
      const out = await t.outputText()
      t.expect(out.includes('yaml-user'), `YAML 渲染输出应包含 yaml-user, got: ${out.slice(0, 100)}`)
      t.expect(out.includes('10'), 'YAML 脚本表达式应计算出 10')
      await t.shot('yaml-render', { key: true })
    }],

    ['P-16', '加载 JSON 文件到数据面板', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 1500 })
      const dir = mkdtempSync(resolve(tmpdir(), 't4j-p16-'))
      const file = resolve(dir, 'data.json')
      writeFileSync(file, JSON.stringify({ fromFile: 'file-p16', nested: { ok: true } }))
      await t.page.locator('.pg-pane').nth(1).locator('input[type=file]')
        .setInputFiles(file)
      await t.settle(800)
      const val = await t.ta(1).inputValue()
      t.expect(val.includes('file-p16'), `文件内容应载入数据面板, got: ${val.slice(0, 60)}`)
      await t.shot('file-loaded', { key: true })
    }],

    ['P-17', '三栏布局无横向溢出', async (t) => {
      await t.goto('/playground', { selector: '.playground', idleMs: 2000 })
      const panes = await t.page.locator('.pg-pane').count()
      t.expect(panes === 3, `应有三栏面板, got ${panes}`)
      const m = await t.page.evaluate(() => ({
        docW: document.documentElement.scrollWidth,
        viewW: window.innerWidth,
      }))
      t.expect(m.docW <= m.viewW + 4, `出现横向滚动 (${m.docW} > ${m.viewW})`)
    }],
  ],
}))
