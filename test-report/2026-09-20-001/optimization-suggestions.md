# template4j-site 优化建议书

> 评审基准：以成熟平台（Vercel / Stripe Docs / Ant Design 官网 / 阿里云 OpenAPI 体验站 / Twilio Docs）的产品与交互规范为参照，结合 2026-09-20 全量回归测试中发现的实际问题，从「界面人性化、功能合理化、布局与视觉、工程质量」四个维度给出建议。
> 每条建议标注优先级：P0（伤害核心体验，随下个版本修复）/ P1（显著提升体验）/ P2（打磨项）。

---

## 一、缺陷修复（测试发现，先于一切优化）

> 详见《README.md》统一测试报告。以下缺陷直接影响"开箱体验"，建议按 P0 处理。

| # | 缺陷 | 影响 | 建议 |
| --- | --- | --- | --- |
| 1 | Playground「结果/原始」面板把已序列化的 JSON 字符串再次 `JSON.stringify`（双重序列化），用户看到的是 `"{\n \"msg\": ...}"` 转义串而非 JSON 树 | 核心功能展示不可读 | SSE `done` 事件若 `rendered` 为字符串，先 `JSON.parse` 再格式化；或后端保证返回对象 |
| 2 | `Ctrl+Enter` 渲染快捷键失效：keydown 绑定在 `display:none` 的 fallback textarea 上，可见的 CodeEditor 不触发 | 状态栏明示的快捷键不可用 | 把快捷键处理挂到 CodeEditor 组件内部 textarea，或页面级 keydown 监听 |
| 3 | 分享链接路径拼接错误：`location.pathname + 'playground'` 产生 `/playgroundplayground?state=...` | 分享出去的链接 404 | 改用 `router.resolve({path:'/playground', query}).href`；复制成功后提示可点击的链接 |
| 4 | 后端缺少 `/users`、`/orders`（及 `/api/user`、`/api/orders`）mock 端点 → 首页 Hero 演示、Playground「Parallel mock」预设、Showcase 卡 1/2 全部渲染失败或降级 | 门面演示开箱即坏，第一印象致命 | 在 site 后端补一组确定性 mock Controller（参考 showcase 自述"走 demo 后端 + mock endpoints"）；或前端内置 MSW 级 mock |
| 5 | 无 404 路由：未知路径白屏（仅剩导航栏） | 迷路用户无出路 | `router` 增加 `/:pathMatch(.*)*` → 404 视图，含「返回首页」与搜索入口 |
| 6 | `App.vue` 在 `el-menu` 内使用原生 `<li class="nav-gh">`，被 Element Plus 解析为空 `el-sub-menu`：GitHub 入口不可见（0×0），导航栏出现莫名「…」菜单 | 外链入口丢失 + 界面异物 | 外链类动作移出 `el-menu`，用 header 右侧 action 区（icon-button）承载 |
| 7 | Try 页「清空」调用 `setResult` 把空状态写入历史，历史首条变为 `null` 条目，点击"恢复"得到空模板 | 历史功能可信度受损 | `clear()` 不写历史；或历史过滤 `rendered == null`；空态条目不渲染 |
| 8 | **后端 trace 订阅泄漏**：StreamController 的 SSE trace 订阅者在连接结束后未从共享 `RenderEngine` 反注册，后续任意请求的 trace 事件继续向死连接写入。实测：干净后端 live benchmark 4.08x，Playground 用过 Trace 后倒挂 0.71x，持续压测接口挂起（HTTP 线程单线程 CPU 235s） | 后端随使用时间整体劣化；Perf 页 live 数据失真；长驻进程内存无界增长——**生产级最严重问题** | SSE 会话结束（onComplete/onError/onTimeout）反注册消费者，或 trace 消费者挂请求级 Context 而非共享引擎；补一条"Playground 开 Trace → benchmark 提速不低于基线 50%"的回归用例 |

---

## 二、界面人性化

### 2.1 语言与文案（P0）
- **导航菜单硬编码英文**（`Home/Playground/...`），语言切到中文后菜单不变。大厂做法：全站文案走 i18n，包括导航、按钮 tooltip、空态。当前 `nav.home='首页'` 等 key 已存在却未使用。
- 错误信息人性化：Groovy/渲染异常目前把原始堆栈直接铺在错误面板。参照 Stripe/Datadog：一句话人话结论（"第 3 行的表达式无法解析"）+ 可折叠「技术详情」+ 一键复制错误（含 `trace_id`，后端响应已返回该字段但 UI 未展示）。
- 状态栏 `● 有未保存的修改` 建议改为可点击（点击即保存），并配合 `Ctrl+S`；"已保存"用绿色对勾图标强化（现在已有配色，补图标）。

