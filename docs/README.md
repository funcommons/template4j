# template4j

JSON / YAML 模板引擎核心（`template4j-core`） + Spring Boot 集成（`template4j-web`）。

> 用户手册：[`manual/`](./manual/)
>
> 开发分析文档：[`docs/analysis/`](./docs/analysis/)
>
> 变更日志：[`CHANGELOG.md`](./CHANGELOG.md)

## 快速开始

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>template4j-core</artifactId>
    <version>0.1.0</version>
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

## 模块

- **`template4j-core`** — 核心引擎（无 Spring 依赖）
- **`template4j-web`** — Spring Boot 集成 + 文档站点

## 构建

```bash
mvn -pl template4j-core -am test        # 跑测试（309 用例）
mvn -pl template4j-core -am verify     # 测试 + JaCoCo 覆盖率门槛（LINE ≥ 80%）
```

## License

TBD
