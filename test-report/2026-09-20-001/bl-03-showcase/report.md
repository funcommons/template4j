# BL-03 精选示例 Showcase — 回归测试报告

> 示例卡片展示、展开/收起、live 运行（parallel / schema / mcp）、无端点卡片提示
> 执行时间: 2026-09-20T04:58:34.789Z ~ 2026-09-20T04:58:56.279Z · 环境: http://localhost:5173 (vite dev) + :8088 (Spring Boot) · 1440×900 Chromium

**结论: 8 例, ✅ 8 通过, ❌ 0 失败 (通过率 100%)**

| 用例 | 名称 | 结果 | 耗时 | 证据 |
| --- | --- | --- | --- | --- |
| S-01 | 页面加载与 6 张卡片 | ✅ 通过 | 1744ms | `bl-03-showcase/shots/S-01-01-showcase-default.png` |
| S-02 | 卡片展开/收起与互斥 | ✅ 通过 | 2900ms | — |
| S-03 | 卡片1 Parallel Demo 运行 | ✅ 通过 | 2065ms | `bl-03-showcase/shots/S-03-01-card1-parallel.png` |
| S-04 | 卡片2 BFF User Profile 运行 | ✅ 通过 | 2449ms | `bl-03-showcase/shots/S-04-01-card2-bff.png` |
| S-05 | 卡片5 Schema Guard 运行 | ✅ 通过 | 2481ms | `bl-03-showcase/shots/S-05-01-card5-schema.png` |
| S-06 | 卡片6 MCP over HTTP 运行 | ✅ 通过 | 2484ms | `bl-03-showcase/shots/S-06-01-card6-mcp.png` |
| S-07 | 卡片3/4 无端点提示 | ✅ 通过 | 3116ms | `bl-03-showcase/shots/S-07-01-cards-no-endpoint.png` |
| S-08 | 卡片标签与描述完整 | ✅ 通过 | 1588ms | — |

## 证据清单

截图按用例编号存储于本目录 `shots/`；失败用例保留全过程截图与 `events.json`（console/pageerror/网络事件），通过用例仅保留关键截图。