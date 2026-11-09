# Changelog

template4j 项目的所有显著变更记录。格式基于 [Keep a Changelog](https://keepachangelog.com/)。

---

## [0.5.0] - 2026-07-14

### Version

- `0.4.0` → `0.5.0-SNAPSHOT` (parent + core + web + demo)。


### Added (组件 — Tier 1 编排)

- **`parallel` 组件** (`ParallelRenderer`) — 并发执行多个子模板, 聚合结果供 `body` 渲染。专为 BFF/API 聚合场景。
  - `tasks` 任务映射 + `body` 聚合模板 + `timeout` (整体 deadline, 默认 30s) + `continueOnError` 容忍单任务失败 + `error` 分支。
  - 共享 daemon `FixedThreadPool` (默认 16, 系统属性 `template4j.parallel.poolSize` 可调)。
  - 嵌套 parallel 不死锁: 超出 pool size 的任务排队等待。
- **`try` 组件** (`TryRenderer`) — 错误隔离与降级。
  - `select` + 可选 `fallback` / `onError` / `rethrow`。先执行 onError 副作用, 再返回 fallback。
  - `$error` 变量提供 message/cause/path, 不暴露 original Throwable。
- **`rest` 增强子属性** — 生产编排能力。
  - `timeout` (ms, 默认 30000)
  - `retry: { count, backoff: fixed|linear|exponential, baseMs, retryOn: 5xx|network|all }`
  - `cache: { key, ttl }` — 进程内有界 LRU (默认 1024, 系统属性 `template4j.rest.cache.maxEntries`), 命中返回 defensive deep copy 避免并发 mutate 污染。
  - `auth: { type: bearer|basic, token | user+pass }` — 自动注入 Authorization header。
  - `circuitBreaker: { threshold, resetSec }` — CLOSED/OPEN/HALF_OPEN 三态机。

### Added (Tier 2 - 工程化)

- **`compile` API** — 显式预编译 + 内部 parseStrTemplate LRU 缓存。性能 5.7x。
- **`withTraceConsumer`** — RENDER_STRING / COMPONENT / REST / SCRIPT 事件。零开销默认。
- **`withInputSchema` / `withOutputSchema`** — JSON Schema 校验 (networknt)。fail-fast。
- **CLI** — `java -jar template4j-core-shaded.jar render/watch/mcp/version/help` 命令行入口。
- **MCP Server** — stdio JSON-RPC, 暴露 `render_template` 工具给 AI Agent (Claude Desktop / Cursor)。
- **`template4j-site` 演示站** — 7 个 demo 页面: home / learn / try / docs / show-case。

### Changed (依赖)

- **新增 `commons-text:1.12.0`** — 用于 `TemplateUtil.parseStrTemplate`, 把 6 种占位符扫描委托给 `StringSubstitutor` (escape 设为 `\0` 禁用)。
- **新增 `json-schema-validator:1.5.6`** — networknt 的 schema 校验。
- **删除 `URegex` 工具类** — `parseStrTemplate` 改用 StringSubstitutor, `codeReplaceAt` 改为状态机, `convertVars` 改为 char-scan, URegex 已无生产调用者。`UString.relativeUrl` 内联 `Pattern`。
- **`RenderRenderer` / `RestRenderer` 不再依赖 Spring** — `RestRenderer` 改用 JDK `java.net.http.HttpClient`; `template4j-core` 完全去 Spring (运行时零 Spring 依赖)。

### Fixed (P1, 累计 11 个)

- ParallelRenderer `timeout` 改为基于 deadline 的整体超时 (原 per-future 实现总等待可能 > timeout)。
- TryRenderer 不再暴露 `original` Throwable (避免堆栈/类路径泄露)。
- manual/06 rest `error` 模板示例从错误的 `{@status}` 修正为 `{$responseData.status}`。
- backoff 公式说明从模糊的 `attempt` 改为"第 N 次重试(1-based)", 与代码一致。
- 全仓库文档同步: 9 个内置组件 (含 parallel/try); spring-web / RestTemplate 残留在 manual/README/CLAUDE.md/10-best-practices/11-grammar-reference 中清零。
- **新增 (Round 2-3)**: CACHE 有界 LRU (默认 1024 条)。
- **新增 (Round 2-3)**: cache 命中时 defensive deep copy 避免并发 mutate 污染。
- **新增 (Round 2-3)**: parallel `$error` 字段补全 path。
- **新增 (Round 4-5)**: RestRenderer / Cli / McpServer 重复 import 与 dead code 清理 (`LinkedHashMap`/`Map`/`ConcurrentHashMap` 各 import 两次)。
- **新增 (Round 4-5)**: JDK 17 兼容性 — 移除 Java 21-only API (`List::getFirst`, `HttpClient.close()` in try-with-resources),`maven-enforcer-plugin` 锁 `[17,18)`。

### Tests

- **新增 102 个测试**: ParallelRendererTest (7) + TryRendererTest (7) + RestRendererEnhancedTest (8) + RestRendererEnhancedTemplateTest (3) + ParallelRestIntegrationTest (4) + ParallelNestedStressTest (9) + ComponentHelpersCoverageTest (10) + ComponentRemainingCoverageTest (8) + TryRendererRemainingCoverageTest (5) + AdversarialInputTest (11) + TemplateCompileCacheTest (5) + TraceConsumerTest (8) + SchemaValidationTest (8) + CircuitBreakerTest (4) + CliTest (9) + McpServerTest (9) + ParallelOverallDeadlineTest (1) -1 dup。
- **第二轮覆盖率改进 (Round 3-5) 新增 ~80 个测试**: DtoRecordsCoverageTest (11) + TraceIdFilterCoverageTest (5) + GlobalExceptionHandlerIT (3) + TemplateServiceCoverageTest (14) + ExampleServiceCoverageTest (20) + TryApiIT (8) + ParallelControllerCoverageTest (2) + TryRendererExtraCoverageTest (5) + ScriptUtilHelpersTest (24) + RestRendererCoverageTest (30) + CliTest alias coverage (6)。
- **第三轮覆盖率改进 (Round 6+) 新增 ~50 个测试**: ExampleServiceParseWebDocTest (6) + ParallelRendererExtraCoverageTest (10) + McpServerTest (4) + RestRendererCoverageTest (1) + CliTest printVersion coverage (8) + GlobalExceptionHandlerIT (1)。
- 总测试数: 1244 → **1488 (core) + 114 (site) = 1602**。
- 全模块 `mvn verify` 全绿 (parent / core / site)。
- benchmark: simple 模板 warm 路径 **2,060 ns → 360 ns (5.7x)**。
- JaCoCo 阈值: 0.95/0.85 → **0.97 (LINE) / 0.93 (BRANCH)** core, 0.85/0.70 → **0.92 / 0.87** site。当前实测 core **LINE 97.01% / BRANCH 93.07%**, site **LINE 92.86% / BRANCH 88.10%**。
- 显著改进: McpServer 67.4%→84.8% BR, Cli 73.5%→85.5% BR, RestRenderer 80.4%→84.7% BR, TryRenderer 82.1%→85.7% BR, ExampleService 70.5%→83.0% BR, TemplateService 82.4%→88.2% BR, ParallelRenderer 86.4%→88.6% BR。

### Changed (Site — Playground 优化)

- **Playground.vue 重写** (`template4j-site/frontend/src/views/Playground.vue`):
  - 状态栏: 实时显示 Renders 计数、Last duration、unsaved/saved 指示、快捷键提示。
  - Snippet 栏: 8 个一键插入按钮 (`{@path}` / `{$var}` / `{=expr}` / `{% %}` / if / each / rest / try)。
  - Tabbed output: Output / Trace / Raw 三视图切换;Trace 行按 REST/COMPONENT/SCRIPT/RENDER_STRING 加颜色区分。
  - Format JSON 按钮: 一键格式化 data 文本框(JSON.parse → JSON.stringify(..., 2))。
  - Clear 按钮: 重置所有状态。
  - 实时 data 校验: 顶部标题栏显示 ✓ valid / ✗ error,文本框下显示详细错误。
  - 快捷键: Ctrl+Enter / Cmd+Enter 触发 render;Ctrl+S 保存到 localStorage;Tab 缩进 2 空格。
  - 自动渲染: 600ms debounce,只在 data valid 且 template 非空时触发。
  - 新 preset: "Parallel mock" 展示 `@class:parallel` 用法。
  - **i18n** (zh-CN / en-US): 新加 30+ 个 playground.* 翻译键,状态栏/snippet/Format/Clear 等全部本地化。
  - **拖拽文件**: Data 面板加 dropzone + "加载文件"按钮,支持拖拽 `.json` / `.yaml` 文件直接填入 Data 文本框(带内嵌的极简 YAML → JSON 转换)。
- **Cli.printVersion 重构**: 拆出 `readVersionFromJarManifest(URL)` 和 `readVersionFromPackage()` 两个可测试的 static helper,8 个新单测覆盖 null URL / 坏 URL / 临时 jar(读 Implementation-Version)/ 目录 URL 等场景。
- **新增 e2e**: `e2e/e2e-playground.mjs` — Playwright 流程: 加载 → 自动渲染 → Format → Trace → 快捷键 → Clear → 拖拽文件 → CodeEditor 高亮,8 张截图。
- **新增 CodeEditor 组件** (`template4j-site/frontend/src/components/CodeEditor.vue`): 轻量级 Monaco 替代品,无外部依赖(避免 +2MB Monaco)。特性:
  - 透明 textarea + 绝对定位 `<pre>` 覆盖层,高亮透明文字 + 覆盖层彩色显示。
  - 语法高亮: JSON key/string/number/bool,以及 template4j 占位符 `{@x}` / `{$x}` / `{= x }` / `{% x %}` 用紫色背景。
  - 占位符自动补全: 输入 `{` 后弹出候选(`{@path}` / `{$var}` / `{= expr}` 等),Tab/Enter 补全,Esc 取消。
  - 滚动同步(overlay 跟随 textarea 滚动)。
  - 焦点高亮、Tab 缩进 2 空格。
- **新增 Tier 3 流式渲染**:
  - `RenderStream` 类 (`template4j-core/src/main/java/fun/commons/template4j/core/RenderStream.java`): 包装 `RenderEngine.render()` 为可订阅事件流。两种使用模式:
    - 拉模式: `awaitAll()` / `pollAll(timeoutMs)` 阻塞等结果。
    - 推模式: `subscribe(onNext, onError, onComplete)` 实时接收 trace 事件。
    - 终态: 最后一个事件是 `RESULT_TYPE` 携带渲染结果,失败是 `ERROR_TYPE` 携带错误。
  - `RenderEngine.renderStream()` 默认方法: 便捷创建 RenderStream。
  - 10 个单测覆盖拉/推模式、错误处理、幂等、null handlers 等。
  - `StreamController` SSE 端点 (`/api/try/stream`): `text/event-stream` 输出,边渲染边推 trace 事件。Vue Playground 用 `fetch().getReader()` 接收。
  - 2 个 StreamControllerTest 验证 hello/done/error 事件格式。

### Tests

- 总测试数: 1488 → **1510 (core) + 117 (site) = 1627**。
- 新增 ~24 个测试: RenderStreamTest (10) + StreamControllerTest (3) + 旧的 CoverageTest 累计。

---

## [0.4.0] - 2026-07-05

### Changed (依赖清理)

- **弃用 utils4j 依赖** — 之前 `template4j-core` 通过 `fun.commons:utils4j:0.1.0` 引入 `fun.commons.framework.utils.*`，但代码仓库里同时存在一份本地拷贝 `fun.commons.template4j.utils.*`（UCode/UJson/URegex/UString/UThrow/UType），与 utils4j 同名同方法。
  - **删除**：根 `pom.xml` 的 `<utils4j.version>` 和 `<dependency>utils4j</dependency>`，`template4j-core/pom.xml` 的 utils4j 依赖。
  - **迁移**：所有 `import fun.commons.framework.utils.*` → `import fun.commons.template4j.utils.*`，涉及 16 个源文件（main + test）。
  - **清理**：删除死代码 `UCode.java`（与 utils4j 同名，0 处引用）。
  - **覆盖率门槛**：JaCoCo excludes 移除 utils 路径（因为现在 utils 是活跃代码）。

### Added (测试与文档)

- **新增 4 个测试类 / ~110 个测试方法**（`template4j-core/src/test/java/...`）：
  - `UJsonTest`（56）— 本地 UJson 全方法：JSON/YAML 解析、smartCast 各 type、findMap 系列、mergeJson 各种组合、copy 系列。
  - `URegexTest`（15）— extractStr/extractList/extractGroups/extractGroupsList/replaceBy/replaceSubstring 各分支。
  - `UStringTest`（7）— isBesieged/extractBesieged/relativeUrl 各类输入。
  - `UTypeMoreTest`（25）— isTrue/isFalse/testBoolean 各类型 + castValue 转 JSON/JSONObject/JSONArray + loadClass 已知/未知。
  - `LoaderMoreTest`（9）— LoaderUtil 各种 extension（json/json5/jt/japi/yml/yaml/yt/yapi）+ resourceType 覆盖 + DefaultTemplateLoader null 路径。
  - `SelfObjectMoreTest`（5）— getRoot()/path() 各分支 + getResourceUrl 继承。
  - `JsonRendererEdgeTest`（11）— render(null/空 Map/空 Array/标量) + star key merge + RestRenderer 错误块。
  - `RenderEngineApiExceptionsTest`（6）— withLoader/withImport/withBean null/重复异常。
  - `MoreCoverageTest`（已扩展到 31 个）— 加 `testSimplifyValueDollar/testSimplifyValueDollarWithType/testConvertVarsDoubleAt/testConvertVarsSingleAt`。

### Coverage

| 维度 | 0.3.0 终 | 0.4.0 中 | 0.4.0 终 (100% push) | Δ 总 |
| :--- | :---: | :---: | :---: | :---: |
| INSTRUCTION | 89.0% | - | - | - |
| BRANCH | 79.2% | 85.1% | **89.95%** | **+10.75** |
| LINE | 90.7% | 93.6% | **96.29%** | **+5.59** |
| METHOD | 74.4% | - | - | - |
| 测试总数 | 334 | 510 | **616** | **+282** |

#### 0.4.0-100% push 新增测试文件 (106 个测试)：

- `RestRendererServerTest`（4）— 用 JDK `HttpServer` mock 真实 2xx/4xx/5xx 响应
- `MoreCoverageTest`（已扩展至 34）— 加 `testSwitchDecimalStringMatch`/`testInvokeWithNullSelect`/`testInvokeWithArgsNotCollection`
- `RenderContextRenderOverloadsTest`（4）— 覆盖 RenderContext.render 的 4 个重载
- `RenderExceptionTest`（5）— removeUnused 各分支
- `RenderBindingsTest`（7）— RenderBindings 的 get/containsKey 各分支
- `SelfObjectUnitTest`（7）— 直接构造 SelfObject 覆盖 getRoot/path 各分支
- `JsonRendererEdgeTest`（已扩展至 32）— convertSelf 边界 + RestRenderer 错误路径
- `RenderEngineApiExceptionsTest`（已扩展至 16）— withLoader/withImport/withBean 异常 + render(Object,Object) 路径
- `UTypeMoreTest`（已扩展至 30）— castValue 全部 String/JSONObject/JSONArray 分支
- `UStringTest`（已扩展至 11）— relativeUrl 各分支
- `EachRendererExtraTest`（10）— EachRenderer Map 分支 + Array 分支
- `RenderRendererTest`（3）— RenderRenderer 各 data 分支
- `ExpressionValueTest`（10）— JsonRenderer.expressionValue 各分支
- `LoaderMoreTest`（已扩展至 11）— loadJson/loadYaml default 方法
- `UJsonUnsupportedTypeTest`（1）— smartCast 不支持的 type
- `DefaultTemplateLoaderCoverageTest`（1）— getDefaultClassLoader 反射
- `FuncVarsPutAtTest`（1）— vars.putAt 分支
- `TemplateUtilCoverageTest`（7）— convertVars 各 special key 分支

#### 仍未覆盖的关键分支（说明）：

1. `DefaultTemplateLoader.getDefaultClassLoader` — 3 层 classloader fallback（Thread.context → class.classLoader → system），需要 mock Thread，难度高。
2. `RestRenderer` 的某些非典型路径（如非常规 Content-Type）。
3. 各 utility 类的隐式 `private` 构造函数 — 无调用，永远不会覆盖。
4. `$xxx()#` 形式 — 触发 recursive guard，安全阻止无限递归。
5. `convertVars.convertVars` 中 `UThrow.assertArgument(true, ...)` 的死分支 — 代码 bug 而非测试缺失。

### Removed (依赖)

- `fun.commons:utils4j:0.1.0` — 本地 utils 覆盖全部用法，无需外部依赖。
- `commons-codec:commons-codec` — 原仅 UCode 使用（UCode 已删除）。

### Version

- `0.3.0` → `0.4.0`。

---

## [0.3.0] - 2026-07-04

### Added

- **`@..x` JSONPath deepScan 支持** — `ScriptUtil.codeReplaceAt` 新增 rewrite 规则：把 `@..x` 改写为 `data("..x")` 函数调用形式，避开 Groovy 把 `..` 当 spread 算子导致的 `ClassCastException`。
  - 修复前：`{@..name}` → Groovy 抛 `ClassCastException`
  - 修复后：`{@..name}` → `data("..name")` → JSONPath eval 返回 deepScan 结果
  - 测试：`SelfObjectAndScriptUtilTest.testAtDeepScan`
- **`testAtDeepScanWithQuotes`** — 同时验证 `@'..x'`（带引号）也继续可用。
- **`ExtraCoverageTest`（23 个新测试）** — 补 IfRenderer 11 个 is 分支全覆盖、RenderContext 3 个 renderField 重载、DefaultTemplateLoader 异常路径、SelfObject putVarDefine / putVars 边界。
- **CHANGELOG.md / manual/02-syntax.md** 移除"待实现"标记，更新为"✅ 已支持"。

### Changed

- **`ScriptUtil.codeReplaceAt`**：
  - 新增第一条 regex `@\.\.(...)` 处理 deepScan（必须先于单点 regex）
  - 单点 regex `(@)[\(\.\[]` 加 negative lookahead `(?!\.)`，避免误匹配 `@..` 中的第一个 `.`
- **覆盖率门槛**（pom.xml jacoco config）：LINE ≥ 80% / BRANCH ≥ 70% → **LINE ≥ 90% / BRANCH ≥ 75%**
- **版本号** 0.2.0 → 0.3.0。

### Coverage

| 维度 | 0.2.0 | 0.3.0 起点 | 0.3.0 终 |
| :--- | :---: | :---: | :---: |
| INSTRUCTION | 85.4% | 86.6% | **89.0%** |
| BRANCH | 73.6% | 75.8% | **79.2%** |
| LINE | 88.4% | 89.3% | **90.7%** |
| METHOD | 69.3% | 71.4% | **74.4%** |
| 测试总数 | 309 | 311 | **334** |

---

## [0.2.0] - 2026-07-04

### Fixed (业务 bug 修复)

- **`TemplateLoader.loadYaml` 默认实现错参数** — 之前 `default Object loadYaml(String url)` 直接把 `url` 传给 YAML 解析器，导致返回的 `UJson.JSONFromYaml(url)` 把 URL 当文本解析。改为 `UJson.JSONFromYaml(load(url))`。
- **`RestRenderer` 5xx 状态码处理错误** — 之前用 `new HttpClientErrorException(status, body)` 抛 5xx，构造器内部抛 `IllegalArgumentException`（HttpClientErrorException 只接受 4xx）。改为按状态码分支：`4xx` 抛 `HttpClientErrorException`，`5xx` 抛 `HttpServerErrorException`，catch 块也对应改为识别 `HttpStatusCodeException` 父类。
- **`RestRenderer` error 块 message 字段内容错** — 之前 `ex.getMessage()` 返回 Spring 的长前缀（"500 Internal Server Error on GET request..."），改为 `ex.getResponseBodyAsString()` 返回原始响应体。
- **`FuncLoad` 不使用引擎的 `templateLoader`** — 之前脚本内 `load('./x.json')` 总是走 `new DefaultTemplateLoader()`（classpath 查找），忽略 `engine.withLoader(...)` 配置。改为从 `self.renderer.getTemplateLoader()` 拿到当前引擎的 loader。顺带把 `JsonRenderer` 改为 `public` 并加 `getTemplateLoader()`。
- **`TemplateUtil.convertKey` 不识别 `key#` 后缀** — 文档（`template-comment.yml`）承诺 `"abc#": "{@name}"` 是原样取值，代码只识别 `%` / `&`。补 `#` 分支。
- **`TemplateUtil.convertVars` 不识别 `$xxx#` 后缀** — 同上，文档承诺 `"$abc#": "{@name}"` 是变量原样，代码漏处理。补 `#` 分支。
- **`TemplateUtil.convertVars` 不识别 `@@` 局部数据** — 文档（`template-data.yml`）承诺 `"@@": {...}` 是字面局部数据（不走模板渲染），代码只处理 `@` / `$` / `$$`。补 `@@` 分支。
- **`TemplateUtil.parseStrTemplate` + `JsonRenderer.expressionValue` 不识别 `{#…#}` escape** — 文档（`template-comment.yml`）承诺 `{#不要渲染：{@name}#}` 原样不渲染，代码只匹配 `<&…&>`。在 `parseStrTemplate` 加 `{#…#}` 提取，在 `expressionValue` 加 `{#…#}` 字面返回分支。

### Changed

- **Maven 依赖清理** — 移除 `mysql:mysql-connector-java`、`com.alibaba:druid`、`com.fasterxml.jackson.core:jackson-core`（皆为引入但无 import），删除对应 `<properties>` 中的 `mysql.version` / `druid.version`。
- **测试弱断言加强** — `ComponentRendererTest` 中 3 处 `assertNotNull(...)` 改为具体值断言（`testSwitchRendererBasic` / `testSwitchRendererReturnObject` / `testSwitchRendererNumberMatch`）。
- **构建加 JaCoCo 覆盖率门槛** — `pom.xml` 中 `jacoco-maven-plugin:0.8.11` 加 `check` 目标，门槛：LINE ≥ 80%、BRANCH ≥ 70%（排除 `GroovyImports`，因依赖运行时类加载覆盖不稳定）。`mvn verify` 会在覆盖不足时 fail。

### Added (测试与文档)

- **新增 9 个测试类 / ~180 个测试方法**（`template4j-core/src/test/java/...`）：
  - `RestRendererTest` — HTTP 出站全分支（method / dataType / success / error）
  - `LoaderUtilTest` — 资源加载 + `&include` 字符串 / 数组 / 嵌套
  - `FuncFneTest` / `FuncCastTest` / `FuncLoadTest` / `FuncRenderAndVarsTest` — 6 个 Func* 全覆盖
  - `RenderEngineOverloadTest` — 12 个 renderJson / renderYaml / renderUrl 重载
  - `EscapeKeyAndTemplateUtilTest` — escape / simplify / convertKey/convertVars
  - `DocumentedFeatureTest` — `documents/*.yml` 中各特性的最小可运行示例
  - `SelfObjectAndScriptUtilTest` — 变量作用域 / 递归守卫 / `@` 重写边界
  - `TemplateLoaderTest` — 自定义 loader 路径
- **用户手册** `manual/` 12 章（quick-start、syntax、data-and-vars、scripting、functions、components、merge-and-include、resource-loading、custom-component、best-practices、grammar-reference、analysis、coverage-plan、fix-report）。
- **`CHANGELOG.md`**（本文件）。

### Coverage

| 维度 | 0.1.0 | 0.2.0 | Δ |
| :--- | :---: | :---: | :---: |
| INSTRUCTION | 59.2% | 85.4% | **+26.2** |
| BRANCH | 48.0% | 73.6% | **+25.6** |
| LINE | 62.9% | 88.4% | **+25.5** |
| METHOD | 45.0% | 69.3% | **+24.3** |

---

## [0.1.0] - 2026-07-04 (initial)

- 多模块 Maven 项目搭建（`template4j-parent` / `template4j-core` / `template4j-web`）
- JSON / YAML 模板引擎核心（`JsonRenderer` + `RenderEngine` 接口）
- 7 个内置组件：`if` / `each` / `switch` / `invoke` / `render` / `const` / `rest`
- 6 个 Func* 工具（`data` / `vars` / `render` / `load` / `cast` / `fne`）
- 5 种占位符：`{@}` / `{$}` / `{=}` / `{% %}` / `<& &>`
- 9 类特殊键：`#` 注释 / `key%` 脚本后缀 / `key&` 原样后缀 / `@` 局部数据 / `$$` 预渲染变量 / `$name` / `$name()` / `$enter{}` / `$return{}`
- 资源加载：classpath / 自定义 `TemplateLoader` / 格式推断 / `&include` 嵌套
- 现有文档：`template4j-web/src/main/resources/docs/documents/*.yml` 40+ 文件
- 测试基线：118 个，0 失败
