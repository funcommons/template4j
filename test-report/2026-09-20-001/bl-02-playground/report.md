# BL-02 模板演练场 Playground — 回归测试报告

> 核心交互演练场：预设加载、JSON/YAML/String、手动渲染、快捷键、片段、格式化、分享、Trace、错误、持久化、文件加载
> 执行时间: 2026-09-20T05:26:15.174Z ~ 2026-09-20T05:27:38.120Z · 环境: http://localhost:5173 (vite dev) + :8088 (Spring Boot) · 1440×900 Chromium

**结论: 17 例, ✅ 12 通过, ❌ 5 失败 (通过率 71%)**

| 用例 | 名称 | 结果 | 耗时 | 证据 |
| --- | --- | --- | --- | --- |
| P-01 | 加载与默认预设自动渲染 | ❌ 失败 | 3200ms | `bl-02-playground/shots/P-01-01-default-render.png` · `bl-02-playground/shots/P-01-02-zz-final-fullpage.png` · `bl-02-playground/shots/P-01-events.json` |
| P-02 | 四个预设逐一加载渲染 | ❌ 失败 | 17687ms | `bl-02-playground/shots/P-02-01-last-preset.png` · `bl-02-playground/shots/P-02-02-zz-final-fullpage.png` · `bl-02-playground/shots/P-02-events.json` |
| P-03 | 模板类型 JSON/YAML/String 切换 | ✅ 通过 | 3101ms | `bl-02-playground/shots/P-03-01-type-string.png` |
| P-04 | 手动编辑模板并点击渲染 | ✅ 通过 | 2699ms | `bl-02-playground/shots/P-04-01-manual-render.png` |
| P-05 | Ctrl+Enter 快捷键渲染 | ❌ 失败 | 12428ms | `bl-02-playground/shots/P-05-01-zz-final-fullpage.png` · `bl-02-playground/shots/P-05-events.json` |
| P-06 | 代码片段插入 | ✅ 通过 | 2549ms | — |
| P-07 | 格式化 JSON 数据 | ✅ 通过 | 2866ms | `bl-02-playground/shots/P-07-01-formatted.png` |
| P-08 | 清空按钮 | ✅ 通过 | 2949ms | — |
| P-09 | 分享链接生成与状态回填 | ❌ 失败 | 4950ms | `bl-02-playground/shots/P-09-01-share-clicked.png` · `bl-02-playground/shots/P-09-02-share-restore.png` · `bl-02-playground/shots/P-09-03-zz-final-fullpage.png` · `bl-02-playground/shots/P-09-events.json` |
| P-10 | Trace 开关与事件面板 | ✅ 通过 | 4351ms | `bl-02-playground/shots/P-10-01-trace-events.png` |
| P-11 | Raw 原始输出 Tab | ❌ 失败 | 3407ms | `bl-02-playground/shots/P-11-01-zz-final-fullpage.png` · `bl-02-playground/shots/P-11-events.json` |
| P-12 | 脚本错误 → 友好错误面板 | ✅ 通过 | 3646ms | `bl-02-playground/shots/P-12-01-render-error.png` |
| P-13 | 数据 JSON 非法提示 | ✅ 通过 | 2738ms | `bl-02-playground/shots/P-13-01-invalid-data.png` |
| P-14 | Ctrl+S 保存与刷新持久化 | ✅ 通过 | 5091ms | `bl-02-playground/shots/P-14-01-persisted-after-reload.png` |
| P-15 | YAML 模板渲染 | ✅ 通过 | 2601ms | `bl-02-playground/shots/P-15-01-yaml-render.png` |
| P-16 | 加载 JSON 文件到数据面板 | ✅ 通过 | 2937ms | `bl-02-playground/shots/P-16-01-file-loaded.png` |
| P-17 | 三栏布局无横向溢出 | ✅ 通过 | 2595ms | — |

## 失败用例与修复意见

### P-01 加载与默认预设自动渲染

- **现象**: 结果面板应显示格式化 JSON 树而非转义字符串(疑似双重序列化), got: "{\n  \"msg\": \"Hello, alice!\",\n  \"age\": 30\n}"
- **根因**: JsonRenderer/SSE 返回的 rendered 是 JSON 字符串, 前端 Playground.vue renderStream() 直接 stringify(payload.rendered, null, 2)
- **修复意见**: 前端: `typeof payload.rendered === "string" ? payload.rendered : JSON.stringify(payload.rendered, null, 2)`; 或后端保证 rendered 为对象。同步修复 P-11(Raw tab 同源问题)。

### P-02 四个预设逐一加载渲染

- **现象**: 预设「Parallel mock」渲染出现错误面板: template path:/@class:parallel component render error : template path:/@class:parallel task 'user' failed : template path:/parallel/@class:rest component render
- **现象**: Parallel mock 输出应包含 u-1, got: {"doubled":84,"tier":"normal"}
- **根因**: 预设引用的 /api/user、/api/orders mock 端点在后端不存在（curl 实测 500）
- **修复意见**: 补 mock 端点；预设加载失败时同步给出「该预设需要 mock 端点」的引导提示, 而不是让用户面对原始组件错误堆栈。

### P-05 Ctrl+Enter 快捷键渲染

- **现象**: Ctrl+Enter 应触发渲染 (2 → 2)
- **根因**: @keydown.ctrl.enter 绑定在 style="display:none" 的 fallback textarea(templateRef)上, 可见的 CodeEditor textarea 不冒泡到它
- **修复意见**: 把快捷键监听移入 CodeEditor 组件内部 textarea, 或改用 window keydown 监听(编辑器聚焦时)。

### P-09 分享链接生成与状态回填

- **现象**: 分享链接应以 http://localhost:5173/playground?state= 开头, got: http://localhost:5173/playgroundplayground?state=eyJ0Ijoie1xuICBcInNoYXJlXCI6IFwibWFya2VyLTkxOVwiXG59IiwiZCI6InsgXCJuYW1lXCI6IFwiYWxpY2VcIiwgXCJhZ2VcIjogMzAgfSIsInkiOiJqc29uIn0=
- **根因**: copyShareUrl() 用 `location.pathname + "playground"` 拼接, 在 /playground 页 pathname 已含该路径
- **修复意见**: 使用 `router.resolve({ path: "/playground", query: { state } }).href`; 同时在复制成功 toast 中附可点击链接。

### P-11 Raw 原始输出 Tab

- **现象**: Raw 输出应为单行 JSON, got: "{\n  \"msg\": \"Hello, alice!\",\n  \"age\": 30\n}"
- **根因**: 与 P-01 同源: rawOutput = JSON.stringify(rendered), 而 rendered 已是字符串
- **修复意见**: 同 P-01 一并修复。

## 执行备注

- [P-09] 实际生成: http://localhost:5173/playgroundplayground?state=eyJ0Ijoie1xuICBcInNoYXJlXCI6IFwibWFya2VyLTkxOVwiXG59IiwiZCI6InsgXCJuYW1lXCI6IFwiYWxpY2VcIiwgXCJhZ2VcIjogMzAgfSIsInkiOiJqc29uIn0=

## 证据清单

截图按用例编号存储于本目录 `shots/`；失败用例保留全过程截图与 `events.json`（console/pageerror/网络事件），通过用例仅保留关键截图。