# BL-10 异常与容错 — 回归测试报告

> 错误输入、边界输入、网络故障下的 UI 兜底与恢复能力
> 执行时间: 2026-09-20T07:29:59.565Z ~ 2026-09-20T07:30:37.176Z · 环境: http://localhost:5173 (vite dev) + :8088 (Spring Boot) · 1440×900 Chromium

**结论: 7 例, ✅ 7 通过, ❌ 0 失败 (通过率 100%)**

| 用例 | 名称 | 结果 | 耗时 | 证据 |
| --- | --- | --- | --- | --- |
| E-01 | 模板脚本错误 → 错误面板且页面不崩 | ✅ 通过 | 4071ms | `bl-10-errors/shots/E-01-01-err-template.png` |
| E-02 | 非法 JSON 数据 → 行内提示不崩溃 | ✅ 通过 | 2830ms | `bl-10-errors/shots/E-02-01-err-bad-data.png` |
| E-03 | 空模板点击渲染行为 | ✅ 通过 | 3666ms | `bl-10-errors/shots/E-03-01-err-empty-template.png` |
| E-04 | 超限模板（>64KB）→ 服务端限制兜底 | ✅ 通过 | 4231ms | `bl-10-errors/shots/E-04-01-err-oversize.png` |
| E-05 | 断网（API 不可达）→ 错误反馈且可恢复 | ✅ 通过 | 3749ms | `bl-10-errors/shots/E-05-01-err-offline.png` · `bl-10-errors/shots/E-05-02-err-offline-recovered.png` |
| E-06 | Learn 未知 id 容错（错误视角） | ✅ 通过 | 1670ms | `bl-10-errors/shots/E-06-01-err-learn-404.png` |
| E-07 | 全站 8 页巡检：零 pageerror / 零异常 4xx | ✅ 通过 | 14611ms | `bl-10-errors/shots/E-07-01-sweep-last.png` |

## 执行备注

- [E-04] 服务端返回: 400 / 错误面板=1

## 证据清单

截图按用例编号存储于本目录 `shots/`；失败用例保留全过程截图与 `events.json`（console/pageerror/网络事件），通过用例仅保留关键截图。