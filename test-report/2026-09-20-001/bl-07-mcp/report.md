# BL-07 MCP 集成 — 回归测试报告

> MCP 接入三步引导、Claude 配置复制、tools/list 与 render_template 实测
> 执行时间: 2026-09-20T05:02:28.137Z ~ 2026-09-20T05:02:37.015Z · 环境: http://localhost:5173 (vite dev) + :8088 (Spring Boot) · 1440×900 Chromium

**结论: 4 例, ✅ 4 通过, ❌ 0 失败 (通过率 100%)**

| 用例 | 名称 | 结果 | 耗时 | 证据 |
| --- | --- | --- | --- | --- |
| M-01 | 页面加载与三步引导 | ✅ 通过 | 1490ms | `bl-07-mcp/shots/M-01-01-mcp-page.png` |
| M-02 | 复制 Claude 配置 | ✅ 通过 | 1761ms | `bl-07-mcp/shots/M-02-01-mcp-copy-config.png` |
| M-03 | tools/list 调用 | ✅ 通过 | 1484ms | `bl-07-mcp/shots/M-03-01-mcp-tools-list.png` |
| M-04 | render_template 调用 | ✅ 通过 | 1507ms | `bl-07-mcp/shots/M-04-01-mcp-render-call.png` |

## 证据清单

截图按用例编号存储于本目录 `shots/`；失败用例保留全过程截图与 `events.json`（console/pageerror/网络事件），通过用例仅保留关键截图。