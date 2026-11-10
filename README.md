# template4j

**📖 在线文档站:[funcommons.github.io/template4j](https://funcommons.github.io/template4j/)** — [快速开始](https://funcommons.github.io/template4j/guide/) · [组件总览](https://funcommons.github.io/template4j/components)

[![build](https://github.com/funcommons/template4j/actions/workflows/build.yml/badge.svg)](https://github.com/funcommons/template4j/actions/workflows/build.yml)
[![codeql](https://github.com/funcommons/template4j/actions/workflows/codeql.yml/badge.svg)](https://github.com/funcommons/template4j/actions/workflows/codeql.yml)
[![security-scan](https://github.com/funcommons/template4j/actions/workflows/security-scan.yml/badge.svg)](https://github.com/funcommons/template4j/actions/workflows/security-scan.yml)

JSON / YAML 声明式模板引擎:模板本身就是 JSON 形态的对象,混合字面量与嵌入式表达式,通过 `@class` 调用内置组件完成 BFF / API 编排。模板即数据,一个 JSON 模板就是一个接口。

| 模块 | 说明 |
| --- | --- |
| `template4j-core` | 核心引擎(运行时零 Spring,JDK 17+) |
| `template4j-site` | Spring Boot + Vue 3 演示站 |
| `docs-site/` | VitePress 文档站(部署于 GitHub Pages) |

**0.5.0 关键特性**:
- 9 个内置组件:if / each / switch / invoke / render / const / rest / **parallel** / **try**
- `rest` 增强:`timeout` / `retry` / `cache` / `auth` / `circuitBreaker`(BFF 编排三件套)
- 性能:simple 模板热路径 **sub-μs**(2026-11 复测 0.51 μs,门限余量 44x–196x),`compile()` 显式预编译 + 模板 / Groovy 编译 LRU 缓存(详见[基准报告](./docs/analysis/benchmark-report-2026-11-10.md))
- 工程化:`withTraceConsumer`(调试追踪) / `withInputSchema` / `withOutputSchema`(JSON Schema 校验)
- 入口:CLI(`render` / `watch` / `mcp` / `version` / `help`) + MCP server(对接 Claude Desktop / Cursor)
- 安全:SSRF 白名单 fail-closed + 响应体积上限 + Groovy 沙箱模式;依赖漏洞清零(21 → 0),CodeQL / Trivy / OWASP 常驻 CI
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

详见 [`manual/zh-CN/01-quick-start.md`](./manual/zh-CN/01-quick-start.md),或在线版[文档站·快速开始](https://funcommons.github.io/template4j/guide/)。

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

详见 [`manual/zh-CN/12-cli.md`](./manual/zh-CN/12-cli.md) 和 [`manual/zh-CN/13-mcp-server.md`](./manual/zh-CN/13-mcp-server.md)。

## 构建

```bash
mvn test                       # 跑测试 (~1362 用例, JUnit 5)
mvn -B verify                  # 测试 + JaCoCo 覆盖率门槛 (LINE ≥ 95% / BRANCH ≥ 85%)
mvn -Pbenchmark -pl template4j-core test    # 性能 benchmark
mvn -Pshade -pl template4j-core package -DskipTests  # fat jar
```

## License

TBD
