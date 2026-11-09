# 03 · 示例目录

> 8 个递进式学习示例，覆盖 template4j-core 的全部核心特性。每个示例是 `classpath:/examples/<id>.<ext>` 资源文件 + `index.json` 中的元数据。

## 1. 示例总览

| # | ID | 标题 | 类别 | 难度 | 引擎特性 |
|---|----|------|------|------|---------|
| 1 | `01-syntax-data` | 数据路径 `{@...}` | syntax | 1 | `{@path}` JSONPath |
| 2 | `02-syntax-vars` | 变量与作用域 `{$...}` | syntax | 2 | `{$name}` / `$x` 定义 / `$$` 求值 |
| 3 | `03-expression-groovy` | Groovy 脚本 `{=...}` `{%...%}` | expression | 3 | `{=expr}` / `{%block%}` / `@`→`data.` |
| 4 | `04-component-if` | 条件组件 `if` | component | 2 | `@class:if` / `select` / `then` / `else` / `is` |
| 5 | `05-component-each` | 循环组件 `each` | component | 3 | `@class:each` / `$key` / `$item` / `merge` |
| 6 | `06-component-rest` | HTTP 组件 `rest` | component | 4 | `@class:rest` / `url` / `success` / `error` |
| 7 | `07-include-outer` | 模板复用 `&include` | advanced | 4 | `&include:relative/url` 字符串形式 |
| 8 | `08-yaml-config` | YAML 配置式模板 | advanced | 3 | `.yaml` 解析 / YAML→JSON |

## 2. index.json 结构

```jsonc
{
  "examples": [
    {
      "id": "01-syntax-data",
      "title": "数据路径 {@...}",
      "category": "syntax",          // syntax | expression | component | advanced
      "difficulty": 1,                // 1-5
      "tags": ["{@}", "jsonpath"],
      "summary": "演示 @ 表达式从 data 中取值",
      "description_path": "01-syntax-data.md",  // 可选，详细说明
      "template_path": "01-syntax-data.json5",
      "data": { "user": { "name": "template4j" } },
      "expected_path": "01-syntax-data.expected.json"
    },
    // ...
  ]
}
```

## 3. 示例 1：数据路径 {@...}

### 模板（`01-syntax-data.json5`）

```json5
{
  // 从 data 中取 user.name
  "greeting": "{@user.name}",
  // 嵌入字符串
  "msg": "Hello, {@user.name}! You are {@user.role}.",
  // 整体取一个对象
  "userInfo": "{@user}"
}
```

### 数据

```json
{ "user": { "name": "template4j", "role": "admin" } }
```

### 预期输出

```json
{
  "greeting": "template4j",
  "msg": "Hello, template4j! You are admin.",
  "userInfo": { "name": "template4j", "role": "admin" }
}
```

### 学习要点

- `{@path}` 中 `path` 是 fastjson2 JSONPath 表达式
- `@` 在表达式中表示 data（root data context）
- 整体替换：当字符串只含一个表达式时，返回值可以是任意 JSON 类型（对象/数组/数字/布尔）

## 4. 示例 2：变量与作用域 {$...}

### 模板

```json5
{
  // 定义变量 $x，值是字面量（不渲染）
  "$x": "literal",

  // 定义变量 $y，先渲染后赋值（$$）
  "$$y": "{@user.name}",

  // 引用变量
  "out_x": "{$x}",
  "out_y": "{$y}",

  // 变量后接 JSONPath
  "upper": "{$y.upperCase}"
}
```

### 数据

```json
{ "user": { "name": "template4j" } }
```

### 预期

```json
{
  "out_x": "literal",
  "out_y": "template4j",
  "upper": "TEMPLATE4J"
}
```

### 学习要点

- `$name`：在 varMap 中查找名为 `$name` 的变量
- `$$name`：先对 value 做渲染，再赋值（用于引用 data 计算结果）
- 变量后可接 JSONPath：`{$var.path.to.sub}`

## 5. 示例 3：Groovy 脚本 {=...} {%...%}

### 模板

```json5
{
  // 简单表达式
  "sum": "{= 1 + 2 }",
  // 在脚本中访问 data（@ 自动改写为 data.）
  "upperName": "{= data.user.name.toUpperCase() }",
  // 多行脚本块
  "computed": "{%
    def list = [1, 2, 3, 4, 5]
    return list.sum() * 2
  %}",
  // 使用已注册的 StringUtils（默认 import）
  "abbrev": "{= StringUtils.abbreviate(data.user.name, 5) }"
}
```

### 数据

```json
{ "user": { "name": "template4j" } }
```

### 预期

```json
{
  "sum": 3,
  "upperName": "TEMPLATE4J",
  "computed": 30,
  "abbrev": "t...j"
}
```

### 学习要点

