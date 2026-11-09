# Showcase: API Orchestration with template4j

> 本目录是"声明式 API 编排"叙事的可视化示例。每个示例自包含 (template + data + expected output), 可独立运行。

## 示例清单

| # | 名称 | 演示组件 | 文件 |
|---|---|---|---|
| 1 | GitHub User Card | parallel + rest | `01-github-card.jt` |
| 2 | BFF User Profile 聚合 | parallel + try + rest + fallback | `02-bff-user-profile.jt` |
| 3 | 熔断的下游接口 | rest + circuitBreaker + retry | `03-circuit-breaker.jt` |
| 4 | 缓存去重 | rest + cache + auth | `04-cached-auth-rest.jt` |
| 5 | Schema 守护 | withInputSchema + withOutputSchema | `05-schema-guard/` |
| 6 | MCP 调用链 | mcp server + render_template | `06-mcp-demo.md` |

## 运行方式

```bash
# 用 CLI
template4j render examples/01-github-card.jt \
    --data '{"login": "torvalds"}'

# 用 watch dev server
template4j watch --dir examples --port 8080
# 浏览器打开 http://localhost:8080/01-github-card.jt?login=torvalds
```

## 示例 1: GitHub User Card

**问题**: 输入 GitHub 用户名, 输出包含用户基础 + 最近 5 repo + star 总数的卡片 JSON。

**模板** (`01-github-card.jt`):

```json
{
  "@class": "parallel",
  "tasks": {
    "user":  { "@class": "rest", "url": "https://api.github.com/users/{@login}" },
    "repos": { "@class": "rest", "url": "https://api.github.com/users/{@login}/repos?per_page=5" }
  },
  "body": {
    "login": "{$user.login}",
    "name": "{$user.name}",
    "avatar": "{$user.avatar_url}",
    "followers": "{$user.followers}",
    "topRepos": {
      "@class": "each",
      "select": "{$repos}",
      "body": {
        "name": "{$item.name}",
        "stars": "{$item.stargazers_count}"
      }
    },
    "totalStars": "{= $repos.collect{ it.stargazers_count }.sum() }"
  }
}
```

**特点**:
- ✅ 2 个 HTTP 并发(`parallel` + `rest`)
- ✅ 第二个调用结果用 `each` 转换为简化数组
- ✅ Groovy 表达式 `{=...}` 算 star 总和
- ⚠️ 依赖 GitHub API rate limit (60/h 未鉴权)

## 示例 2: BFF User Profile 聚合

**问题**: 移动端用户详情接口, 聚合 3 个微服务, 要求整体 300ms 超时 + 局部降级。

**模板** (`02-bff-user-profile.jt`):

```json
{
  "@class": "parallel",
  "tasks": {
    "user": {
      "@class": "try",
      "select": {
        "@class": "rest",
        "url": "/users/{@id}",
        "circuitBreaker": { "threshold": 5, "resetSec": 60 }
      },
      "fallback": { "error": "user service down", "id": "{@id}" }
    },
    "orders": { "@class": "rest", "url": "/users/{@id}/orders" },
    "payment": {
      "@class": "try",
      "select": { "@class": "rest", "url": "/payments?user={@id}" },
      "fallback": { "@class": "rest", "url": "/cache/payments/{@id}" }
    }
  },
  "timeout": 300,
  "body": {
    "user": "{$user}",
    "orders": "{$orders}",
    "payment": "{$payment}"
  }
}
```

**特点**:
- ✅ `parallel` 整体 300ms deadline
- ✅ user 接口 5 次失败熔断 60s
- ✅ payment 失败自动走缓存接口(级联 try/fallback)
- ✅ orders 失败会让整个 parallel 走 error 路径(没有 try 包装)

## 示例 3: 熔断的下游接口

**问题**: 演示 circuitBreaker 的 CLOSED/OPEN/HALF_OPEN 状态机。

**模板** (`03-circuit-breaker.jt`):

```json
{
  "@class": "rest",
  "url": "/flaky",
  "retry": { "count": 2, "baseMs": 100, "backoff": "exponential" },
  "circuitBreaker": { "threshold": 3, "resetSec": 30 },
  "error": { "status": "failed", "msg": "{$responseData.message}" }
}
```

**行为**: 前 3 次失败(每次 1+2 retries)→ 第 4 次直接走 error 不发 HTTP → 30 秒后 half-open 探测 → 成功则关闭, 失败继续 open。

## 示例 4: 缓存去重 + Auth

**问题**: 高 QPS 接口下避免重复调下游 + 自动鉴权。

**模板** (`04-cached-auth-rest.jt`):

```json
{
  "@class": "rest",
  "url": "/api/orders",
  "auth": { "type": "bearer", "token": "{@token}" },
  "cache": { "key": "orders:{@userId}", "ttl": 60000 },
  "timeout": 5000
}
```

**特点**: 同 userId 60 秒内只调下游 1 次, Authorization header 自动注入。

## 示例 5: Schema 守护

**问题**: 入参必须满足契约, 渲染结果必须满足响应契约。fail-fast。

**文件**: `05-schema-guard/`
- `input-schema.json` — 入参 schema
- `output-schema.json` — 输出 schema
- `template.jt` — 模板
- `run.sh` — Java 启动代码片段

```java
RenderEngine engine = RenderEngine.build()
    .withInputSchema(Files.readString("input-schema.json"))
    .withOutputSchema(Files.readString("output-schema.json"));

// data 不匹配 → 立即抛, 不渲染
// result 不匹配 → 拦截上线 (回归测试时用)
Object result = engine.renderJson(template, data);
```

## 示例 6: MCP 调用链

**问题**: Claude Desktop / Cursor 调用 template4j 渲染。

**配置** (Claude Desktop `claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "template4j": {
      "command": "java",
      "args": ["-cp", "template4j-core.jar", "fun.commons.template4j.core.McpServer"]
    }
  }
}
```

**对话**:

```
User: 用模板 {\"v\":\"{@x}\"} 渲染 x=42 的结果
Assistant: 调用 render_template 工具 → {"v": 42}
```

详细见 `manual/13-mcp-server.md`。

## 性能参考

| 示例 | 单次渲染 (JDK 17 / mac M2) | 备注 |
|---|---|---|
| #1 GitHub Card | ~150ms (受 GitHub API 限速) | 网络 I/O 主导 |
| #2 BFF Profile | ~120ms (3 接口并行) | 整体超时 300ms 足够 |
| #3 Circuit Breaker open 状态 | <1ms | 不发 HTTP |
| #4 Cache 命中 | <0.1ms | 内存读取 |

## 集成方式

### 嵌入 Java 应用

```java
RenderEngine engine = RenderEngine.build()
    .withBean("userService", userService)
    .withImport("com.example.Constants");

String templateJson = Files.readString("02-bff-user-profile.jt");
Object result = engine.renderJson(templateJson, request.getData());
```

### Spring Boot Controller

```java
@RestController
public class BffController {
    private final RenderEngine engine;
    private final Template compiled;  // 启动时预编译

    public BffController() throws Exception {
        this.engine = RenderEngine.build();
        this.compiled = engine.compile(Files.readString("bff.jt"));
    }

    @PostMapping("/mobile/user/{id}")
    public Object mobile(@PathVariable String id) throws Exception {
        return compiled.render(Map.of("id", id));
    }
}
```

`compile()` 让热路径性能达到 360ns/call(simple 模板)。