### 2.2 反馈与确认（P1）
- **清空无撤销**：Playground/Try 的「清空」直接抹掉编辑内容。大厂通用模式是"操作 + Undo toast（5 秒）"，比弹确认框打断更顺滑。
- **分享成功后给出可见链接**：剪贴板可能被浏览器策略拦截，失败 fallback 已有 warning，建议进一步在页面内展示可复制链接条（参考 Gravatar/Notion 的 share popover）。
- **危险操作二次确认**：清空/覆盖预设建议 `ElMessageBox.confirm`（低打断场景用 Undo 替代）。

### 2.3 加载与空态（P1）
- Docs/Learn 加载仅有文字"加载中..."，参照大厂内容站使用 **Skeleton 骨架屏**（Element Plus `el-skeleton`），杜绝布局跳动。
- Trace 空态只有一句"暂无追踪事件"，建议补一张引导图 + 「打开 Trace 并渲染」按钮，把空态变成引导态（empty state → onboarding）。
- Try 无历史时隐藏历史区块即可（当前已处理 ✓），但历史条目里 `null` 渲染成 `"null..."` 需过滤（见缺陷 7）。

### 2.4 可访问性 a11y（P1，成熟平台 CI 卡口项）
- 图标按钮补 `aria-label`（▶ Render、📁 加载文件、语言切换 🌐）。
- 状态不只靠颜色：数据非法的 `✗ 无效` 已有符号 ✓；Trace 行类型用背景色区分，建议加左侧色条 + 图标。
- 焦点管理：展开卡片、tab 切换支持方向键；弹层关闭后焦点返还触发钮。
- 以 axe DevTools 跑基线，把"严重违规 = 0"纳入 CI。

### 2.5 主题与个性化（P2）
- `usePreference` 已有 `theme` 字段与 localStorage 持久化，但无切换入口——补一个亮/暗切换按钮（Element Plus 暗色变量已就绪）。代码块 `.code-block` 背景硬编码 `#f5f5f5`，暗色下会刺眼，改用 `var(--el-fill-color-light)`。

---

## 三、功能合理化

### 3.1 Playground（核心，投入产出比最高）
- **预设保存闭环**（P0 级功能断层）：`Ctrl+S` 提示"已保存到浏览器"，但保存的预设没有任何 UI 入口可列出/加载/删除——用户存了就"丢"了。建议：预设下拉分「内置预设 / 我的预设」两组，我的预设支持重命名与删除。
- **输出区操作**（P1）：加「复制结果」「下载 .json」「在 Try 中打开」；大厂 Playground（如 Gauss/Redis 官网）标配。
- **错误定位**（P1）：渲染错误若能给出模板行号/路径（trace 已有 path 信息），点击可滚动到编辑器对应位置。
- **对比视图**（P2）：模板 → 渲染结果的字段映射 diff（教学价值高，契合 Learn 场景）。

### 3.2 信息架构收敛（P1）
- **Try 与 Playground 功能高度重叠**（模板+数据+渲染）。建议 Try 并入 Playground 作为"极简模式"，或明确分工：Try=免输入的快速示例，点击即用（预填示例模板），Playground=完整工程台。当前 Try 空着两只编辑框，新用户面对空白不知从何下手。
- 导航顺序调整（见 4.1）。

### 3.3 Learn 学习中心（P1）
- **学习进度**：36 个示例建议打点（已读/已运行），侧栏显示进度环，激励完成率——成熟文档站（react.dev/tutorial）标配。
- **全屏运行**：交互示例支持「在 Playground 中打开」整例带入（目前只有单 section 的 openInTry）。
- 搜索命中关键词高亮；按难度/分类的筛选 chip 补齐（数据结构已有 difficulty 字段）。

### 3.4 Showcase / Perf / MCP / Docs（P2）
- Showcase 无端点卡片（3/4）显示"无 live 端点"，建议提供**静态模拟输出**或跳转文档锚点，别让卡片"点了个寂寞"。
- Perf 静态基准数据硬编码在前端，建议 API 化并带测试时间戳/环境信息，增强可信度（大厂 benchmark 页都会标注 "measured on ..., 2026-09"）；live benchmark 结果支持复制为 Markdown 表格；**当前 live 数据已被缺陷 8 的 trace 泄漏污染（0.71x 倒挂），修复前应对 speedup<1 显示醒目警告**。
- MCP 页提供「下载 claude_desktop_config.json」与 curl 示例复制；tools/list 输出做 JSON 高亮。
- Docs 与 Learn 内容重复（Docs 实际渲染的是 learn 的 intro-home）。建议 Docs 定位为「API 参考 + 语法速查表」，与 Learn（教程）区分；补 TOC 目录与全文搜索。

