// Generate per-BL report.md from result.json, with curated fix suggestions.

import { readFileSync, writeFileSync, existsSync, readdirSync } from 'node:fs'
import { resolve, dirname, basename } from 'node:path'
import { fileURLToPath } from 'node:url'

const SUITE = dirname(fileURLToPath(import.meta.url))
const REPORT_DIR = resolve(SUITE, '../../test-report', process.env.T4J_REPORT_SEQ || '2026-09-20-001')

// caseId -> { defect, cause, fix } 修复意见
const FIX = {
  'H-07': {
    defect: '首页 Hero 演示调 /api/try 返回 400, 输出面板降级为"(需要 /users /orders mock 端点)"',
    cause: '后端 site 服务未提供 /users、/orders mock 端点, hero 的 parallel 示例 rest 任务全部失败',
    fix: '在 template4j-site 增加确定性 mock Controller（GET /users/{id}、GET /orders?userId=）；或前端内置 MSW mock。验收: 首页 hero 输出面板直接展示渲染 JSON。',
  },
  'P-01': {
    defect: '「结果」面板把 SSE done 事件的 rendered 字符串再次 JSON.stringify, 展示为转义字符串',
    cause: 'JsonRenderer/SSE 返回的 rendered 是 JSON 字符串, 前端 Playground.vue renderStream() 直接 stringify(payload.rendered, null, 2)',
    fix: '前端: `typeof payload.rendered === "string" ? payload.rendered : JSON.stringify(payload.rendered, null, 2)`; 或后端保证 rendered 为对象。同步修复 P-11(Raw tab 同源问题)。',
  },
  'P-02': {
    defect: '预设「Parallel mock」渲染报错: rest 组件调 /api/user 失败(500 No static resource api/user)',
    cause: '预设引用的 /api/user、/api/orders mock 端点在后端不存在（curl 实测 500）',
    fix: '补 mock 端点；预设加载失败时同步给出「该预设需要 mock 端点」的引导提示, 而不是让用户面对原始组件错误堆栈。',
  },
  'P-05': {
    defect: 'Ctrl+Enter 快捷键不触发渲染（状态栏明示该快捷键）',
    cause: '@keydown.ctrl.enter 绑定在 style="display:none" 的 fallback textarea(templateRef)上, 可见的 CodeEditor textarea 不冒泡到它',
    fix: '把快捷键监听移入 CodeEditor 组件内部 textarea, 或改用 window keydown 监听(编辑器聚焦时)。',
  },
  'P-09': {
    defect: '分享链接生成 `/playgroundplayground?state=...`, 打开 404',
    cause: 'copyShareUrl() 用 `location.pathname + "playground"` 拼接, 在 /playground 页 pathname 已含该路径',
    fix: '使用 `router.resolve({ path: "/playground", query: { state } }).href`; 同时在复制成功 toast 中附可点击链接。',
  },
  'P-11': {
    defect: '「原始」tab 显示双重序列化的转义字符串, 而非单行 JSON',
    cause: '与 P-01 同源: rawOutput = JSON.stringify(rendered), 而 rendered 已是字符串',
    fix: '同 P-01 一并修复。',
  },
  'T-06': {
    defect: '「清空」把空状态写入历史(setResult(null) unshift), 历史首条变 null 条目; 点击"恢复"得到空模板',
    cause: 'Try.vue clear() 调 store.setResult(null,0,[]), 而 setResult 无条件写历史',
    fix: 'clear() 不调用 setResult（只清 result/template/data）; 或历史写入与结果展示分离, rendered==null 不入历史。',
  },
  'F-02': {
    defect: 'Playground 使用 Trace 后, live benchmark 提速倒挂为 0.71x（干净后端为 4.08x）, 且后端随使用持续劣化直至接口挂起',
    cause: '后端资源泄漏: StreamController 的 SSE trace 订阅者在连接结束后未从共享 RenderEngine 反注册, 后续任意请求的 trace 事件继续向已断开的 SSE 连接写入（jstack: exec 线程卡在 StreamController.lambda$stream$0 → HashMap.putVal, 单线程累计 CPU 235s）。证据: F-02-jstack-evidence.txt',
    fix: '核心修复: SSE 会话结束(onComplete/onError/onTimeout)时反注册 trace 消费者, 或 trace 消费者挂到请求级 Context 而非共享引擎。防御: /api/demo/compile 前重置引擎; 前端对 speedup<1 显示醒目警告。回归: 先访问 Playground 开 Trace, 再跑 benchmark, 断言 speedup ≥ 基线的 50%。',
  },
  'N-02': {
    defect: '未知路径白屏（无 404 页）',
    cause: 'vue-router 未配置 catch-all 路由',
    fix: '增加 `{ path: "/:pathMatch(.*)*", component: NotFound }`, 页面含「返回首页」与各入口快捷链接。',
  },
  'N-06': {
    defect: 'i18n 健康度: 13 处消息内嵌 HTML 触发 vue-i18n "Detected HTML" XSS 风险告警（perf.intro/liveHint/method2、mcp.step2Hint 等配 v-html 渲染）',
    cause: '翻译文案中写死 <code> 等 HTML 标签, 视图层用 v-html 渲染; 成熟平台做法是文案纯文本 + 组件插槽承载结构',
    fix: '拆分文案与结构: 用 <i18n-t> 组件或 slot 插入代码片段; 短期可在构建期校验 message 不含标签; CI 把 intlify warning 升级为 error。',
  },
  'N-07': {
    defect: 'GitHub 外链不可见(0×0), 被吞进隐藏 el-sub-menu, 导航栏出现「…」空菜单',
    cause: 'App.vue 在 el-menu 内写原生 <li class="nav-gh">, Element Plus el-menu 将其解析为 sub-menu, 链接进入隐藏 popup',
    fix: '外链移出 el-menu: header 布局改为 flex, 右侧 action 区放置语言/主题/GitHub icon-button。',
  },
  'E-04': {
    defect: '70KB 模板未触发文档声明的 65536 上限, 服务端照常渲染',
    cause: '/api/try、/api/try/stream 未执行 max-template-size 校验（application.yml 已配置 template4j.demo.max-template-size: 65536）',
    fix: '在 TryController/StreamController 入口校验 template 字节数, 超限返回 400 + 业务码; 前端编辑器字数统计处提前预警。',
  },
  'E-05': {
    defect: '(若失败) 断网容错链路问题',
    cause: '见 result.json',
    fix: '保证请求失败路径有统一 ElMessage 反馈, 且恢复后无需刷新即可继续渲染。',
  },
  'E-07': {
    defect: '全站巡检发现异常事件',
    cause: '多为首页 hero 演示 400（同 H-07）; 明细见 events',
    fix: '修复 mock 端点后归零; requestfailed 需逐条排查。',
  },
}

