# BL-08 文档中心 Docs — 回归测试报告

> 文档详情展示（markdown section、可运行 json-renderer section、Java 调用代码）与 Try 联动
> 执行时间: 2026-09-20T07:17:20.511Z ~ 2026-09-20T07:17:33.833Z · 环境: http://localhost:5173 (vite dev) + :8088 (Spring Boot) · 1440×900 Chromium

**结论: 4 例, ✅ 4 通过, ❌ 0 失败 (通过率 100%)**

| 用例 | 名称 | 结果 | 耗时 | 证据 |
| --- | --- | --- | --- | --- |
| D-01 | 页面加载 intro-home | ✅ 通过 | 2157ms | `bl-08-docs/shots/D-01-01-docs-load.png` |
| D-02 | Markdown section 内容 | ✅ 通过 | 2131ms | — |
| D-03 | 可运行 section 与 Java 调用代码 | ✅ 通过 | 3935ms | `bl-08-docs/shots/D-03-01-docs-run-section.png` |
| D-04 | Open in Try 联动跳转 | ✅ 通过 | 2749ms | `bl-08-docs/shots/D-04-01-docs-open-in-try.png` |

## 证据清单

截图按用例编号存储于本目录 `shots/`；失败用例保留全过程截图与 `events.json`（console/pageerror/网络事件），通过用例仅保留关键截图。