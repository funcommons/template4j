# 10 最佳实践

## 一、性能

### 1. 首渲损耗

含脚本（`{% %}` / `{= %}`）的模板第一次渲染需要**编译 Groovy 脚本**，约 100ms 级别。之后的渲染命中编译产物，性能损耗接近 0。

| 模板特征 | 性能 |
| :--- | :--- |
| 无脚本（纯 `{@}` `{$}` `<&>`） | 首次就有完整性能 |
| 含脚本 | 首次 100ms 级别，后续接近无开销 |

### 2. 预热关键模板

对响应敏感的接口，在系统启动时主动渲染一次（用任意数据），把模板编译产物缓存下来：

```java
@Component
public class TemplateWarmup implements ApplicationRunner {

    @Autowired private RenderEngine engine;
    @Autowired private TemplateRepo repo;

    @Override
    public void run(ApplicationArguments args) {
        for (String name : repo.listCriticalTemplates()) {
            String json = repo.load(name);
            try {
                engine.renderJson(json, new JSONObject());
            } catch (Exception e) {
                log.warn("warmup failed for {}", name, e);
            }
        }
        log.info("template warmup done, {} templates", repo.listCriticalTemplates().size());
    }
}
```

### 2a. 显式 `compile` API（v0.5+）

需要精确控制何时编译、何时渲染时，用 `engine.compile(template)` 显式预编译：

```java
// 启动时编译一次
Template compiled = engine.compile(templateJson);

// 每次请求只走渲染（热路径快 2-5x）
Object result = compiled.render(dataMap);
```

`Template` 对象线程安全，可缓存复用。编译产物内部包含 `parseStrTemplate` 的 tokenized string + expressionMap，避免每次渲染重做 6 次 `StringSubstitutor` 扫描。

适用场景：高 QPS 接口、CI/CD pipeline 中的固定模板、模板即配置的中心化服务。

引擎内部也维护一个有界 LRU 缓存（默认 1024 条，系统属性 `template4j.parseCache.maxEntries` 可调），即便不显式 `compile`，相同字符串模板的二次渲染也会自动命中缓存。

### 2b. 调试追踪 (v0.5+)

```java
engine.withTraceConsumer(ev -> System.out.println(ev.format()));
```

输出示例：

```
[RENDER_STRING] /user        → "alice"
[REST]          /api/user    GET http://.../users/1  200  (45 ms)
[SCRIPT]        /body        {= @x > 0 ? 'a' : 'b' }  (2 ms)
[COMPONENT]     /parallel    tasks=2  (310 ms)
```

事件类型（`TraceEvent.type()`）：`RENDER_STRING` / `RENDER_MAP` / `COMPONENT` / `REST` / `SCRIPT` / `ERROR`。

**性能影响**：未注册 consumer 时零开销（JIT 消除 null 检查）。注册后只付出字符串格式化 + callback 成本（每次事件 ~微秒级）。生产环境建议用 ring buffer + 异步 consumer 摊销。

```java
// 用示例: 把 trace 发到日志 / 监控
List<TraceEvent> events = new CopyOnWriteArrayList<>();
engine.withTraceConsumer(events::add);
Object result = engine.render(template, data);

events.forEach(ev -> metrics.record(ev.type(), ev.durationMs()));
```

### 2c. Schema 校验 (v0.5+)

```java
engine.withInputSchema("""
        {
          "$schema": "http://json-schema.org/draft-07/schema#",
          "type": "object",
          "properties": {
            "id": { "type": "integer", "minimum": 1 }
          },
          "required": ["id"]
        }
        """);

// data 不匹配 → 立即抛 RenderException, 不进入渲染流程
Object result = engine.renderJson(template, data);
```

