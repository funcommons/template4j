# template4j

JSON / YAML 模板引擎核心（`template4j-core`） + 官网 + Demo（`template4j-site`）。

**0.5.0 关键特性**:
- 9 个内置组件:if / each / switch / invoke / render / const / rest / **parallel** / **try**
- `rest` 增强:`timeout` / `retry` / `cache` / `auth` / `circuitBreaker`(BFF 编排三件套)
- 性能:simple 模板热路径 **2,060 ns → 360 ns (5.7x)**,引入 `compile()` + LRU 缓存
- 工程化:`withTraceConsumer`(调试追踪) / `withInputSchema` / `withOutputSchema`(JSON Schema 校验)
- 入口:CLI(`render` / `watch` / `mcp` / `version` / `help`) + MCP server(对接 Claude Desktop / Cursor)
- **完全去 Spring**:RestRenderer 用 JDK HttpClient,核心 jar 不依赖 spring-web

> 用户手册:[`manual/`](./manual/) · [Release Notes 0.5.0](./docs/release-notes-0.5.0.md) · [技术博客](./docs/blog-0.5.0-api-orchestration.md) · [变更日志](./CHANGELOG.md)

## 快速开始

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>template4j-core</artifactId>
    <version>0.5.0</version>
</dependency>
```

```java
RenderEngine engine = RenderEngine.build();
String template = """
        {
            "info": "{@name} 同学 {@age} 岁"
        }
        """;
Object result = engine.renderJson(template, Map.of("name", "小红", "age", 19));
```

详见 [`manual/zh-CN/01-quick-start.md`](./manual/zh-CN/01-quick-start.md)。

## CLI / MCP

```bash
# build fat jar
mvn -Pshade -pl template4j-core package -DskipTests

# CLI render
java -jar template4j-core/target/template4j-core-shaded.jar render template.jt --data '{"name":"alice"}'

# CLI watch (dev server)
java -jar template4j-core/target/template4j-core-shaded.jar watch --dir templates/ --port 8080

# MCP server (for Claude Desktop / Cursor)
java -jar template4j-core/target/template4j-core-shaded.jar mcp
```

详见 [`manual/12-cli.md`](./manual/12-cli.md) 和 [`manual/13-mcp-server.md`](./manual/13-mcp-server.md)。

## 模块

- **`template4j-core`** — 核心引擎(无 Spring 依赖,JDK 17+)
- **`template4j-site`** — Spring Boot + Vue 3 demo app

## 构建

```bash
mvn test                       # 跑测试 (~1362 用例, JUnit 5)
mvn -B verify                  # 测试 + JaCoCo 覆盖率门槛 (LINE ≥ 95% / BRANCH ≥ 85%)
mvn -Pbenchmark -pl template4j-core test    # 性能 benchmark
mvn -Pshade -pl template4j-core package -DskipTests  # fat jar
```

## License

TBD
