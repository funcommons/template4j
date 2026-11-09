# 优化方案 · 第二轮（性能 / 安全 / 结构）

> 日期：2026-11-09。基于第一轮 14 项（S1-S6/P1-P4/A1-A6）全部落地、1775 测试全绿之后的代码现状。
> 每项均给出**代码证据**、**方案（三方开源优先）**、**验收标准**。未动代码，仅提案。
> 第一轮遗留 TODO 的兑现项：A3、S2。

## 优先级总览

| # | 类别 | 一句话 | 严重度 | 工作量 | 建议 |
| --- | --- | --- | --- | --- | --- |
| A1 | 结构 | 全部改动落盘提交 + PR 门禁合入 | 流程风险 | 0.5d | **立即做** |
| S1 | 安全 | RateLimitFilter：XFF 可伪造绕过 + bucket 表无界增长 | **高**（真实缺陷） | 0.5d | **立即做** |
| P1 | 性能 | 沙箱脚本无缓存，每 eval 重编译 | 中 | 1d | 第二顺位 |
| S4 | 安全 | security-scan 不在 PR 门禁上，PR 依赖无扫描 | 中 | 0.5d | 随 A1 |
| A2 | 结构 | CI 缺 frontend / e2e job | 中 | 1d | 随 A1 |
| P2 | 性能 | TraceEvent 无 consumer 时仍无条件分配 | 低 | 0.5d | 批量做 |
| A3 | 结构 | micrometer `script.cache.size` 假数据（遗留 TODO） | 低 | 0.5d | 批量做 |
| S2 | 安全 | 沙箱默认导入 `java.io/net` 与白名单矛盾（遗留 TODO） | 低 | 0.5d | 批量做 |
| S3 | 安全 | rest 响应数据含 `@class` 是否可被二次渲染——审计钉死 | 低（审计项） | 0.5d | 批量做 |
| P3 | 性能 | 超时渲染每调用 new Thread | 低 | 0.5d | 批量做 |
| P4 | 性能 | renderString 多占位符逐个 replace O(n²) | 低 | 1d | 观察 |
| A4 | 结构 | singleReturn 类型不稳定语义 | 观察项 | — | 不动，记录 |

---

## S1 · RateLimitFilter 两处真实缺陷（本轮最重要发现）

**证据**：`template4j-site/src/main/java/fun/commons/template4j/site/config/RateLimitFilter.java:79-88` 与 `:41`

1. **XFF 可伪造 → 限流整体失效**。`clientKey()` 无条件信任 `X-Forwarded-For` 首值。站点直连部署（无反向代理，或代理未清洗 XFF）时，攻击者每次请求换一个 XFF 值即可拿到无限个独立桶，限流形同虚设。
2. **`buckets` 无界增长 → 内存 DoS**。`ConcurrentHashMap<String, Bucket>`（:41）按 clientKey 永久驻留；配合上面的 XFF 喷射，可在数分钟内制造百万级桶条目撑爆堆。

**方案（三方优先，Caffeine 已在依赖树中）**：

- 新增配置 `template4j.demo.rate-limit.trust-proxy`（默认 **false**）：
  - `false` → clientKey 一律 `remoteAddr`（XFF 仅在 `trust-proxy=true` 且 remoteAddr 命中 `trusted-proxies` CIDR 列表时才取）。
  - CIDR 匹配用 `commons-ip-math`（com.github.seancfoley:ipaddress 5.5.1，纯 JDK、无传递依赖）或手写 IPv4 前缀匹配（零依赖，demo 规模足够）。
- `buckets` 换 **Caffeine**：`maximumSize(100_000) + expireAfterAccess(30min)`，一行配置消灭无界增长。

**验收**：伪造 XFF 直连时仍被限流（429）；喷射 10 万个不同 XFF 后 bucket 表条目数有上限；`RateLimitFilterTest` 增补 4 个用例。

---

## P1 · 沙箱路径脚本零缓存（每 eval 重编译）

