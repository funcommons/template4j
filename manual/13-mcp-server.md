# 13 MCP Server (v0.5+)

template4j-core 自带一个最小 MCP (Model Context Protocol) server，stdio transport (JSON-RPC 2.0)，可让 AI Agent（Claude Desktop / Cursor / Continue 等）通过标准协议调用模板渲染。

## 用法

在 MCP client 配置中添加：

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

或用 shaded fat jar (推荐, 单文件部署)：

```json
{
  "mcpServers": {
    "template4j": {
      "command": "java",
      "args": ["-jar", "/path/to/template4j-core-shaded.jar", "mcp"]
    }
  }
}
```

shaded jar 见 [CLI 章节](12-cli.md) `-Pshade` profile。

并在 `META-INF/MANIFEST.MF` 中指定 `Main-Class: fun.commons.template4j.core.McpServer`。

## 暴露的工具

| 工具名 | 描述 |
|---|---|
| `render_template` | 用 template4j 渲染模板 |

参数：

```json
{
  "template": "{\"v\":\"{@v}\"}",   // 必填
  "data": {"v": 42},                 // 必填，JSON object
  "format": "json"                   // 可选: "json" (默认) | "yaml"
}
```

返回：渲染结果的 JSON 字符串包在 MCP `text` content 中。

## 协议支持

实现 [MCP 2024-11-05](https://modelcontextprotocol.io/specification) 的最小子集：

- `initialize` / `notifications/initialized` 握手
- `tools/list` 列出工具
- `tools/call` 调用工具
- `ping` 心跳
- 未识别方法返回 `-32601 method not found`

未实现：resources、prompts、sampling、completion、roots、logging（按需可加）。

## 设计原则

- **零依赖**：JDK + fastjson2 + template4j-core 内部 API
- **stdio transport**：每行一个 JSON-RPC 请求/响应（line-delimited），最适合 IDE 插件 / CLI 工具集成
- **stateless**：每个请求创建独立 `RenderEngine`，无跨请求状态
- **错误透传**：渲染失败返回 `isError: true` + 异常消息，client 可向用户展示

## 安全

MCP server **不在沙箱内**：模板可以执行任意 Groovy 脚本（`{=...}` / `{%...%}`），并发起 HTTP（`@class: rest`）。**仅在你信任的 Agent/环境中部署**。生产场景建议：

- 用 `engine.withImport(...)` + 模板白名单限制
- 在隔离容器中运行 server，网络出口加白名单
- 不接受来自 Agent 的 `rest` 组件 url（用自定义 RenderComponent 拦截）
