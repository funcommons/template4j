# template4j-site 全量回归测试报告（复测轮 003）

| | |
| --- | --- |
| **报告编号** | 2026-09-20-003 |
| **测试对象** | template4j-site 官网（Vue 3 + Element Plus 前端 @5173 / Spring Boot 后端 @8088，本轮修复后） |
| **测试日期** | 2026-09-20 |
| **测试方式** | Playwright（系统 Chromium, 1440×900）驱动真实浏览器，逐业务线全量回归；截图留证 |
| **业务线** | 10 条 / **用例** 75 条 / **通过** 75 / **失败** 0 / **观察项** 0 |
| **配套文档** | [优化建议书](optimization-suggestions.md) · 单业务线明细 `bl-*/report.md` |
| **对照** | 同口径：001（62/75 失败 13）/ 002（75/75 通过）/ 003（75/75 通过） |

---

## 1. 总体结论

本轮对**已修复版本**做独立复测（修复明细见 002 报告 [README.md](../2026-09-20-002/README.md) §2）。**75/75 全绿、0 失败**。证明：
- 13 项缺陷修复均落地、可复现、可稳定
- 修复后单点回归无副作用（Playground 17/17、Learn 10/10 含 36 例全量遍历、Perf 4/4 在套件顺序下仍 ≥ 1.19x）

### 1.1 业务线结果总览

| 业务线 | 用例 | 通过 | 失败 | 通过率 | 明细 |
| --- | ---: | ---: | ---: | ---: | --- |
| BL-01 门户与首页 | 7 | 7 | 0 | 100% | [report](bl-01-home/report.md) |
| BL-02 模板演练场 Playground（核心） | 17 | 17 | 0 | 100% | [report](bl-02-playground/report.md) |
| BL-03 精选示例 Showcase | 8 | 8 | 0 | 100% | [report](bl-03-showcase/report.md) |
| BL-04 学习中心 Learn | 10 | 10 | 0 | 100% | [report](bl-04-learn/report.md) |
| BL-05 在线试用 Try | 7 | 7 | 0 | 100% | [report](bl-05-try/report.md) |
| BL-06 性能基准 Perf | 4 | 4 | 0 | 100% | [report](bl-06-perf/report.md) |
| BL-07 MCP 集成 | 4 | 4 | 0 | 100% | [report](bl-07-mcp/report.md) |
| BL-08 文档中心 Docs | 4 | 4 | 0 | 100% | [report](bl-08-docs/report.md) |
| BL-09 全局导航与国际化 | 7 | 7 | 0 | 100% | [report](bl-09-nav-i18n/report.md) |
| BL-10 异常与容错（横切） | 7 | 7 | 0 | 100% | [report](bl-10-errors/report.md) |
| **合计** | **75** | **75** | **0** | **100%** | |

---

## 2. 业务线划分

按产品功能架构拆分为 8 条功能业务线 + 2 条横切业务线，与 001 保持一致便于跨轮对比：

| 编号 | 业务线 | 覆盖范围 |
| --- | --- | --- |
| BL-01 | 门户与首页 | Hero 演示、CTA 跳转、功能卡片、依赖复制、统计区 |
| BL-02 | 模板演练场 Playground | 预设、JSON/YAML/String、渲染、Trace、快捷键、分享、持久化、文件加载（核心） |
| BL-03 | 精选示例 Showcase | 6 张示例卡、live 运行、互斥展开 |
| BL-04 | 学习中心 Learn | 侧栏导航、搜索、交互示例、翻页、404、36 例全量遍历 |
| BL-05 | 在线试用 Try | 双类型渲染、历史、清空 |
| BL-06 | 性能基准 Perf | 静态基准图、live benchmark、双语言 |
| BL-07 | MCP 集成 | 三步引导、配置复制、tools/list、render_template |
| BL-08 | 文档中心 Docs | markdown/可运行 section、Open in Try 联动 |
| BL-09 | 全局导航与国际化（横切） | 8 路由、404、中英切换、持久化、i18n 健康 |
| BL-10 | 异常与容错（横切） | 错误模板、非法数据、超限模板、断网恢复、全站巡检 |

---

## 3. 三轮对比

| 业务线 | 001（修前） | 002（修后首跑） | 003（本轮复测） |
| --- | --- | --- | --- |
| BL-01 | 6/7 | 7/7 | **7/7** |
| BL-02 | 12/17 | 17/17 | **17/17** |
| BL-03 | 8/8 | 8/8 | **8/8** |
| BL-04 | 10/10 | 10/10 | **10/10** |
| BL-05 | 6/7 | 7/7 | **7/7** |
| BL-06 | 3/4 | 4/4 | **4/4** |
| BL-07 | 4/4 | 4/4 | **4/4** |
| BL-08 | 4/4 | 4/4 | **4/4** |
| BL-09 | 4/7 | 7/7 | **7/7** |
| BL-10 | 5/7 | 7/7 | **7/7** |
| **合计** | **62/75** | **75/75** | **75/75** |