**证据**：`ScriptEnvironment.java:73-77` —— 沙箱分支 `sandbox.eval(imports.toCode() + script, ...)` 直接走 `GroovyShell`，无任何缓存；非沙箱分支已有 Caffeine `CompiledScript` 缓存。CLAUDE.md 也明确记录"sandbox path recompiles per eval"。

**影响**：`withSandbox(true)` 后每条 `{= }` / `{% %}` 都做一次完整 AST 解析 + SandboxTransformer 重写 + 编译。循环模板（each × N 行 × M 脚本）下沙箱吞吐比非沙箱低一个数量级——**安全开关变成性能悬崖**，生产不敢开。

**方案**：与非沙箱同构——Caffeine 缓存编译产物，key = `importsVersion + "\0" + script`。groovy-sandbox 的 `SandboxTransformer` 是**编译期** AST 重写（运行期才查拦截器），转换+编译结果可以安全复用：`GroovyShell.parse` 得到 `Script` 后，每线程 `script.newInstance(bindings)` 克隆执行（Groovy `Script` 非线程安全但可反复 newInstance）。属性开关沿用 `template4j.scriptCache.*`。

**验收**：同一脚本循环渲染 1000 次，沙箱开启后耗时 ≤ 沙箱关闭的 1.5x（当前实测差距更大）；`SandboxRenderTest` 增加"缓存命中结果一致 + imports 变更后失效"用例。

---

## S4 · dependency 扫描不在 PR 门禁上

**证据**：`.github/workflows/security-scan.yml` 触发器只有 `push: main` + 每周 schedule + 手动。PR 引入高危依赖要**合入之后**才被发现。

**方案（三方/官方优先）**：PR 卡口用 GitHub 官方 OSS 的 **`actions/dependency-review-action`**（diff 视角，只查本次 PR 新增依赖的漏洞/许可证，秒级完成，无 NVD 冷启动问题）；全量 dependency-check/Trivy 留在 push main + 每周做基线。

```yaml
# build.yml 新增或 security-scan.yml 增加 pull_request 触发
- uses: actions/dependency-review-action@v4
  with:
    fail-on-severity: high
    deny-licenses: GPL-2.0, AGPL-3.0
```

**验收**：构造一个引入已知 CVE 依赖的 PR，门禁变红。

---

## A1 · 全部改动落盘提交（流程风险最高的一项）

**证据**：`git status` —— 数千行改动（core 安全/性能、site 过滤器、前端、e2e 框架、三份测试报告、全部文档）悬在工作区，仓库仅 1 个 initial commit。任何误操作（checkout/reset）即全部丢失，且 CI 从未在真实 PR 上跑过这批代码。

**方案**：按主题拆 commit（建议 7 个：`core-perf` / `core-security` / `site-filters` / `frontend` / `e2e-tests` / `docs` / `ci`），推分支 → PR → build.yml 门禁 → 合入 main。`docs/branch-protection.md` 已写好保护规则，落库启用。

**验收**：`git status` 干净；PR CI 全绿；main 上 `mvn -B verify` 通过。

---

## A2 · CI 缺 frontend 与 e2e job

**证据**：`build.yml` 仅有 `core`、`site` 两个 job。前端 16 个 vitest、e2e 回归套件（`e2e/e2e-smoke.mjs` + bl-01~10）均不在 CI。

**方案**：

- `frontend` job：`npm ci && npm run test && npm run build`（vitest 16 例 + vue-tsc 类型检查，~1min）。
- `e2e` job：起 `template4j-site`（后台）→ `node e2e/e2e-smoke.mjs`，PR 上跑 smoke，nightly cron 跑全量 bl-01~10 回归。Playwright 用 `mcr.microsoft.com/playwright` 镜像或 `npx playwright install chromium --with-deps`。

**验收**：PR 时 frontend job 必跑；nightly 报告产物上传 artifact。

---

## P2 · TraceEvent 无 consumer 时仍无条件分配

**证据**：`TraceEvent.of(...)` 在调用点先构造事件对象，`trace()` 内部才判空（`JsonRenderer.java:216-221` 的 isEmpty 检查发生在**分配之后**）。调用点遍布 5 个文件（JsonRenderer / RestRenderer / RenderStream / ExpressionEvaluator / RenderEngine）。`renderString` 热路径每次调用分配一个匿名类实例。

