# BL-06 性能基准 Perf — 回归测试报告

> 基准对比条形图、图例、live benchmark 实测、方法论说明
> 执行时间: 2026-09-20T08:29:28.423Z ~ 2026-09-20T08:29:39.337Z · 环境: http://localhost:5173 (vite dev) + :8088 (Spring Boot) · 1440×900 Chromium

**结论: 4 例, ✅ 4 通过, ❌ 0 失败 (通过率 100%)**

| 用例 | 名称 | 结果 | 耗时 | 证据 |
| --- | --- | --- | --- | --- |
| F-01 | 页面加载与静态基准图 | ✅ 通过 | 1483ms | `bl-06-perf/shots/F-01-01-perf-static.png` |
| F-02 | Live benchmark 运行与提速可信度 | ✅ 通过 | 1748ms | `bl-06-perf/shots/F-02-01-perf-live.png` |
| F-03 | 条形宽度与数值单调一致 | ✅ 通过 | 1448ms | — |
| F-04 | 方法论与英文文案 | ✅ 通过 | 3082ms | `bl-06-perf/shots/F-04-01-perf-en.png` |

## 执行备注

- [F-02] live 结果: ["5000 次迭代","bare render: 141 ms (avg 28266 ns)","compiled render: 118 ms (avg 23766 ns)","提速: 1.19x"]

## 证据清单

截图按用例编号存储于本目录 `shots/`；失败用例保留全过程截图与 `events.json`（console/pageerror/网络事件），通过用例仅保留关键截图。