- 支持 input schema（渲染前校验 data）与 output schema（渲染后校验结果）。
- schemaContent 接受 JSON 字符串 / `Map` / Jackson `JsonNode`。
- 使用 [networknt/json-schema-validator](https://github.com/networknt/json-schema-validator)（Draft-07），传递依赖约 1MB。
- 无 schema 时零开销（null 检查）。

适用场景：BFF 接口入参契约、模板即配置场景的回归守护、CI 流水线中的 schema 漂移检测。

### 3. 单例 `RenderEngine`

`RenderEngine.build()` 返回的实例是**不可变快照**；`withBean` / `withComponent` 返回新实例。多个线程共享同一实例是安全的：

```java
public final class TemplateConfig {
    public static final RenderEngine ENGINE = RenderEngine.build();
}
```

按需派生：

```java
public RenderEngine forRequest(Request req) {
    return TemplateConfig.ENGINE.withBean("session", req.getSession());
}
```

### 4. 减少 `&include` 嵌套

`&include` 在**渲染前**静态合并，每次渲染都会重新加载。深嵌套的 `&include` 既增加 IO 也增加解析开销。可以：

- 把小片段直接内联；
- 或把多个 `&include` 合并到一个文件。

### 5. 避免 `@each` 拼大数组

`@each` 默认 `merge: true`，body 返回 List 会被展平。遍历十万级数组时考虑：

- 在脚本里直接构造 List（用 `[...collect{...}]`），返回给 `*` 合并；
- 或者 `merge: false` 把整段作为单个元素，避免展平操作。

### 6. JSONPath 复杂度

`{@...}` 使用 fastjson2 JSONPath。深路径（`a.b.c.d.e`）+ 大数据集下，每次 `JSONPath.eval` 会重算路径。若某字段反复读取（比如 `each` 里 1000 次循环都访问 `@deep.path.field`），可在脚本里取一次：

```json
{
  "$value": "{= @deep.path.field }",
  "items": {
    "@class": "each",
    "select": "{@items}",
    "body": { "v": "{$value}" }
  }
}
```

`$value` 渲染一次（一次 JSONPath），后续 `each` 里直接变量读取（O(1)）。

### 7. 并发与"伪异步"的边界 (v0.5+)

`parallel` 组件用 daemon `FixedThreadPool`(默认 16，系统属性 `template4j.parallel.poolSize` 可调)，每个 task 在独立线程上调用 `RestRenderer`。底层 `HttpClient.send()` 是**阻塞 I/O**，task 数 > pool size 时排队等待。

适用场景：BFF 聚合 3-10 个下游接口，pool size 16 富富有余。

**不适用**：

- **C10k 长尾延迟**：1000+ 并发 rest 调用 → pool 饱和，task 排队等价于串行。需要 **真正的 async HTTP**(`HttpClient.sendAsync` + 全链路 CompletableFuture)，这是 0.6.0 路线图。
- **CPU 密集脚本**：`{%...%}` Groovy 块占用 worker，不要在 `parallel.tasks` 里写 CPU-bound 计算。

**临时解法**：调大 pool size。

```bash
java -Dtemplate4j.parallel.poolSize=128 ...
```

代价：内存占用上升（每线程 ~1MB stack），线程上下文切换开销。生产环境建议测压后定值。

### 8. `engine.compile()` 不是真编译 (v0.5 现状)

`compile(template)` 当前**只**缓存 `parseStrTemplate` 结果（占位符 tokenized string + expressionMap），渲染时仍走 `JsonRenderer.convertSelf` 递归遍历 AST。

**真正的 AST → Lambda 编译**（类似 jte）是 0.6.0 路线图。预期收益再 5-10x（消除递归 + JSON parsing）。

当前 5.7x 提速已足够大多数场景。如果热路径仍嫌慢，可：

- 预热：启动时 `engine.compile(criticalTemplate)` 一次
- 减少 `each` 内的 JSONPath：见 §6
- 拆模板：嵌套 5 层 map → 平铺，每层递归都有开销

`{@users[?(@.age > 18)]}` 这类过滤表达式每次都要遍历。热路径上：

- 优先用 `$name` 缓存中间结果；
- 或在脚本里用 Groovy 的 List 操作（`users.findAll{ it.age > 18 }`），可配合 `withBean` 把数据预处理成更快的结构。

## 二、安全

### 1. 模板不是数据，是代码

> **把模板视为源代码进行保护，而不是数据。**

模板中嵌入 Groovy 脚本可以**执行任意 Java 代码**——读文件、HTTP 请求、调用任何 Bean、修改全局状态。所以：

- ❌ **不要**让前端传入模板字符串。
- ❌ **不要**把用户输入拼成模板。
- ❌ **不要**把不受信的 YAML 解析后直接 `render`。
- ✅ 模板应该与代码同级管理（Git 版本控制、Code Review）。
- ✅ 即便模板存储在数据库，也只允许**受控角色**编辑。

### 2. 输入数据

数据本身**不参与 Groovy 求值**——`{@xxx}` 只走 JSONPath，不会触发 `codeReplaceAt`。但要注意：

- 通过 `{= @x.y.z }` 引用的字段会被改写为 `data.x.y.z`——如果 `data` 里某个 Map 的 key 是 `@xxx`，脚本里写 `@xxx` 仍能拿到。
- 通过 `cast(@'path', Class)` 转换的数据类型，需要确保来源可信（不安全的反序列化等同 Java 反序列化漏洞）。

### 3. `@rest` 出站访问

`@rest` 使用 JDK 内置 `java.net.http.HttpClient`，默认跟随重定向、允许 HTTP/HTTPS。模板作者如果能控制 `@rest` 的 `url`，就相当于任意 SSRF 出口。生产环境建议：

- 拦截内网地址（10.x / 172.16.x / 192.168.x）；
- 用自定义 `RenderComponent` 替换内置 `rest`，加白名单。

### 4. 错误信息

`RenderException` 会带上模板路径（`self.path()`），方便定位。但如果路径包含敏感数据（用户邮箱、token 等），注意日志脱敏。

### 5. 模板缓存的隔离

多租户场景下，不同租户的模板可能同名。**不要**把模板缓存放在全局静态 Map 里——按租户命名空间隔离。

## 三、可维护性

### 1. 命名

- 变量 `$today`、`$user` 优于 `$d`、`$u`。
- 模板片段命名空间：`partials/header.json`、`pages/order-detail.json`，避免堆在一个目录。

### 2. 注释

善用 `#` 注释：

```json
{
    "#": "下单页：用户已登录才渲染价格",
    "@if|!blank": "{@user.session}",
    "then": { "price": "{@product.price}" },
    "else": { "redirect": "/login" }
}
```

`#` 字段会被剥离，不会出现在渲染结果中。

### 3. 复杂逻辑下沉

超过 5 行的 Groovy 脚本建议：

- 写成 Java 方法，通过 `withBean` 注入；
- 或拆成多个小 `$name` / `$name()`，组合调用。

### 4. 模板与文档

建议每个模板在文件头加 `description`（通过自定义组件 / 注解承载）或在配套 README 中说明输入数据 schema。

### 5. 版本演进

模板格式演进时（如新增组件、新增占位符），通过 `engine.withImport(...)` 兼容旧模板，避免硬性升级。