**方案**：`Renderer` 接口加 `default boolean traceEnabled() { return true; }`，JsonRenderer 实现 `!traceConsumers.isEmpty()`；调用点改为 `if (self.renderer.traceEnabled()) trace(...)`。保持"默认零开销"承诺真正成立。

**验收**：无 consumer 时 JMH/简单循环基准分配数为 0；现有 trace 测试全绿。

---

## A3 · micrometer 指标假数据（兑现第一轮遗留 TODO）

**证据**：`RenderMetrics.java:38` —— `TODO(T2): wire the real scriptCacheSize() value once core...`，`script.cache.size` gauge 注册为常量占位。而 core 已有现成真值源：`JsonRenderer.scriptCacheSize()`（package-private）。

**方案**：`RenderEngine` 加可选能力接口（如 `interface ScriptCacheStats { int scriptCacheSize(); default void clearScriptCache() {} }`，返回 -1 表示不支持），JsonRenderer 实现（方法提升为 public）；site 端 `Gauge.builder("template4j.script.cache.size", engine::scriptCacheSize)` 真值接线。site 的 engine 是启动时构建的单例，接线点在 `TemplateEngineConfig`。

**验收**：站点 `/actuator/metrics/template4j.script.cache.size` 运行期数值随脚本缓存增长/清空变化。

---

## S2 · 沙箱默认导入与白名单矛盾（兑现第一轮遗留 TODO）

**证据**：`GroovyImports.java:20-21` 默认导入 `java.io.*`、`java.net.*`；沙箱模式下这两个 import 照常拼进脚本，但白名单拦截 `File`/`URL`/`Socket` 等 → **导入承诺了被禁止的类**。语义混乱，且迫使 GroovySandbox 白名单写特例。

**方案**：sandbox 模式下用收敛版 import 集（移除 `java.io.*`/`java.net.*`，保留 `java.*` 的安全子集——若 groovy 默认 `java.*` 整体导入与白名单冲突，改为显式列举 `java.math.*`/`java.time.*`/`java.util.*`）。`ScriptEnvironment.eval` 沙箱分支改用 `imports.toSandboxCode()`。

**验收**：沙箱内 `new File('x')`、`new URL('http://x')` 编译期即拒（类不可见）；合法脚本（Math/StringUtils/fastjson2）不受影响。

---

## S3 · rest 响应数据 `@class` 二次渲染审计（钉死语义）

**证据**：`RestRenderer.java:315-319` —— 上游响应 deep copy 后作为 `responseData` 注入 success 模板。当前判断是**低风险**（`{@x}` 单占位直接返回原对象、`each`/`render` 渲染的是模板侧 body 而非数据侧），但引擎存在 `render` 组件、`*` 合并等会把"数据"当"模板"渲染的路径。一旦某条路径可达，恶意上游响应即可携带 `{"@class":"rest","url":"http://内网"}` 形成组件级注入链。

**方案**：先写**语义钉死测试**：响应数据含 `@class`/`$enter{}`/`$return{}` 的对象经 success/error 各分支渲染后，确认这些键永不被执行（无论单占位直返、字符串拼接还是嵌套字段引用）。若测试发现可达路径，给 RestRenderer 加"响应数据按 const 处理"开关（默认开），并在 CLAUDE.md 记录该安全边界。

**验收**：新增 `RestResponseInjectionTest`，明确记录"响应数据 = 数据，不是模板"作为产品语义。

---

## P3 · 超时渲染每调用 new Thread

**证据**：`JsonRenderer.java:244-247` —— `timeoutMs > 0` 时每次渲染 `new Thread(future, "template4j-render-timeout")`。高并发短超时场景线程创建/销毁开销显著（每次 ~0.1-0.5ms + 栈内存）。

**方案**：JDK 17/21 自适应——运行时 ≥21 用虚拟线程 `Thread.ofVirtual().unstarted(...)`（虚拟线程专为这种短阻塞任务设计，enforcer 已放开 `[17,)`），17 回退共享 daemon 池（`Executors.newCachedThreadPool` + daemon ThreadFactory）。一行判断 `Runtime.version().feature() >= 21`。

