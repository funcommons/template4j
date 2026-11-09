# Release Notes — 0.5.0

**发布日期**: 2026-07-14

template4j 0.5.0 是**API 编排引擎**的关键里程碑。在保留 JSON/YAML 模板引擎核心的同时,新增了 BFF / API 聚合场景的全部生产要素。

## 🎯 核心亮点

### 1. 三个编排组件

- **`parallel`** — 并发执行多个子模板,整体 deadline,容忍单任务失败。
- **`try`** — 错误隔离与降级,`fallback` / `onError` / `rethrow`。
- **`rest` 增强** — `timeout` / `retry` / `cache` / `auth` / `circuitBreaker`。

```json
{
  "@class": "parallel",
  "tasks": {
    "user":   { "@class": "rest", "url": "/users/{@id}", "auth": { "type": "bearer", "token": "{@tok}" } },
    "orders": { "@class": "rest", "url": "/users/{@id}/orders", "cache": { "key": "orders:{@id}", "ttl": 60000 } }
  },
  "timeout": 3000,
  "body": {
    "userName": "{$user.name}",
    "orderCount": "{$orders.size()}"
  }
}
```

### 2. 性能 5.7x

引入 `parseStrTemplate` 结果 LRU 缓存 + `engine.compile()` 显式预编译 API。simple 模板热路径:

| | 0.4.0 | **0.5.0** |
|---|---|---|
| Simple (warm) | 2,060 ns/call | **360 ns/call** (5.7x) |
| JSON (warm) | 6,780 ns/call | 3,920 ns/call (1.7x) |
| Each-100 (warm) | 1,265 μs/call | 949 μs/call (1.3x) |

### 3. 工程化三件套

- **`withTraceConsumer`** — RENDER_STRING / COMPONENT / REST / SCRIPT 事件追踪,零开销默认。
- **`withInputSchema` / `withOutputSchema`** — JSON Schema 校验(networknt),fail-fast。
- **`compile` API** — 显式预编译 + LRU 自动缓存。

### 4. CLI + MCP Server

```bash
# CLI (fat jar 单文件部署, ~16 MB, 入口 fun.commons.template4j.core.Cli)
java -jar template4j-core-shaded.jar render template.jt --data-file data.json
java -jar template4j-core-shaded.jar version
java -jar template4j-core-shaded.jar watch --dir ./templates --port 8080

# MCP Server (供 Claude Desktop / Cursor 调用)
java -jar template4j-core-shaded.jar mcp
```

### 5. Spring 依赖完全移除

`RestRenderer` 改用 JDK `java.net.http.HttpClient`,`template4j-core` 不再依赖 spring-web。多模块项目可零冲突引入。

## 📊 数字

| 维度 | 0.4.0 | **0.5.0** |
|---|---|---|
| 内置组件 | 7 | **9** (新增 parallel / try) |
| rest 子属性 | 3 (url/method/data) | **9** (+timeout/retry/cache/auth/circuitBreaker) |
| 测试数 | 1244 | **1563** (+319) |
| 覆盖率 LINE | 100% (small) | 97.00% (含 ~700 行新代码) |
| 覆盖率 BRANCH | 100% (small) | 92.77% |
| JaCoCo 阈值 | 1.00/1.00 | 0.97/0.92 (core), 0.92/0.87 (site) |
| 依赖 (compile) | 7 | 7 (替换 spring-web → commons-text + networknt) |
| Manual 章节 | 11 | **13** (+CLI / MCP Server) |

## 🔧 Breaking changes

无。`render` / `renderJson` / `renderYaml` / `withBean` / `withComponent` / `withImport` / `withLoader` API 完全兼容。

新增 API 全部为加法(`compile` / `withTraceConsumer` / `withInputSchema` / `withOutputSchema`)。

`@class: rest` 新增子属性全部可选,无指定时行为与 0.4.0 一致。

## 🐛 Bug fixes

- `RenderException.removeUnused` NPE(JDK HttpClient 的 null-message ConnectException 触发)
- `convertVars` 中 3 处 `k.matches` regex 改为 char-scan,降低正则使用
- `parseStrTemplate` 改为 `StringSubstitutor` 6 遍,降低手写状态机
- `codeReplaceAt` 改为单遍状态机
- 删除 `URegex` 工具类(零生产调用者)

## 📚 升级指南

0.4 → 0.5 零破坏性变更。Maven 用户:

```xml
<dependency>
  <groupId>fun.commons</groupId>
  <artifactId>template4j-core</artifactId>
  <version>0.5.0</version>
</dependency>
```

如果你曾自定义 `RenderComponent`,接口未变,直接兼容。

## 🙏 致谢

感谢在 0.5.0 开发期间提出 issue / 提交 PR 的所有贡献者。0.6.0 路线图关注:

- **真异步 HTTP**(`HttpClient.sendAsync`)— 进一步降低 parallel 延迟
- **模板预编译为 Lambda** — 性能再 5-10x
- **CLI watch dev server** — 模板热重载
- **官网 + showcase demo** — 把"声明式 API 编排"叙事可视化

详见 `CHANGELOG.md`。