- `{= expr }` 单行表达式，`{% block %}` 多行脚本块（必须 return）
- `@user.name` 在 Groovy 中被改写为 `data.user.name`
- 默认 import：`java.util.*` / `com.alibaba.fastjson2.*` / `org.apache.commons.lang3.*`
- 可通过 `engine.withImport()` 增加导入

## 6. 示例 4：条件组件 `if`

### 模板

```json5
{
  "@class": "if",
  "select": "{@user.age}",
  "is": ">=18",
  "then": { "msg": "成年人" },
  "else": { "msg": "未成年" }
}
```

### 数据

```json
{ "user": { "age": 20 } }
```

### 预期

```json
{ "msg": "成年人" }
```

### 学习要点

- `@class:if` 触发 IfRenderer
- `select` 是判定值
- `is` 支持运算符：`true`/`false`/`null`/`blank`/`empty`/`>=N`/`<=N`/`==X` 等
- `then`/`else` 是分支结果

## 7. 示例 5：循环组件 `each`

### 模板

```json5
{
  "@class": "each",
  "select": "{@users}",
  "itemName": "user",
  "keyName": "idx",
  "body": {
    "id": "{$idx}",
    "name": "{$user.name}",
    "isAdult": "{= $user.age >= 18 }"
  },
  "merge": false,
  "nonnull": false
}
```

### 数据

```json
{ "users": [
  { "name": "Alice", "age": 20 },
  { "name": "Bob", "age": 17 }
]}
```

### 预期

```json
[
  { "id": 0, "name": "Alice", "isAdult": true },
  { "id": 1, "name": "Bob", "isAdult": false }
]
```

### 学习要点

- `select` 必须是数组或 Map（其它类型静默返回空数组）
- `body` 内可通过 `$item` / `$key` 访问当前元素
- `merge: true` 把数组结果打平合并
- `nonnull: true` 过滤 null

## 8. 示例 6：HTTP 组件 `rest`

### 模板

```json5
{
  "@class": "rest",
  "url": "https://api.github.com/repos/apache/maven",
  "method": "GET",
  "success": {
    "repo": "{$responseData.full_name}",
    "stars": "{$responseData.stargazers_count}"
  },
  "error": {
    "msg": "请求失败: {$responseData.status}"
  }
}
```

### 数据

```json
{}
```

### 预期（实际值随 GitHub API 响应变化）

```json
{ "repo": "apache/maven", "stars": 4321 }
```

### 学习要点

- `@class:rest` 调用 RestRenderer
- `url` / `method` / `headers` / `data` 配置请求
- `$responseData` 在 success/error 子模板中可用
- 仅在 `2xx` 状态码下进入 success；其它进入 error

## 9. 示例 7：模板复用 `&include`

### 模板（`07-include-outer.json5`）

```json5
{
  "&include": "./07-include-inner.json5",
  "mainMsg": "from outer"
}
```

### 内层模板（`07-include-inner.json5`）

```json5
{
  "innerMsg": "from inner",
  "shared": "inner-shared"
}
```

### 预期

```json
{
  "innerMsg": "from inner",
  "shared": "inner-shared",
  "mainMsg": "from outer"
}
```

### 学习要点

- `&include: <relative/path>` 字符串形式：把字符串替换为另一资源的渲染结果
- `&include` 对象形式：键值可以是 String 或 List<String>
- 路径相对于当前模板 URL 解析

## 10. 示例 8：YAML 配置式

### 模板（`08-yaml-config.yaml`）

```yaml
greeting: "Hello, {@user.name}!"
user:
  name: "{@user.name}"
  age: "{@user.age}"
isAdult: "{= @user.age >= 18 }"
```

### 数据

```yaml
user:
  name: template4j
  age: 20
```

### 预期（JSON 输出）

```json
{
  "greeting": "Hello, template4j!",
  "user": { "name": "template4j", "age": 20 },
  "isAdult": true
}
```

### 学习要点

- YAML 模板等价于 JSON 模板（引擎内部转 JSON）
- 适合做配置式输出（如代码生成）
- 通过 `engine.renderYaml()` 或 `engine.renderYamlUrl()`

## 11. 资源命名约定

| 文件 | 用途 |
|------|------|
| `<id>.json5` 或 `<id>.yaml` | 源模板 |
| `<id>.data.json` 或 `<id>.data.yaml` | 数据（可选，与模板同名加 `.data`） |
| `<id>.expected.json` | 预期渲染结果（用于集成测试断言） |
| `<id>.md` | 详细说明（可选） |
| `index.json` | 总目录 |

## 12. 难度等级与推荐学习路径

```
难度 1 (语法入门)
    └── 01-syntax-data
    └── 04-component-if

难度 2 (变量与组件)
    └── 02-syntax-vars
    └── 05-component-each

难度 3 (表达式)
    └── 03-expression-groovy
    └── 08-yaml-config

难度 4 (高级)
    └── 06-component-rest
    └── 07-include-outer
```

详细路径见 [04-learning-path.md](04-learning-path.md)。