**验收**：`RenderOptionsTest` 全绿；并发 200 路 5ms 超时渲染基准对比线程分配数。

---

## P4 · renderString 多占位符逐个 replace（观察项）

**证据**：`JsonRenderer.java:336-359` —— 逆序循环内 `strResult.replace(key, ...)`，每次全串扫描+重建。占位符 k 个、串长 n 时最坏 O(k·n)。已有 parseCache 兜底解析成本，此项只在超长模板 + 多占位符时可见。

**方案（暂不动，仅记录）**：占位符 key 是 `StringSubstitutor` 生成的唯一 token（`%{n}$s` 形态），可改单遍 `StringBuilder` 顺序组装，token 用 indexOf 跳读。涉及 null 替换/JSON 内嵌/quotedKey 三条分支语义，需完整回归；建议待真实性能投诉再做。

---

## A4 · singleReturn 类型不稳定（观察项，不动）

**证据**：`JsonRenderer.java:341-345` —— 字符串模板恰好是单个占位符时返回**原始对象**（JSONObject/Number…），否则返回**拼接后的 String**。同一字段值类型随模板写法漂移（`"{@x}"` vs `prefix{@x}`）。

这是产品级语义（改动破坏所有现有模板的兼容性），仅在此记录供 API 3.0 设想：未来可提供 `RenderOptions.rawSinglePlaceholder(false)` 显式控制。**本轮不动。**

---

## 建议执行顺序

```
第一批（当天）：A1 落盘提交 → S1 限流修复（真实缺陷）
第二批（1-2天）：P1 沙箱缓存 → S4 PR 扫描门禁 → A2 CI 补 job
第三批（收尾）：P2 trace 零分配 → A3 gauge 真值 → S2 sandbox imports → S3 注入审计测试 → P3 虚拟线程
观察池：P4、A4（有真实信号再动）
```

沿用第一轮流程约束：每任务 ≤1000 行、写测试→写业务→测试→修复 ≤4 轮、子 agent 单用途、全量测试按阈值触发。

---

## 执行状态（已全部完成 ✅ 2026-11-09）

按第一轮流程约束（≤1000 行/步、写测试→写业务→测试→修复 ≤4 轮、子 agent 单用途、阈值触发全量）执行完毕，全程记录见 `round2-execution-state.md`。

| 方案项 | 落库 | 备注 |
| --- | --- | --- |
| S1 限流两缺陷 | dc9ace8 + 25013fb | trust-proxy/Caffeine 有界桶 + 17 组防御分支测试；RateLimitFilter 类 LINE 100% |
| P1 沙箱脚本缓存 | 238a26f | miss 即 put 契约；批测 9/9 |
| S4 PR 扫描门禁 | ce6ed84 | dependency-review@v4；**待用户开 Dependency graph** |
| A2 CI 补 job | 393aa30 | frontend 22s 绿；e2e PR smoke / nightly 全量 |
| P2 trace 零分配 | 238a26f | 全调用点守卫 + TOCTOU 防御 |
| A3 gauge 真值 | 238a26f | ScriptCacheStats 可选能力接口 |
| S2 沙箱 imports | 6ec226c | 默认/显式分离存储；诚实边界（Groovy 固有默认导入→运行期白名单兜底）写入 javadoc |
| S3 注入审计 | 238a26f | 11 用例钉死"数据≠模板"，无 FINDING |
| P3 虚拟线程 | 238a26f | V21 反射隔离 + jacoco 平台类排除 |
| A1 落盘提交 | feat/v0.5 全程 | 15 commits / PR #1 / CI 9 绿 |
| 观察池 P4/A4 | 未动 | 按方案"等真实信号" |

**计划外收获**：T0b CodeQL 5 error 修复（25731a9）；T10 e2e smoke 首跑抓到 600/800px 真实溢出三根因并修复（6b25c1f）。

**三端终态**：core 1635 / site 200 / frontend 33 全绿；门禁 LINE 95.74% / BRANCH 90.48%；CodeQL 0 error。
