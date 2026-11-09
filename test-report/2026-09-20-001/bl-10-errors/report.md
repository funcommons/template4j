# BL-10 异常与容错 — 回归测试报告

> 错误输入、边界输入、网络故障下的 UI 兜底与恢复能力
> 执行时间: 2026-09-20T05:03:45.543Z ~ 2026-09-20T05:04:24.098Z · 环境: http://localhost:5173 (vite dev) + :8088 (Spring Boot) · 1440×900 Chromium

**结论: 7 例, ✅ 5 通过, ❌ 2 失败 (通过率 71%)**

| 用例 | 名称 | 结果 | 耗时 | 证据 |
| --- | --- | --- | --- | --- |
| E-01 | 模板脚本错误 → 错误面板且页面不崩 | ✅ 通过 | 4068ms | `bl-10-errors/shots/E-01-01-err-template.png` |
| E-02 | 非法 JSON 数据 → 行内提示不崩溃 | ✅ 通过 | 2914ms | `bl-10-errors/shots/E-02-01-err-bad-data.png` |
| E-03 | 空模板点击渲染行为 | ✅ 通过 | 3681ms | `bl-10-errors/shots/E-03-01-err-empty-template.png` |
| E-04 | 超限模板（>64KB）→ 服务端限制兜底 | ❌ 失败 | 4654ms | `bl-10-errors/shots/E-04-01-err-oversize.png` · `bl-10-errors/shots/E-04-02-zz-final-fullpage.png` · `bl-10-errors/shots/E-04-events.json` |
| E-05 | 断网（API 不可达）→ 错误反馈且可恢复 | ✅ 通过 | 3777ms | `bl-10-errors/shots/E-05-01-err-offline.png` · `bl-10-errors/shots/E-05-02-err-offline-recovered.png` |
| E-06 | Learn 未知 id 容错（错误视角） | ✅ 通过 | 1719ms | `bl-10-errors/shots/E-06-01-err-learn-404.png` |
| E-07 | 全站 8 页巡检：零 pageerror / 零异常 4xx | ❌ 失败 | 14716ms | `bl-10-errors/shots/E-07-01-sweep-last.png` · `bl-10-errors/shots/E-07-02-zz-final-fullpage.png` · `bl-10-errors/shots/E-07-events.json` |

## 失败用例与修复意见

### E-04 超限模板（>64KB）→ 服务端限制兜底

- **现象**: 70KB 模板未被拦截(文档限制 65536): 无 4xx、无错误面板。api4xx=[]
- **根因**: /api/try、/api/try/stream 未执行 max-template-size 校验（application.yml 已配置 template4j.demo.max-template-size: 65536）
- **修复意见**: 在 TryController/StreamController 入口校验 template 字节数, 超限返回 400 + 业务码; 前端编辑器字数统计处提前预警。

### E-07 全站 8 页巡检：零 pageerror / 零异常 4xx

- **现象**: 巡检发现 1 个异常: [
 {
  "page": "/",
  "type": "api4xx",
  "text": "400 http://localhost:5173/api/try"
 }
]
- **根因**: 多为首页 hero 演示 400（同 H-07）; 明细见 events
- **修复意见**: 修复 mock 端点后归零; requestfailed 需逐条排查。

## 证据清单

截图按用例编号存储于本目录 `shots/`；失败用例保留全过程截图与 `events.json`（console/pageerror/网络事件），通过用例仅保留关键截图。