---

## 四、布局与视觉（对标成熟平台）

### 4.1 导航与信息架构（P0/P1）
- 顺序建议：`Home · Learn · Playground · Showcase · Docs · MCP · Perf`。学习路径前置（当前 Learn 排第 6）；Perf/MCP 是特性展示页，可收进「更多」或页脚。
- Header 右侧统一 action 区：语言切换、主题切换、GitHub icon（用 `el-icon` + svg，而非被吞的 `<li>`）、**版本号徽标**（当前全站无版本标识，成熟平台必有，且与 Maven 版本联动）。
- 移动端/窄屏：导航无折叠策略，768px 以下会溢出（本次 1440px 宽通过，建议补 375/768 两档的响应式断言进 E2E）。

### 4.2 设计 Token 统一（P1）
- 颜色散落硬编码：`#10b981/#ef4444/#f59e0b/#a78bfa...` 建议收敛为 CSS variables 并对齐 Element Plus 主题（成功/危险/警告/信息），为暗色主题与多品牌铺路（`usePreference.brand` 字段已预留）。
- 图标体系混用 emoji（▶ 📁 🌐 ⚠️ 🔀 🛡️）与 Element icons，统一用 `@element-plus/icons-vue`；emoji 在 Win/macOS 渲染不一致且有"玩具感"。
- 代码块统一组件化：语法高亮（Shiki/highlight.js）+ 复制按钮 + 可折叠。现状 Docs 的 markdown 用 `<pre>` 裸展示源码，Learn 有复制按钮但无高亮，Playground 的 CodeEditor 自带高亮——三套表现应归一。

### 4.3 排版细节（P2）
- 正文列宽：Docs/Showcase 长文本限 `max-width: 72ch`，行高 1.7，提升可读性。
- 卡片阴影/圆角/间距 token 化（当前卡片 radius 6px、面板 4px、live 面板 8px 三种并存）。
- 表格/键值对（如 API 文档的 endpoint 列表）用 `el-table` 统一斑马纹与 hover。

---

## 五、工程质量（大厂研发规范）

1. **E2E 进 CI**：本次产出的 10 条业务线 Playwright 回归套件可直接接入 GitHub Actions（`mvn package` → `java -jar site` → `vite dev` → `node e2e/regression/*.mjs`），PR 门禁跑冒烟子集（每线 1-2 例），夜间跑全量。
2. **契约层 mock**：为 `/users`、`/orders` 等演示依赖建立确定性 mock（后端 Controller 或前端 MSW），让"开箱演示"与环境解耦（对应缺陷 4）。
3. **前端可观测性**：接入 Sentry/自建 onError 上报，`X-Trace-Id` 已全链路贯穿（client.ts 拦截器已实现），缺的是 UI 展示与日志平台查询入口。
4. **i18n 卡口**：CI 中以 vue-i18n 的 missing 提示为 error（本次 N-06 已巡检双语言 × 8 页面零缺失），新增 key 未翻译即红。
5. **组件测试**：当前前端仅 utils 有单测；CodeEditor/RenderedMarkdown 是复杂组件，建议 Vitest + Vue Test Utils 补关键交互。
6. **性能预算**：Lighthouse CI 卡 LCP/CLS；Playground 引入 Monaco/Shiki 等重依赖前先做 bundle 预算。
7. **无障碍基线**：axe 扫描进 CI，serious/critical 为零。

---

## 六、落地路线图建议

| 阶段 | 内容 | 目标 |
| --- | --- | --- |
| 第 1 周 | 缺陷 1-8 修复（trace 泄漏优先）+ mock 端点补齐 + 回归套件进 CI | 开箱体验与服务健康达标，防回归 |
| 第 2-3 周 | 预设保存闭环、输出区操作、404 页、header action 区与版本徽标、导航 i18n | 对齐成熟平台基本盘 |
| 第 4 周起 | Learn 进度与筛选、Try/Playground IA 收敛、暗色主题、a11y 与 Lighthouse 卡口 | 打造差异化打磨项 |

---

*本建议书基于 2026-09-20 全量回归测试（10 条业务线 / 75 用例）的实测证据与代码走读产出。*