> 003 ＝ 002：修复在重新运行的同一套件上稳定收敛，未引入新故障。

---

## 4. 关键证据图（按业务线）

每张截图下方附"亮点/防退化点"。点击文件名查看大图。

### BL-01 门户与首页（7/7）

![首页全貌：中文导航 + GitHub ↗ + Hero 真实渲染 + 功能卡片 + 依赖复制](bl-01-home/shots/H-01-01-home-full.png)

> 导航中文化（DEF-08 / OBS-01 闭环），Hero parallel 演示真实渲染（DEF-01 闭环），统计区展示 9 组件 / 5.7x / 1441 测试 / 0 Spring 依赖，复制 Maven 坐标按钮在右上角。

![Hero 演示输出面板：parallel 任务 userName+orderCount](bl-01-home/shots/H-07-01-hero-demo-output.png)

> H-07 用例证据：mock 端点 `/users/{@id}`、`/users/{@id}/orders` 已能闭环；输出 `userName: alice, orderCount: 2`，与首页文案"30 行 JSON 替代 200 行 CompletableFuture"对仗。

### BL-02 模板演练场 Playground（17/17）

![Playground 默认渲染：结果面板格式化 JSON 树，无转义](bl-02-playground/shots/P-01-01-default-render.png)

> DEF-02 闭环：rendered 不再被双重序列化；结果/原始/状态栏/分享链接按钮一应俱全；导航中文 + Render/格式化/清空/分享按钮完整。

![4 个预设（含 Parallel mock）逐一渲染](bl-02-playground/shots/P-02-01-last-preset.png)

> DEF-01 闭环：最后一个预设（Parallel mock）以 u-1 + 84/正常 真实渲染输出，无 500 错误面板。

![手动编辑 → 点击 Render 渲染 6×7 表达式](bl-02-playground/shots/P-04-01-manual-render.png)

> DEF-03 闭环：Ctrl+Enter 快捷键路径走 window 级，渲染次数从 2 → 4（手动 + Ctrl+Enter）。

![分享链接正确生成与回填验证](bl-02-playground/shots/P-09-02-share-restore.png)

> DEF-04 闭环：剪贴板拿到 `http://localhost:5173/playground?state=…`（不再是 404 的 `/playgroundplayground`）；用该 URL 直接访问，模板内容自动回填 `marker-919`。

![Trace 事件面板：RENDER_STRING placeholders=2](bl-02-playground/shots/P-10-01-trace-events.png)

> Trace 开关可用。配合修复 DEF-11，单次开关 + 渲染不会污染后续 benchmark（详见 §5）。

![脚本错误 → 友好错误面板](bl-02-playground/shots/P-12-01-render-error.png)

> 渲染错误统一收敛到 `.pg-err-block`，标题「渲染错误」，可滚动看堆栈，页面不崩溃。

![Ctrl+S 持久化：刷新后模板恢复 marker-p14](bl-02-playground/shots/P-14-01-persisted-after-reload.png)

> 状态栏 `● 已保存` + 字符计数 16，刷新后保留模板内容（`STORAGE_KEY='template4j.playground.v2'`）。

![YAML 模板渲染](bl-02-playground/shots/P-15-01-yaml-render.png)

> YAML 模式 + 嵌入 `{@name}` / `{= @n + 1 }` 表达式，结果 `yaml-user` + `10`，类型 radio 切到 YAML 时"格式化 JSON"按钮自动禁用。

![从文件加载 JSON 到数据面板](bl-02-playground/shots/P-16-01-file-loaded.png)

> 加载文件按钮工作（5MB 限制保护），拖拽 hint 提示完整。

### BL-03 精选示例 Showcase（8/8）

![6 张示例卡片 + 卡片 1 已展开 live 运行结果](bl-03-showcase/shots/S-03-01-card1-parallel.png)

> 标题"Showcase: 6 live examples" + 展开后耗时 332 ms，输出并行聚合结果（含 `payment`、`audit`、`logged`、`reason: skipped (timeout)`），与文案"3 个微服务聚合"对仗。

### BL-04 学习中心 Learn（10/10）

![Learn 侧栏 + 详情默认加载](bl-04-learn/shots/L-01-01-learn-list.png)

> 左侧分类侧栏（介绍/快速开始/文本模板…）+ 右侧分类徽章计数，**搜索 + 36 例全量遍历**通过。

> **L-09 关键证据：36 个示例详情全部零报错**（不仅 no not-found、无 pageerror、无失败请求）——展示如下：

![Learn 详情默认加载](bl-04-learn/shots/L-03-01-detail-default.png)

