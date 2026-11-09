# MCP 调用链示例

本示例演示如何让 Claude Desktop / Cursor / Continue 等 MCP client 通过标准协议调用 template4j 渲染模板。

## 架构

```
[AI Client]  ←(JSON-RPC / stdio)→  [template4j MCP server]  →(render)→  [JSON output]
```

## 1. 启动 MCP server

`template4j-core` 自带 MCP server, 通过 `Cli mcp` 子命令进入 (或直接调 `McpServer.main`):

```bash
# thin jar (classpath 完整)
java -cp template4j-core.jar fun.commons.template4j.core.Cli mcp

# shaded fat jar (推荐, 单文件)
mvn -Pshade -pl template4j-core package -DskipTests
java -jar template4j-core/target/template4j-core-shaded.jar mcp
```

server 启动后从 stdin 读 JSON-RPC 请求, 向 stdout 写响应。

## 2. 配置 Claude Desktop

编辑 `~/Library/Application Support/Claude/claude_desktop_config.json`:

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

重启 Claude Desktop, 应该看到 "template4j" 出现在可用 tools 列表。

## 3. 对话示例

```
User:     用模板 {"v":"{@x}"} 渲染 x=42
Claude:   [调用 render_template tool]
          arguments: { "template": "{\"v\":\"{@x}\"}", "data": { "x": 42 } }
          → {"v": 42}

User:     用 GitHub user 数据渲染 user card 模板 (附文件 user-card.jt)
Claude:   读 user-card.jt 内容
          [调用 render_template tool]
          arguments: { "template": "<file content>", "data": { "login": "torvalds" } }
          → 完整渲染后的 JSON
```

## 4. 用 stdio 直接测试

不需要 MCP client, 直接用 shell:

```bash
(
  echo '{"jsonrpc":"2.0","id":1,"method":"initialize"}'
  echo '{"jsonrpc":"2.0","method":"notifications/initialized"}'
  echo '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
  echo '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"render_template","arguments":{"template":"{\"v\":\"{@x}\"}","data":{"x":42}}}}'
) | java -cp template4j-core.jar fun.commons.template4j.core.McpServer
```

输出:

```
{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05", ...}}
{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"render_template", ...}]}}
{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"{\"v\":42}"}],"isError":false}}
```

## 5. 支持的协议方法

| 方法 | 说明 |
|---|---|
| `initialize` | MCP 握手, 返回 server 信息与 capabilities |
| `notifications/initialized` | client 通知握手完成 (无响应) |
| `tools/list` | 列出工具 (`render_template`) |
| `tools/call` | 调用工具 |
| `ping` | 心跳 |

未实现: `resources/*` / `prompts/*` / `sampling/*` / `completion/*` / `roots/*` / `logging/*`。

## 6. 工具 schema

```json
{
  "name": "render_template",
  "description": "Render a template4j template (JSON or YAML string) with the given data. ...",
  "inputSchema": {
    "type": "object",
    "properties": {
      "template": { "type": "string", "description": "Template string (JSON or YAML)" },
      "data": { "type": "object", "description": "Data context as JSON object" },
      "format": { "type": "string", "enum": ["json", "yaml"], "default": "json" }
    },
    "required": ["template", "data"]
  }
}
```

## 7. 错误处理

- 渲染失败 (`RenderException`) → `result.isError = true`, content 含错误消息
- JSON-RPC 错误 (parse / method not found / 内部) → 标准 `error.code` / `error.message`

错误码:

| code | 含义 |
|---|---|
| -32700 | parse error (输入不是 JSON) |
| -32601 | method not found |
| -32603 | internal error |
| isError=true | tool 执行失败 (业务错误) |

## 8. 安全提示

⚠️ MCP server 直接执行模板, 模板可包含任意 Groovy 脚本 (`{=...}` / `{%...%}`) 和 HTTP 调用 (`@class: rest`)。

**只在可信环境部署**:

- ✅ 个人开发机器 (Claude Desktop / Cursor 本地)
- ✅ 内部容器, 限制网络出口
- ❌ 公网开放 (会被滥用)
- ❌ 不可信用户模板托管

如需 sandbox, 用自定义 `RenderComponent` 替换 `rest`, 加 URL 白名单 + 用 `GroovyCodeSource` 限制脚本能力。

## 9. 集成进自己的 MCP client

```python
# Python example: 通过 stdio 与 server 通信
import subprocess, json

proc = subprocess.Popen(
    ["java", "-cp", "template4j-core.jar", "fun.commons.template4j.core.McpServer"],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)

def call(method, params=None, id=1):
    req = {"jsonrpc": "2.0", "id": id, "method": method}
    if params: req["params"] = params
    proc.stdin.write(json.dumps(req) + "\n")
    proc.stdin.flush()
    return json.loads(proc.stdout.readline())

print(call("initialize"))
print(call("tools/call", {
    "name": "render_template",
    "arguments": {"template": "{\"v\":\"{@x}\"}", "data": {"x": 42}}
}, id=3))
```

## 10. 已知限制

- **stateless**: 每个请求新建 `RenderEngine`, 不能跨请求共享 bean / compile 缓存(后续可加 session)
- **不支持 streaming**: 当前一次输出完整 JSON, 不支持流式渲染
- **stdio only**: 不支持 HTTP / SSE transport (后续可加)

详见 `manual/13-mcp-server.md`。
