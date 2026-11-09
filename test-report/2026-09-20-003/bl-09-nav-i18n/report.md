# BL-09 全局导航与国际化 — 回归测试报告

> 主导航全路由可达、未知路径兜底、中英文切换、语言持久化、i18n 缺失 key 巡检
> 执行时间: 2026-09-20T08:30:02.151Z ~ 2026-09-20T08:30:55.739Z · 环境: http://localhost:5173 (vite dev) + :8088 (Spring Boot) · 1440×900 Chromium

**结论: 7 例, ✅ 7 通过, ❌ 0 失败 (通过率 100%)**

| 用例 | 名称 | 结果 | 耗时 | 证据 |
| --- | --- | --- | --- | --- |
| N-01 | 8 个主路由全部可达 | ✅ 通过 | 10104ms | `bl-09-nav-i18n/shots/N-01-01-routes-all-last.png` |
| N-02 | 未知路径兜底（404 页） | ✅ 通过 | 1511ms | `bl-09-nav-i18n/shots/N-02-01-unknown-route.png` |
| N-03 | 中英切换即时生效 | ✅ 通过 | 4690ms | `bl-09-nav-i18n/shots/N-03-01-lang-en.png` |
| N-04 | 语言选择刷新持久化 | ✅ 通过 | 4153ms | `bl-09-nav-i18n/shots/N-04-01-lang-persist.png` |
| N-05 | 导航菜单硬编码检查（中文态下菜单语言） | ✅ 通过 | 1235ms | `bl-09-nav-i18n/shots/N-05-01-nav-hardcoded.png` |
| N-06 | i18n 健康度巡检（缺 key / 消息内嵌 HTML，全页面 × 双语言） | ✅ 通过 | 27970ms | `bl-09-nav-i18n/shots/N-06-01-i18n-sweep.png` |
| N-07 | GitHub 外链可见可用 | ✅ 通过 | 1405ms | `bl-09-nav-i18n/shots/N-07-01-nav-github-corner.png` |

## 执行备注

- [N-06] 缺失 key: 0 处; 消息内嵌 HTML 警告: 0 处

## 证据清单

截图按用例编号存储于本目录 `shots/`；失败用例保留全过程截图与 `events.json`（console/pageerror/网络事件），通过用例仅保留关键截图。