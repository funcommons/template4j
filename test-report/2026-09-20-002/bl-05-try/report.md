# BL-05 在线试用 Try — 回归测试报告

> 快速试用页：JSON/YAML 双类型渲染、结果与耗时、历史记录恢复、清空
> 执行时间: 2026-09-20T07:16:42.780Z ~ 2026-09-20T07:17:01.824Z · 环境: http://localhost:5173 (vite dev) + :8088 (Spring Boot) · 1440×900 Chromium

**结论: 7 例, ✅ 7 通过, ❌ 0 失败 (通过率 100%)**

| 用例 | 名称 | 结果 | 耗时 | 证据 |
| --- | --- | --- | --- | --- |
| T-01 | 页面加载与控件齐全 | ✅ 通过 | 1439ms | `bl-05-try/shots/T-01-01-try-default.png` |
| T-02 | JSON 模板渲染与结果 | ✅ 通过 | 1499ms | `bl-05-try/shots/T-02-01-try-render-json.png` |
| T-03 | YAML 模板渲染 | ✅ 通过 | 1467ms | `bl-05-try/shots/T-03-01-try-render-yaml.png` |
| T-04 | 数据非法 JSON 提示 | ✅ 通过 | 2250ms | `bl-05-try/shots/T-04-01-try-bad-json.png` |
| T-05 | 模板渲染错误兜底展示 | ✅ 通过 | 2950ms | `bl-05-try/shots/T-05-01-try-render-error.png` |
| T-06 | 历史记录生成与点击恢复 | ✅ 通过 | 4216ms | `bl-05-try/shots/T-06-01-history-restore.png` |
| T-07 | 清空操作 | ✅ 通过 | 2725ms | — |

## 执行备注

- [T-06] 历史条目: ["15:16:57{\"h\":\"second\"}...","15:16:56{\"h\":\"first\"}..."]
- [T-06] 清空后历史条目: ["15:16:57{\"h\":\"second\"}...","15:16:56{\"h\":\"first\"}..."]

## 证据清单

截图按用例编号存储于本目录 `shots/`；失败用例保留全过程截图与 `events.json`（console/pageerror/网络事件），通过用例仅保留关键截图。