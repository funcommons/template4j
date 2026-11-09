# template4j-site 优化建议书（003 复测后）

> 评审基准：以成熟开发者平台（Vercel / Stripe Docs / Ant Design 官网 / 阿里云 OpenAPI / react.dev / MDN / Linear / Vercel Templates / Twilio Docs）的产品与交互规范为参照，结合 2026-09-20 三轮回归（发现轮 001、修复后 002、复测轮 003）的实测证据与代码走读产出。
> 每条建议标注 **优先级**：**P0（伤害核心体验/数据可信度，下个迭代必修）/ P1（显著提升体验与商业化）/ P2（打磨与 a11y）**。
> 已修复的 13 项缺陷见 [README §2](./README.md#1-总体结论) 与 [002 报告](../2026-09-20-002/README.md) §2，本文档聚焦"修复完成后还差什么"。

---

## 0. 当前状态总评（复测 003）

**开箱体验达成基线**（001 轮的 TOP-4 阻断已全部闭环）：
- ✅ 首页 Hero / Playground 预设 / Showcase 1/2 卡 — 真实渲染，无降级文案
- ✅ 结果面板格式化 JSON，无双重转义
- ✅ Ctrl+Enter 渲染、分享链接、404 页、模板上限、后端泄漏 — 全修
- ✅ 中文导航、GitHub ↗ 入口、i18n 模板 — 全修

**但对标成熟平台，仍存在明显的**：

1. **信息架构冗余**：Try 与 Playground 高度重叠；Docs 与 Learn 内容部分重叠；MCP/Perf 是次级入口却被放主导航首位
2. **关键功能缺闭环**：Playground「保存预设」无 UI 入口；分享链接无可点提示；状态栏「已保存」是纯文本，不引导动作
3. **视觉/排版/可访问性**对标差距大（颜色硬编码、emoji 与 icon 混用、代码块无高亮、a11y 无卡口）
4. **商业化能力缺失**：未展示版本号徽标、未提供 GitHub 星星/下载量、未做 SEO/OG 元数据、Helm/Docker/CLI 引导入口缺失

下文按"界面人性化 → 功能合理化 → 布局与视觉 → 工程与商业化"四块给建议。

---

## 一、界面人性化

### 1.1 反馈与确认（P0）

| 现象 | 003 证据 | 大厂做法 | 建议 |
| --- | --- | --- | --- |
| 清空/覆盖为不可逆操作 | Try/T-06 历史污染（已修），但 Playground「清空」/Docs 「Run」误触无 Undo | Notion/Linear：操作 + 5 秒 Undo toast | 「清空」「恢复历史」「重置」统一接入 Undo Toast；危险操作（清空我的预设）走 `ElMessageBox.confirm` |
| 分享仅 toast「已复制」 | P-09：剪贴板被浏览器策略拦截时用户无法继续 | Vercel/GitHub：share popover 显示可点链接 + 二维码 | 弹出 share popover：链接 + 复制按钮 + 二维码 + "扫描预览"（桌面/移动自适应） |
| 渲染失败只给原文堆栈 | P-12：.pg-err-block 标题"渲染错误"加堆栈，但用户无修复引导 | Stripe Docs：人话结论 + 折叠技术细节 + "在 Playground 打开" | 错误面板分三段：人话结论（"第 3 行表达式 `+++` 解析失败"）+ 可折叠 stack + 「定位到编辑器」按钮（用 trace.path 滚动 + 高亮） |
| 没有"保存成功"的次级反馈 | P-14：`● 已保存` 文字一晃而过 | Linear：状态变化给短暂的「音效 + 微动画 + 持久可见态」 | ElMessage.success 加 toast + 状态栏图标动画；增加"未保存 30 秒"倒计时提示 |

### 1.2 加载与空态（P1）

| 现象 | 证据 | 建议 |
| --- | --- | --- |
| 加载态只有文字"加载中..." | Docs D-01 / Learn L-03 | `el-skeleton` 骨架屏 + 标题/正文/侧栏三段占位，杜绝布局跳动 |
| Trace 空态只有"暂无追踪事件" | Playground P-10 | 引导态：插画 + 「打开 Trace + 立即渲染」按钮（empty → onboarding） |
| 搜索无结果时只显示空列表 | Learn L-02 隐含 | "没有匹配的示例，试试 *script* 或 *if*" 推荐词 |
| 历史为空时整个区块隐藏 | Try T-06 OK；但 Playground「我的预设」永远为空态 | 空态放 "保存当前预设到我的预设" CTA |

### 1.3 主题与个性化（P1）

- **暗色主题未上线**：仓库 `usePreference.theme` 已持久化但 UI 无入口；`.code-block` 背景硬编码 `#f5f5f5` 暗色刺眼。Element Plus 暗色变量已就绪，补 `el-switch` + 切换动画。
- **品牌主题未启用**：`usePreference.brand='mchuan'` 字段存在但 UI 无入口，且组件未消费。多品牌租户预留接口，对应 Element Plus 主题变量。
- **字号/字重**对标 MDN：正文 `font-size: 14px`、行高 `1.7`、代码块 `13-14px`；当前 Docs markdown pre 用 `<pre>` 直接渲染，字号偏小且无高亮。

### 1.4 可访问性 a11y（P1，成熟平台 CI 卡口项）

- 图标按钮补 `aria-label`（▶ Render、📁 加载文件、语言切换 🌐、GitHub ↗）
- 状态不只靠颜色：数据非法的 `✗ 无效` 已有符号 ✓；Trace 行类型用背景色区分，应加左侧色条 + 图标
- 焦点管理：卡片展开、tab 切换支持方向键；弹层关闭后焦点返还触发钮
- `axe DevTools` 跑基线，"严重违规 = 0"纳入 CI（与 002 轮 i18n 卡口同等级）
- 暗色主题对比度 ≥ 4.5:1

---

## 二、功能合理化

### 2.1 Playground（核心，投入产出比最高）

| 现状 | 003 证据 | 成熟平台做法 | 建议 |
| --- | --- | --- | --- |
| 「保存预设」无入口 | P-14：Ctrl+S 后仅"已保存到浏览器"，用户存了就丢 | RunKit/StackBlitz/Replit 有"我的代码片段"列表 | 预设下拉分两组：内置预设 / 我的预设；后者支持重命名、删除、复制 ID（类似 GitHub Gist） |
| 输出区只有"结果/追踪/原始" | P-01/P-10/P-12 | GraphQL Playground：结果 / Schema / History / Docs | 输出区操作扩展：①复制结果 ②下载 .json/.txt ③"在 Try 中打开"（带 trace）④错误时定位模板行号 ⑤模板/结果字段映射 diff（教学价值极高，契合 Learn 场景） |
| 模板/数据/输出三栏比例写死 | P-01 默认 1:1:1 | TypeScript Playground 可拖拽改变栏宽 | 给 .pg-pane 加 `resizable`（Element Plus 不带，集成 `vue-resizable` 或自实现 ~30 行），状态写入 localStorage |
| 无键盘导航可见性 | P-04/P-05 | VSCode/WebStorm 级 | Cmd/Ctrl+Shift+P 命令面板（运行/格式化/保存/分享/复制结果/切换 Tab/切换类型），大幅缩短熟练用户操作路径 |
| Trace 事件无聚合视图 | P-10 | Chrome DevTools Performance | 加"按类型聚合"折叠 + 耗时 Top-N 排序 |
| 「文件加载」按钮文案冷 | 「加载文件」+「或拖拽 JSON / YAML 文件到此」 | Vercel：清晰的上传 dropzone 图标 | 加大 dropzone 占位（折叠状态）、加最近加载历史；5MB 限制在大文件时显式提示而非 ElMessage.error |

### 2.2 信息架构收敛（P1）

**当前**：8 个主入口（Home / Playground / Showcase / Perf / MCP / Learn / Try / Docs）——Try 与 Playground 重叠、Docs 与 Learn 重叠。

**大厂做法**（Linear/Stripe Docs/react.dev）：
- 主导航 ≤ 5 个核心入口 + 「更多」溢出
- Try 作为 Playground 的"极简模式"子页面（/playground?mode=try，预填示例）
- Docs 定位为"API 参考 + 语法速查"，与 Learn（教程）区分
- MCP/Perf 收进文档区或页脚

**建议新 IA**（按用户旅程排序，学习路径前置）：
```
Home → Learn → Playground → Showcase → Docs → (MCP/Perf 在 Docs「工具与生态」下拉)
```

### 2.3 Learn 学习中心（P1）

- **学习进度**：36 个示例加"已读/已运行"打点，侧栏进度环（react.dev tutorial 标配）
- **难度筛选**：已有 difficulty 字段（1-3），补侧栏 chip 筛选
- **搜索命中高亮**：关键词 `<mark>` 包住（react.dev/Stripe 标配）
- **全屏运行**：交互示例支持「在 Playground 中打开」整例带入（当前 L-06 仅单 section）
- **目录大纲**：详情页右侧 sticky TOC（react.dev/Stripe Docs 标配）

### 2.4 Showcase / Perf / MCP / Docs（P2）

| 业务线 | 当前问题 | 建议 |
| --- | --- | --- |
| Showcase | S-07 卡片 3/4 显示"无 live 端点"，空点击 | 补静态模拟输出（mock 数据，写死展示结果）；或跳文档锚点说明"该特性通过 X 实现" |
| Perf | 静态基准硬编码；live benchmark 数字易随机器漂移 | API 化基准数据 + 时间戳/环境标注；live 结果支持复制为 Markdown 表格（revenue stats 页常用） |
| MCP | 配置复制按钮 + 步骤引导 OK；tools/list 输出未做 JSON 高亮 | 加 JSON 语法高亮 + 一键 curl 复制（用 `bash <<EOF` 模板） |
| Docs | 与 Learn 内容部分重叠；缺 TOC、缺全文搜索 | 重新定位为「API 参考 + 语法速查」；补 TOC + 全文搜索（@vueuse/integrations + FlexSearch） |

### 2.5 Trust & Security 信号（P1，文档站被低估的能力）

- "生产级"页：当前零。文案强调「BFF 编排能力」「30 行 JSON 替代 200 行 CompletableFuture」但没有 SLA、可观测性、灾备描述
- 顶部放 **版本徽标 + 链接**：v0.5.0 / GitHub stars / 二进制下载量（gh API）
- "⚠️ 安全提示"已存在（MCP 页），扩展为全站 footer 的"Use with care"链接
- GitHub README badges + 静态 status page（status.template4j.io）补足可信度

---

## 三、布局与视觉（对标成熟平台）

### 3.1 导航与 Header（P0）

003 证据：导航中文化 + GitHub ↗ 右上角 ✓。但还差：

```
┌─ 左: Logo  ━ 中: 导航菜单  ━ 右: 搜索 ⌘K | 主题 | 版本 v0.5.0 | GitHub ↗ | 文档
```

- ⌘K 全局命令面板（Stripe Docs/Linear 标配），快捷键 + 模糊搜索
- 版本徽标：点击弹下拉「v0.5.0 · What's new · Changelog」
- 主题切换按钮（暗/亮/跟随系统）
- 当前 768px 以下无折叠策略——补响应式汉堡菜单（XState/Slide-over）

### 3.2 设计 Token 统一（P1）

```scss
/* 当前散落 */
.pg-stat-warn { color: #f59e0b; }
.pg-stat-ok   { color: #10b981; }
.bar-old { background: #ef4444; }
.bar-new { background: #10b981; }
.bar-speedup { background: rgba(255,255,255,0.25); }  /* 与语义无关 */
```

| 改造项 | 建议 |
| --- | --- |
| 颜色 | 全部硬编码颜色 → `--el-color-success/danger/warning/info` + 自定义 `--brand-color` |
| 字体 | 系统 emoji + 等宽 + 衬线/无衬线 4 套 → 收敛为 2 套（Inter var + JetBrains Mono），element-plus 默认字重不适配代码块 |
| 圆角 | 4px / 6px / 8px 三档 → 统一 token（`--radius-sm/md/lg`） |
| 间距 | 4/8/12/16/20/24/32 → 4 的倍数阶梯；Tailwind 默认风格 |
| 阴影 | `box-shadow: 0 0 0 1px rgba(0,0,0,.06)` 等临时写法 → `--shadow-1/2/3` 三档 |

### 3.3 图标与代码块（P1）

- **图标**：当前 emoji（▶/🌐/⚠️/🔀/🛡️）+ Element Plus icons 混用。emoji 在 Win/macOS/Linux 渲染差异大且"玩具感"。**统一用 `@element-plus/icons-vue`**，并在 Storybook/文档中标注每个 icon 的语义来源
- **代码块**：Docs markdown pre 用 `<pre>` 裸展示；Playground CodeEditor 自带高亮；三套表现归一为 `<CodeBlock>` 组件：Shiki/highlight.js + 复制按钮 + 语言标签 + 可选折叠

### 3.4 排版细节（P2）

- 列宽：Docs/Showcase 长文本限 `max-width: 72ch`，行高 1.7（MDN 做法）
- 表格：API 端点列表统一 `el-table`，斑马纹 + hover
- 数字格式：1431 测试 → 1,431；2.06μs → 2.06 µs（千分位 + 真正 unicode）

### 3.5 空状态插画（P2）

- 大量"加载中..."/"暂无数据"文字裸奔
- 引入一套轻量 SVG 插画（unDraw / Storyset 风，免费商用）—— 搜索无结果、错误、404、空白历史

---

## 四、工程与商业化

### 4.1 SEO / 分享元数据（P0，发布即漏的必备能力）

003 现状：index.html 无 og:/twitter: meta、无 canonical、无结构化数据、无 sitemap、无 robots.txt

```html
<!-- 当前 -->
<head>
  <meta charset="UTF-8" />
  <link rel="icon" type="image/svg+xml" href="/vite.svg" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>template4j-site</title>
</head>

<!-- 建议补齐 -->
<title>template4j — JSON template engine & BFF orchestrator</title>
<meta name="description" content="9 components, 5.7x faster, 0 Spring dependency. Declarative JSON templates with Groovy scripts, HTTP orchestration, circuit breaker." />
<link rel="canonical" href="https://template4j.funcommons.org/" />
<meta property="og:title" content="template4j — JSON template engine" />
<meta property="og:description" content="..." />
<meta property="og:image" content="https://template4j.funcommons.org/og-card.png" />
<meta name="twitter:card" content="summary_large_image" />
<script type="application/ld+json">{ "@context": "https://schema.org", "@type": "SoftwareApplication", ... }</script>
```

- `public/robots.txt` + 自动 `sitemap.xml`（基于 Learn 36 例）
- 每页路由级 `useHead()`（@unhead/vue）维护动态 meta
- Open Graph 卡片自动截图（`@playwright/test` + chromium 在 CI 跑）

### 4.2 性能预算与监控（P1）

- **Lighthouse CI** 卡 LCP < 2.5s / CLS < 0.1 / TTI < 3.5s（mobile 4G）
- 引入 Monaco/Shiki 等重依赖前先做 bundle 预算（main < 250KB gz）
- 前端可观测性：Sentry onError 上报；client.ts 已带 X-Trace-Id 全链路，补 UI 展示 + 日志平台查询入口
- 后端：002 修复已上；CI 加一条"Playground Trace → benchmark ≥ 基线 50%"的回归（防退化）
- i18n 健康度卡口（N-06 已巡检，intlify 告警 0）入 CI

### 4.3 组件/单测覆盖率（P1）

- 003 现状：template4j-core 后端有覆盖率门槛 0.95 LINE / 0.85 BRANCH（JaCoCo）
- 前端：仅 `utils/__tests__/learn-helpers.test.ts` 单测，核心组件 `CodeEditor` / `RenderedMarkdown` / `Playground.vue` 零测试
- 建议：Playground/Try/Showcase 单测补齐（Vitest + Vue Test Utils + MSW for API）

### 4.4 部署与分发（P0，商业化前置）

- npm：`@funcommons/template4j-core`（当前仅 Maven，Java 圈外用户无法触达）
- Docker：`docker run -p 8088:8088 funcommons/template4j-site`（README 一行启动）
- Helm chart for k8s（参考 Chart）
- Homebrew / apt 安装 CLI：`brew install lightring/template4j/template4j`
- GitHub Actions：release 触发 + Maven Central publish + Docker Hub push + SBOM + signature
- CLI 体验（`template4j render -t tpl.json -d data.json`），文档站补 CLI tab

### 4.5 文档与社区生态（P1）

- README badges：CI / Coverage / Maven Central / License / Discord
- 中文/英文文档双语化（当前 `.md` 文档几乎全中文，README 也偏中）
- Cookbook / Recipes：缓存 + 熔断 + 并行的组合范式（不只 Showcase 卡片）
- 迁移指南：从 Spring Cloud Function / Apache Camel / CompletableFuture 迁移到 template4j
- Discord / Discussions 入口（GitHub Discussions 已就绪，缺首页入口）

### 4.6 商业化能力（P2）

- 在线 Playground"运行次数"统计 + "已被 X 个项目采用"（Vercel 模板站做法）
- 模板市场（Template Marketplace）：用户上传/下载模板（GitHub Gist 桥接）
- "Deploy to Vercel/Netlify/Cloudflare" 一键按钮（模板应用）
- 企业版：审计日志、SOC2、SSO（仓库已有 `withInputSchema`/`withOutputSchema`，缺包装层）

---

## 五、落地路线图建议

| 阶段 | 优先级 | 内容 | 目标 |
| --- | --- | --- | --- |
| 第 1 周 | P0 | Playwright 回归套件进 CI（10 BL/75 用例，PR 门禁跑冒烟子集，夜间全量）；修复后代码防 DEF-11 退化用例 | 防回归 + 量化可信度 |
| 第 1-2 周 | P0 | SEO meta + 截图 + sitemap + robots；版本徽标；⌘K 命令面板；响应式 768/375 | 商业化前置 + 移动端 |
| 第 2-3 周 | P1 | 设计 Token 统一；暗色主题；统一 CodeBlock；⌘K 全功能；Playground 栏宽可拖；Share popover；Undo Toast | 对标成熟平台基本盘 |
| 第 3 周 | P1 | Try/Playground IA 收敛；Learn 进度 + TOC + 搜索高亮；Docs 重新定位为 API 参考 + 全文搜索 | 信息架构升级 |
| 第 4 周 | P2 | npm / Docker / Helm / Homebrew 分发；Storybook；axe CI；Lighthouse CI；Open Graph 自动截图 | 商业化与可观测性 |
| 第 4 周+ | P2 | 模板市场；CLI；社区/迁移文档；企业版占位 | 生态 |

---

## 六、对标参考（成熟平台做法锚点）

| 维度 | 对标 | 直接借鉴点 |
| --- | --- | --- |
| 全局 ⌘K | Stripe Docs / Linear / Vercel | 模糊搜索 + 行动项 + 最近访问 |
| 命令面板 | VSCode / WebStorm | Cmd/Ctrl+Shift+P |
| 错误面板 | Chrome DevTools / Sentry | 人话结论 + 折叠 stack + 定位 |
| 暗色主题 | Vercel / Linear | 设计 token + 系统跟随 |
| Share | GitHub / Vercel Templates | popover + 二维码 + 链接 |
| 文档 TOC | react.dev / MDN | 右侧 sticky + scroll-spy |
| 进度打点 | react.dev Tutorial | 侧栏进度环 + 已完成态 |
| 加载与空态 | Linear | skeleton + 引导型空态 |
| 设计 token | Stripe / Vercel | 双品牌 + 三套响应式 |
| 性能预算 | Vercel / Cloudflare | Lighthouse CI + bundle 预算 |
| 多语言 | Stripe / MDN | 完整 i18n + 路由级 meta |
| 可观测性 | Datadog / Sentry | Trace ID 全链路 + UI 展示 |

---

*本建议书基于 2026-09-20 三轮回归（001 发现/002 修复/003 复测）的实测证据、产品截图与代码走读产出；013 缺陷已全部修复并稳定复现，本建议书聚焦"修复后对标成熟平台还差什么"。所有修复证据与本轮 75/75 通过截图见 [README.md](./README.md) 与各 `bl-*/shots/`。*