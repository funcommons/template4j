# template4j 使用手册

> 一份按图索骥、由浅入深的用户手册。每章给出**语法 + 可运行示例 + 与其它章的交叉引用**。

## 目录

| # | 章节 | 内容 |
| :--- | :--- | :--- |
| 01 | [快速开始](01-quick-start.md) | 5 分钟跑通第一个模板（依赖、引擎、渲染） |
| 02 | [语法基础](02-syntax.md) | 5 种占位符、键前缀/后缀、转义 |
| 03 | [数据与变量](03-data-and-vars.md) | `@` 数据、`$`/`$$` 变量、`@@` 局部数据、`$enter{}`/`$return{}` |
| 04 | [Groovy 脚本](04-scripting.md) | `{% … %}` / `{= … }`、`@` 重写、内置 Func\* 工具（data/vars/render/load/cast/fne） |
| 05 | [方法 $name()](05-functions.md) | 用户定义方法、`_args`、`FuncObject`、递归守卫 |
| 06 | [内置组件](06-components.md) | `if` / `each` / `switch` / `invoke` / `render` / `const` / `rest` / `parallel` / `try` |
| 07 | [合并与包含](07-merge-and-include.md) | `*` 合并键、`&include` 模板嵌套 |
| 08 | [资源加载](08-resource-loading.md) | `renderUrl` / `renderJsonUrl` / `renderYamlUrl`、格式推断 |
| 09 | [自定义组件](09-custom-component.md) | 实现 `RenderComponent`、`engine.withComponent` |
| 10 | [最佳实践](10-best-practices.md) | 性能（预热/compile/缓存）、安全（可信模板）、可维护性 |
| 11 | [语法参考](11-grammar-reference.md) | 全量语法表（占位符/键前缀/组件/工具） |
| 12 | [CLI](12-cli.md) | 命令行工具：`render` / `version` / `help` |
| 13 | [MCP Server](13-mcp-server.md) | 把 template4j 暴露给 AI Agent（stdio JSON-RPC） |

## 核心心智模型

template4j 的设计目标是：**用 JSON / YAML 表达一个数据驱动的模板**，输入数据，得到输出 JSON。

模板与普通 JSON 的区别只在两点：

1. **占位符表达式**：字符串中嵌入 `{@…}` / `{=…}` / `{%…%}` / `{$…}` / `<&…&>`。
2. **特殊键**：以 `$` / `@` / `&` / `#` / `*` 开头或结尾的 key 携带语义（如变量定义、注释、合并、组件调度）。

其它所有内容（数字、布尔、数组、对象字面量）都和普通 JSON 一致。

模板渲染是**递归下降**的：引擎遇到字符串则解析占位符；遇到对象则先处理特殊键、再递归处理字段；遇到数组则逐项递归。

## 你能用 template4j 做什么

- **接口响应生成**：把环境/上下文拼成 JSON，而不必写一堆 `JSONObject.put`。
- **动态 SQL 拼装**：用 `@each` + `@if` 拼 SQL 片段（见 [组件章节](06-components.md) 中的 `if` SQL 例子）。
- **HTTP 聚合**：用 `@rest` 在模板里直接拉远端数据，配合 `success` 块做再加工。
- **配置驱动的可视化页**：模板就是 JSON，可存储在数据库、配置中心，配合前端低代码渲染。

## 最小依赖

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>template4j-core</artifactId>
    <version>0.1.0</version>
</dependency>
```

JDK 17+，传递依赖包括 fastjson2、groovy-jsr223、jackson-dataformat-yaml、commons-text、commons-lang3。`rest` 组件使用 JDK 内置 `java.net.http.HttpClient`，无 Spring 依赖。

## 反馈与勘误

源码与测试位于 `template4j-core/`；已有更细的 `.yml` 文档位于 `template4j-web/src/main/resources/docs/`。本手册是面向人阅读的整合版，与上述材料同步但不必一一对应。