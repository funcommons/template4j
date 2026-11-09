# BL-06 性能基准 Perf — 回归测试报告

> 基准对比条形图、图例、live benchmark 实测、方法论说明
> 执行时间: 2026-09-20T05:27:38.245Z ~ 2026-09-20T05:27:56.032Z · 环境: http://localhost:5173 (vite dev) + :8088 (Spring Boot) · 1440×900 Chromium

**结论: 4 例, ✅ 3 通过, ❌ 1 失败 (通过率 75%)**

| 用例 | 名称 | 结果 | 耗时 | 证据 |
| --- | --- | --- | --- | --- |
| F-01 | 页面加载与静态基准图 | ✅ 通过 | 1476ms | `bl-06-perf/shots/F-01-01-perf-static.png` |
| F-02 | Live benchmark 运行与提速可信度 | ❌ 失败 | 9325ms | `bl-06-perf/shots/F-02-01-perf-live.png` · `bl-06-perf/shots/F-02-02-zz-final-fullpage.png` · `bl-06-perf/shots/F-02-events.json` |
| F-03 | 条形宽度与数值单调一致 | ✅ 通过 | 1434ms | — |
| F-04 | 方法论与英文文案 | ✅ 通过 | 3033ms | `bl-06-perf/shots/F-04-01-perf-en.png` |

## 失败用例与修复意见

### F-02 Live benchmark 运行与提速可信度

- **现象**: live benchmark 提速 0.71x (<1x, 编译渲染反而更慢), 与本页宣称的 5.7x 静态结论矛盾 — 可信度缺陷。明细: ["5000 次迭代","bare render: 3270 ms (avg 654131 ns)","compiled render: 4587 ms (avg 917464 ns)","提速: 0.71x"]
- **根因**: 后端资源泄漏: StreamController 的 SSE trace 订阅者在连接结束后未从共享 RenderEngine 反注册, 后续任意请求的 trace 事件继续向已断开的 SSE 连接写入（jstack: exec 线程卡在 StreamController.lambda$stream$0 → HashMap.putVal, 单线程累计 CPU 235s）。证据: F-02-jstack-evidence.txt
- **修复意见**: 核心修复: SSE 会话结束(onComplete/onError/onTimeout)时反注册 trace 消费者, 或 trace 消费者挂到请求级 Context 而非共享引擎。防御: /api/demo/compile 前重置引擎; 前端对 speedup<1 显示醒目警告。回归: 先访问 Playground 开 Trace, 再跑 benchmark, 断言 speedup ≥ 基线的 50%。

## 执行备注

- [F-02] live 结果: ["5000 次迭代","bare render: 3270 ms (avg 654131 ns)","compiled render: 4587 ms (avg 917464 ns)","提速: 0.71x"]

## 证据清单

截图按用例编号存储于本目录 `shots/`；失败用例保留全过程截图与 `events.json`（console/pageerror/网络事件），通过用例仅保留关键截图。