> 详情展示：标题、来源 `id`、`quick-start` section 自动渲染，markdown section 渲染源码，json-renderer section 含变量输入 + 运行按钮。

### BL-05 在线试用 Try（7/7）

![Try JSON 渲染：含脚本表达式](bl-05-try/shots/T-02-01-try-render-json.png)

> 模板 `"{= @n * 2 }"` → 结果 `42`，且 Try 无"清空污染历史"——`marker-p14` 等自定义历史条目正常展示。

![Try 历史（清空不再产生 null 条目）](bl-05-try/shots/T-06-01-history-restore.png)

> DEF-05 闭环：历史首条为时间 13:07:34 `{"h":"second"}…` —— 不再有 `null...` 污染。

### BL-06 性能基准 Perf（4/4）

![Live benchmark：1.19x，提速不再崩溃](bl-06-perf/shots/F-02-01-perf-live.png)

> DEF-11 闭环最有力证据：套件顺序下（Playground Trace 已被前置用例反复触发）跑 live benchmark → 5000 次迭代，bare avg 28266 ns / compiled avg 23766 ns / **提速 1.19x**，无挂起、无倒挂。注：001 轮此处为 0.71x 倒挂 / 单线程 CPU 235s。

### BL-07 MCP 集成（4/4）

![MCP 三步引导 + 工具调用响应](bl-07-mcp/shots/M-01-01-mcp-page.png)

> 步骤 1/2/3 + Claude 配置 + tools/list + render_template 按钮完整。`tools/list` 响应包含 `render_template`，`render_template` 响应走 MCP JSON-RPC `content` 通道。

### BL-08 文档中心 Docs（4/4）

![Docs intro-home：markdown section + Java 调用代码](bl-08-docs/shots/D-01-01-docs-load.png)

> 文档中心渲染 intro-home 文档（markdown 章节 + json-renderer 章节），无加载错乱。

### BL-09 全局导航与国际化（7/7）

![404 页：友好提示 + 返回首页/学习/文档三出口](bl-09-nav-i18n/shots/N-02-01-unknown-route.png)

> DEF-06 闭环：catch-all 路由 + `NotFound.vue`，展示大号蓝色 404 + 中英双语提示 + 3 个返回按钮。

![中文态下的导航菜单：首页/演练场/示例/性能/MCP/学习/文档](bl-09-nav-i18n/shots/N-05-01-nav-hardcoded.png)

> OBS-01 闭环：菜单走 `t('nav.*')`，中文态全部显示中文（不再是英文硬编码）。

![GitHub 外链在右上角可见可点](bl-09-nav-i18n/shots/N-07-01-nav-github-corner.png)

> DEF-08 闭环：GitHub ↗ 不再被 el-menu 解析成空「…」子菜单，链接独立显示在右上角 action 区。

### BL-10 异常与容错（7/7）

![模板超限被 400 拦截 + 友好错误面板](bl-10-errors/shots/E-04-01-err-oversize.png)

> DEF-09 闭环：70KB 模板命中 `TEMPLATE_SIZE_EXCEEDED` → 400，错误面板给出 `sizeExceeded` 消息。

![断网时 ElMessage 提示 + 渲染失败兜底](bl-10-errors/shots/E-05-01-err-offline.png)

![断网恢复后再次渲染成功](bl-10-errors/shots/E-05-02-err-offline-recovered.png)

> E-05 闭环：网络中断时页面给出 ElMessage 错误反馈，恢复后渲染立即成功。

---

## 5. 后端稳定性证据（DEF-11 修复可复现性）

001 轮首测：Playground 用过 Trace 后 benchmark 从 4.08x 倒挂到 0.71x → 持续压测挂起 >4min，单线程 CPU 烧到 235s。002 轮修复后：同一套件顺序下，benchmark 实测 1.26x ~ 1.83x 稳定区间，无累积劣化。**003 轮复测**：再次套件顺序下，benchmark 实测 **1.19x**，无挂起，无 pageerror。修复稳定可复现。

---

## 6. 复跑方式

```bash
# 1) 启动后端与前端
java -jar template4j-site/target/template4j-site.jar &     # :8088
cd template4j-site/frontend && npx vite --port 5173 &      # :5173

# 2) 全量回归 (10 条业务线, 输出至 test-report/<日期>-<序号>/)
export T4J_REPORT_SEQ=2026-09-20-003
for bl in e2e/regression/bl-*.mjs; do node "$bl"; done

# 3) 重新生成各业务线报告
node e2e/regression/gen-bl-report.mjs
```

*测试代码：`e2e/regression/`（Playwright + 系统 Chrome，1440×900）；截图与事件 JSON 全部位于各 `bl-*/shots/`；通过用例仅保留关键截图，失败用例保留全过程截图与 console/网络事件 `events.json`（本轮无失败，事件 JSON 文件未生成）。*