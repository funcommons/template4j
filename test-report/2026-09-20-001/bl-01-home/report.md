# BL-01 门户与首页 — 回归测试报告

> 官网首页：hero 区、示例演示 tab、CTA、功能卡片、依赖坐标复制、统计数字
> 执行时间: 2026-09-20T04:56:24.706Z ~ 2026-09-20T04:56:45.548Z · 环境: http://localhost:5173 (vite dev) + :8088 (Spring Boot) · 1440×900 Chromium

**结论: 7 例, ✅ 6 通过, ❌ 1 失败 (通过率 86%)**

| 用例 | 名称 | 结果 | 耗时 | 证据 |
| --- | --- | --- | --- | --- |
| H-01 | 首页加载整体渲染 | ✅ 通过 | 2252ms | `bl-01-home/shots/H-01-01-home-full.png` |
| H-02 | Hero 示例 Tab 切换联动 | ✅ 通过 | 4177ms | `bl-01-home/shots/H-02-01-tab-initial.png` · `bl-01-home/shots/H-02-02-tab-switched.png` |
| H-03 | CTA 跳转 Playground | ✅ 通过 | 2768ms | `bl-01-home/shots/H-03-01-cta-playground.png` |
| H-04 | CTA 跳转 Learn | ✅ 通过 | 1634ms | `bl-01-home/shots/H-04-01-cta-learn.png` |
| H-05 | Maven 依赖坐标复制 | ✅ 通过 | 1942ms | `bl-01-home/shots/H-05-01-copy-deps.png` |
| H-06 | 统计数字与 MCP 提示区 | ✅ 通过 | 1465ms | — |
| H-07 | Hero 门面演示开箱可渲染 | ❌ 失败 | 2667ms | `bl-01-home/shots/H-07-01-hero-demo-output.png` · `bl-01-home/shots/H-07-02-zz-final-fullpage.png` · `bl-01-home/shots/H-07-events.json` |

## 失败用例与修复意见

### H-07 Hero 门面演示开箱可渲染

- **现象**: 首页 hero 演示应展示渲染结果, 实际显示兜底文案: "输出(需要 /users /orders mock 端点)" (后端缺 /users /orders mock 端点, /api/try 返回 400)
- **根因**: 后端 site 服务未提供 /users、/orders mock 端点, hero 的 parallel 示例 rest 任务全部失败
- **修复意见**: 在 template4j-site 增加确定性 mock Controller（GET /users/{id}、GET /orders?userId=）；或前端内置 MSW mock。验收: 首页 hero 输出面板直接展示渲染 JSON。

## 证据清单

截图按用例编号存储于本目录 `shots/`；失败用例保留全过程截图与 `events.json`（console/pageerror/网络事件），通过用例仅保留关键截图。