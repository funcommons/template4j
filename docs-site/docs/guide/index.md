# 快速开始

template4j 是一个 JSON / YAML 声明式模板引擎：模板本身就是 JSON 形态的对象，混合字面量与表达式，通过 `@class` 调用内置组件完成 API 编排。

## 环境要求

- JDK 17+（21+ 可启用超时虚拟线程）

## 引入依赖

```xml
<dependency>
  <groupId>fun.commons</groupId>
  <artifactId>template4j-core</artifactId>
  <version>0.5.0</version>
</dependency>
```

运行时零 Spring 依赖；直接引入即可，无传递冲突风险。

## 三行上手

```java
RenderEngine engine = RenderEngine.build();

Object result = engine.render("""
    {
      "name": "BFF 聚合示例",
      "user": { "@class": "rest", "url": "/api/user/{@id}" }
    }
    """, Map.of("id", 42));
```

模板即数据：字面量原样输出，`{@path}` 走 JSONPath 取数，`@class` 调起组件。

## 表达式五形态

| 形态 | 含义 |
| --- | --- |
| `{@ path}` | JSONPath 取数 |
| `{$ name}` | 模板内变量 |
| `{= script }` | Groovy 单行表达式 |
| `{% script %}` | Groovy 块级脚本（多行，须 return） |
| `<& … &>` | 字面文本，不求值 |

## 常用能力

- **REST 编排**：`@class: rest` + `timeout` / `retry` / `cache` / `auth` / `circuitBreaker`
- **并行聚合**：`@class: parallel` + 整体 deadline + `continueOnError`
- **显式预编译**：`engine.compile(template)` → `Template`（线程安全，可复用）
- **执行追踪**：`engine.renderStream(template, data)` 推/拉双模式 SSE
- **Schema 校验**：`engine.withInputSchema(...)` fail-fast

## CLI 与 MCP Server

shaded fat jar 单文件部署（~16MB，入口 `fun.commons.template4j.core.Cli`）：

```bash
java -jar template4j-core-shaded.jar render template.jt --data-file data.json
java -jar template4j-core-shaded.jar watch --dir ./templates --port 8080
java -jar template4j-core-shaded.jar mcp     # MCP stdio server, 供 Claude Desktop / Cursor 调用
```

## 下一步

- 浏览 [组件总览](/components) 了解 9 个内置组件
- 在线试用：`template4j-site` 演示站（learn / try / docs / show-case）
- 全量系统属性参考见仓库 `CLAUDE.md`
