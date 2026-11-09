# template4j 使用手册

> 一份按图索骥、由浅入深的用户手册。每章给出**语法 + 可运行示例 + 与其它章的交叉引用**。

## 语言版本

| 语言 | 目录 | 状态 |
|---|---|---|
| 中文(默认) | [`zh-CN/`](./zh-CN/) | ✅ 完整 |
| English | [`en/`](./en/) | ⚠️ 暂未翻译,见 `en/README.md` |

章节结构以 `manifest.json` 为准,机器可读索引 + 双语标题/描述。

## 机器可读索引

`manifest.json` 含 13 章,每章有:
- `id` / `file` / `lines`
- `title.zh-CN` / `title.en-US`
- `desc.zh-CN` / `desc.en-US`
- `tags[]`

Agent 读这个文件,按用户语言推荐正确的章节。

## 目录(以中文版为例)

| # | 章节 | 内容 |
| :--- | :--- | :--- |
| 01 | [快速开始](zh-CN/01-quick-start.md) | 5 分钟跑通第一个模板(依赖、引擎、渲染) |
| 02 | [语法基础](zh-CN/02-syntax.md) | 5 种占位符、键前缀/后缀、转义 |
| 03 | [数据与变量](zh-CN/03-data-and-vars.md) | `@` 数据、`$`/`$$` 变量、`@@` 局部数据、`$enter{}`/`$return{}` |
| 04 | [Groovy 脚本](zh-CN/04-scripting.md) | `{% … %}` / `{= … }`、`@` 重写、内置 Func\* 工具(data/vars/render/load/cast/fne) |
| 05 | [方法 $name()](zh-CN/05-functions.md) | 用户定义方法、`_args`、`FuncObject`、递归守卫 |
| 06 | [内置组件](zh-CN/06-components.md) | `if` / `each` / `switch` / `invoke` / `render` / `const` / `rest` / `parallel` / `try` |
| 07 | [合并与包含](zh-CN/07-merge-and-include.md) | `*` 合并键、`&include` 模板嵌套 |
| 08 | [资源加载](zh-CN/08-resource-loading.md) | `renderUrl` / `renderJsonUrl` / `renderYamlUrl`、格式推断 |
| 09 | [自定义组件](zh-CN/09-custom-component.md) | 实现 `RenderComponent`、`engine.withComponent` |
| 10 | [最佳实践](zh-CN/10-best-practices.md) | 性能(预热/compile/缓存)、安全(可信模板)、可维护性 |
| 11 | [语法参考](zh-CN/11-grammar-reference.md) | 全量语法表(占位符/键前缀/组件/工具) |
| 12 | [CLI](zh-CN/12-cli.md) | 命令行工具:`render` / `version` / `help` |
| 13 | [MCP Server](zh-CN/13-mcp-server.md) | MCP stdio JSON-RPC 服务,给 Claude Desktop / Cursor 接入 |
