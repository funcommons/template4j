# BL-04 学习中心 Learn — 回归测试报告

> 示例导航、搜索、详情交互（运行/变量/复制/翻页）、404、全部示例遍历回归
> 执行时间: 2026-09-20T07:15:13.319Z ~ 2026-09-20T07:16:42.643Z · 环境: http://localhost:5173 (vite dev) + :8088 (Spring Boot) · 1440×900 Chromium

**结论: 10 例, ✅ 10 通过, ❌ 0 失败 (通过率 100%)**

| 用例 | 名称 | 结果 | 耗时 | 证据 |
| --- | --- | --- | --- | --- |
| L-01 | 列表加载：分类侧栏与示例数量 | ✅ 通过 | 1916ms | `bl-04-learn/shots/L-01-01-learn-list.png` |
| L-02 | 搜索过滤 | ✅ 通过 | 3157ms | `bl-04-learn/shots/L-02-01-search-if.png` |
| L-03 | 默认详情加载（首个示例） | ✅ 通过 | 1859ms | `bl-04-learn/shots/L-03-01-detail-default.png` |
| L-04 | 直达详情与上一个/下一个翻页 | ✅ 通过 | 3472ms | `bl-04-learn/shots/L-04-01-prev-next-nav.png` |
| L-05 | 运行可执行示例 | ✅ 通过 | 33177ms | `bl-04-learn/shots/L-05-01-run-quickstart.png` |
| L-06 | 修改变量后重新运行 | ✅ 通过 | 3182ms | `bl-04-learn/shots/L-06-01-var-edit-run.png` |
| L-07 | 复制按钮 | ✅ 通过 | 1914ms | — |
| L-08 | 未知示例 id → 404 提示页 | ✅ 通过 | 2666ms | `bl-04-learn/shots/L-08-01-not-found.png` |
| L-09 | 全量遍历 36 个示例详情（零报错） | ✅ 通过 | 32917ms | `bl-04-learn/shots/L-09-01-traverse-last.png` |
| L-10 | 侧栏分类展开与收起 | ✅ 通过 | 2701ms | — |

## 执行备注

- [L-09] 共 36 个示例

## 证据清单

截图按用例编号存储于本目录 `shots/`；失败用例保留全过程截图与 `events.json`（console/pageerror/网络事件），通过用例仅保留关键截图。