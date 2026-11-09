# BL-09 全局导航与国际化 — 回归测试报告

> 主导航全路由可达、未知路径兜底、中英文切换、语言持久化、i18n 缺失 key 巡检
> 执行时间: 2026-09-20T05:05:25.552Z ~ 2026-09-20T05:06:20.760Z · 环境: http://localhost:5173 (vite dev) + :8088 (Spring Boot) · 1440×900 Chromium

**结论: 7 例, ✅ 4 通过, ❌ 3 失败 (通过率 57%)**

| 用例 | 名称 | 结果 | 耗时 | 证据 |
| --- | --- | --- | --- | --- |
| N-01 | 8 个主路由全部可达 | ✅ 通过 | 10292ms | `bl-09-nav-i18n/shots/N-01-01-routes-all-last.png` |
| N-02 | 未知路径兜底（404 页） | ❌ 失败 | 1536ms | `bl-09-nav-i18n/shots/N-02-01-unknown-route.png` · `bl-09-nav-i18n/shots/N-02-02-zz-final-fullpage.png` · `bl-09-nav-i18n/shots/N-02-events.json` |
| N-03 | 中英切换即时生效 | ✅ 通过 | 4897ms | `bl-09-nav-i18n/shots/N-03-01-lang-en.png` |
| N-04 | 语言选择刷新持久化 | ✅ 通过 | 4228ms | `bl-09-nav-i18n/shots/N-04-01-lang-persist.png` |
| N-05 | 导航菜单硬编码检查（中文态下菜单语言） | ✅ 通过 | 1258ms | `bl-09-nav-i18n/shots/N-05-01-nav-hardcoded.png` |
| N-06 | i18n 健康度巡检（缺 key / 消息内嵌 HTML，全页面 × 双语言） | ❌ 失败 | 28226ms | `bl-09-nav-i18n/shots/N-06-01-i18n-sweep.png` · `bl-09-nav-i18n/shots/N-06-02-zz-final-fullpage.png` · `bl-09-nav-i18n/shots/N-06-events.json` |
| N-07 | GitHub 外链可见可用 | ❌ 失败 | 1520ms | `bl-09-nav-i18n/shots/N-07-01-nav-github-corner.png` · `bl-09-nav-i18n/shots/N-07-02-zz-final-fullpage.png` · `bl-09-nav-i18n/shots/N-07-events.json` |

## 失败用例与修复意见

### N-02 未知路径兜底（404 页）

- **现象**: 未知路径应展示 404 提示, 实际内容区为空/无提示: ""
- **根因**: vue-router 未配置 catch-all 路由
- **修复意见**: 增加 `{ path: "/:pathMatch(.*)*", component: NotFound }`, 页面含「返回首页」与各入口快捷链接。

### N-06 i18n 健康度巡检（缺 key / 消息内嵌 HTML，全页面 × 双语言）

- **现象**: 发现 29 处 i18n 消息内嵌 HTML(vue-i18n XSS 风险告警, 触发自 perf.intro/liveHint/method2、mcp.step2Hint 等 v-html 用法): [
 {
  "locale": "zh-CN",
  "path": "/perf",
  "sample": "[intlify] Detected HTML in '调 <code>/api/demo/compile</code> 跑 5000 次渲染对比。' message. Recommend not using HTML messages to avoid XSS."
 },
 {
  "locale": "zh-CN",
  "path": "/perf",
  "sample": "[intlify] Detected HTML in '简单模板:<code>\"Hello, `name`!\"</code>' message. Recommend not using HTML messages to avoid XSS."
 },
 {
  "locale": "zh-CN",
  "path": "/mcp",
  "sample": "[intlify] Detected HTML in '保存到 <code>~/Library/Application Support/Claude/claude_desktop_config.json</code>:' message. Recommend not using "
 },
 {
  "locale": "zh-CN",
  "path": "/mcp",
  "sample": "[intlify] Detected HTML in '这里走 demo 后端 <code>/api/demo/mcp</code>,模拟 stdio 流:' message. Recommend not using HTML messages to avoid XSS."
 }
]
- **根因**: 翻译文案中写死 <code> 等 HTML 标签, 视图层用 v-html 渲染; 成熟平台做法是文案纯文本 + 组件插槽承载结构
- **修复意见**: 拆分文案与结构: 用 <i18n-t> 组件或 slot 插入代码片段; 短期可在构建期校验 message 不含标签; CI 把 intlify warning 升级为 error。

### N-07 GitHub 外链可见可用

- **现象**: GitHub 外链存在但不可见(0x0), 被吞进隐藏容器: el-menu el-menu--popup el-menu--popup-bottom-start — App.vue 在 el-menu 内使用原生 <li>,被 Element Plus 解析为空 el-sub-menu,导航栏只显示一个「…」
- **现象**: .nav-gh 选择器未命中(链接不在预期位置)
- **根因**: App.vue 在 el-menu 内写原生 <li class="nav-gh">, Element Plus el-menu 将其解析为 sub-menu, 链接进入隐藏 popup
- **修复意见**: 外链移出 el-menu: header 布局改为 flex, 右侧 action 区放置语言/主题/GitHub icon-button。

## 观察项（不计失败）

- [N-05] 导航菜单为硬编码英文, 中文用户无法感知语言切换: "HomePlaygroundShowcasePerfMCPLearnDocs 🌐 中文"

## 执行备注

- [N-06] 缺失 key: 0 处; 消息内嵌 HTML 警告: 29 处

## 证据清单

截图按用例编号存储于本目录 `shots/`；失败用例保留全过程截图与 `events.json`（console/pageerror/网络事件），通过用例仅保留关键截图。