# BL-02 模板演练场 Playground — 回归测试报告

> 核心交互演练场：预设加载、JSON/YAML/String、手动渲染、快捷键、片段、格式化、分享、Trace、错误、持久化、文件加载
> 执行时间: 2026-09-20T08:20:40.077Z ~ 2026-09-20T08:21:52.640Z · 环境: http://localhost:5173 (vite dev) + :8088 (Spring Boot) · 1440×900 Chromium

**结论: 17 例, ✅ 17 通过, ❌ 0 失败 (通过率 100%)**

| 用例 | 名称 | 结果 | 耗时 | 证据 |
| --- | --- | --- | --- | --- |
| P-01 | 加载与默认预设自动渲染 | ✅ 通过 | 3201ms | `bl-02-playground/shots/P-01-01-default-render.png` |
| P-02 | 四个预设逐一加载渲染 | ✅ 通过 | 17631ms | `bl-02-playground/shots/P-02-01-last-preset.png` |
| P-03 | 模板类型 JSON/YAML/String 切换 | ✅ 通过 | 3100ms | `bl-02-playground/shots/P-03-01-type-string.png` |
| P-04 | 手动编辑模板并点击渲染 | ✅ 通过 | 2700ms | `bl-02-playground/shots/P-04-01-manual-render.png` |
| P-05 | Ctrl+Enter 快捷键渲染 | ✅ 通过 | 2425ms | — |
| P-06 | 代码片段插入 | ✅ 通过 | 2540ms | — |
| P-07 | 格式化 JSON 数据 | ✅ 通过 | 2849ms | `bl-02-playground/shots/P-07-01-formatted.png` |
| P-08 | 清空按钮 | ✅ 通过 | 2932ms | — |
| P-09 | 分享链接生成与状态回填 | ✅ 通过 | 4933ms | `bl-02-playground/shots/P-09-01-share-clicked.png` · `bl-02-playground/shots/P-09-02-share-restore.png` |
| P-10 | Trace 开关与事件面板 | ✅ 通过 | 4351ms | `bl-02-playground/shots/P-10-01-trace-events.png` |
| P-11 | Raw 原始输出 Tab | ✅ 通过 | 3409ms | — |
| P-12 | 脚本错误 → 友好错误面板 | ✅ 通过 | 3642ms | `bl-02-playground/shots/P-12-01-render-error.png` |
| P-13 | 数据 JSON 非法提示 | ✅ 通过 | 2732ms | `bl-02-playground/shots/P-13-01-invalid-data.png` |
| P-14 | Ctrl+S 保存与刷新持久化 | ✅ 通过 | 5083ms | `bl-02-playground/shots/P-14-01-persisted-after-reload.png` |
| P-15 | YAML 模板渲染 | ✅ 通过 | 2603ms | `bl-02-playground/shots/P-15-01-yaml-render.png` |
| P-16 | 加载 JSON 文件到数据面板 | ✅ 通过 | 2930ms | `bl-02-playground/shots/P-16-01-file-loaded.png` |
| P-17 | 三栏布局无横向溢出 | ✅ 通过 | 2585ms | — |

## 证据清单

截图按用例编号存储于本目录 `shots/`；失败用例保留全过程截图与 `events.json`（console/pageerror/网络事件），通过用例仅保留关键截图。