const STATUS = { pass: '✅ 通过', fail: '❌ 失败' }

function genBL(slug) {
  const dir = resolve(REPORT_DIR, slug)
  const resultPath = resolve(dir, 'result.json')
  if (!existsSync(resultPath)) return null
  const r = JSON.parse(readFileSync(resultPath, 'utf8'))
  const lines = []
  lines.push(`# ${r.bl} ${r.name} — 回归测试报告`)
  lines.push('')
  lines.push(`> ${r.desc}`)
  lines.push(`> 执行时间: ${r.startedAt} ~ ${r.finishedAt} · 环境: http://localhost:5173 (vite dev) + :8088 (Spring Boot) · 1440×900 Chromium`)
  lines.push('')
  const s = r.summary
  const rate = s.total ? Math.round(((s.total - s.fail) / s.total) * 100) : 0
  lines.push(`**结论: ${s.total} 例, ✅ ${s.pass} 通过, ❌ ${s.fail} 失败 (通过率 ${rate}%)**`)
  lines.push('')
  lines.push('| 用例 | 名称 | 结果 | 耗时 | 证据 |')
  lines.push('| --- | --- | --- | --- | --- |')
  for (const c of r.cases) {
    const ev = (c.evidence || []).map(e => `\`${slug}/shots/${e}\``).join(' · ')
    lines.push(`| ${c.id} | ${c.name} | ${STATUS[c.status]} | ${c.durationMs}ms | ${ev || '—'} |`)
  }
  lines.push('')
  const fails = r.cases.filter(c => c.status === 'fail')
  if (fails.length) {
    lines.push(`## 失败用例与修复意见`)
    lines.push('')
    for (const c of fails) {
      lines.push(`### ${c.id} ${c.name}`)
      lines.push('')
      for (const i of c.issues.filter(x => x.level === 'fail')) lines.push(`- **现象**: ${i.msg}`)
      const f = FIX[c.id]
      if (f) {
        lines.push(`- **根因**: ${f.cause}`)
        lines.push(`- **修复意见**: ${f.fix}`)
      } else {
        for (const i of c.issues.filter(x => x.level === 'fail')) {
          if (i.stack) lines.push(`- **堆栈**: ${i.stack}`)
        }
        lines.push(`- **修复意见**: 结合证据截图与 events.json 定位（无预置根因）。`)
      }
      lines.push('')
    }
  }
  const warns = r.cases.flatMap(c => (c.issues || []).filter(i => i.level === 'warn').map(i => ({ c, i })))
  if (warns.length) {
    lines.push(`## 观察项（不计失败）`)
    lines.push('')
    for (const w of warns) lines.push(`- [${w.c.id}] ${w.i.msg}`)
    lines.push('')
  }
  const infos = r.cases.flatMap(c => (c.issues || []).filter(i => i.level === 'info').map(i => ({ c, i })))
  if (infos.length) {
    lines.push(`## 执行备注`)
    lines.push('')
    for (const w of infos) lines.push(`- [${w.c.id}] ${w.i.msg}`)
    lines.push('')
  }
  lines.push(`## 证据清单`)
  lines.push('')
  lines.push('截图按用例编号存储于本目录 `shots/`；失败用例保留全过程截图与 `events.json`（console/pageerror/网络事件），通过用例仅保留关键截图。')
  writeFileSync(resolve(dir, 'report.md'), lines.join('\n'))
  return { slug, r }
}

const slugs = readdirSync(REPORT_DIR).filter(d => d.startsWith('bl-'))
const out = []
for (const s of slugs) {
  const res = genBL(s)
  if (res) out.push(res)
}
for (const { r } of out) {
  console.log(`${r.bl} ${r.name}: ${r.summary.pass}/${r.summary.total} pass`)
}
