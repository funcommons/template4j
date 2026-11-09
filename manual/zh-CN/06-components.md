# 06 内置组件

模板中遇到 `"@class": "<name>"` 字段时，引擎把整个子树交由该名字对应的 `RenderComponent` 处理。本章列出 9 个内置组件。

## 一、统一约定

每个组件都有以下共性：

- **触发**：`"@class": "<组件名>"`，或简写 `"@<组件名>"`（见 §八）。
- **`select`**：组件的"输入"。不同组件解读不同。
- **简写形式**：很多组件支持 `@class|method:value` 或 `@class|method` 单行写法。
- **嵌套**：组件内可继续出现其它组件，包括自身。

## 二、`@class: if` —— 条件分支

```json
{
    "@class": "if",
    "select": "{= @result.status == 200 }",
    "then": { "code": 0, "message": "成功" },
    "else": { "code": "{@result.status}", "message": "失败" }
}
```

| 属性 | 说明 | 默认 |
| :--- | :--- | :--- |
| `select` | 测试值（必填） | — |
| `is` | 判定方式：`true` / `false` / `null` / `blank` / `empty` / `!xxx` | `true` |
| `then` / `true` | 测试通过时返回的节点 | — |
| `else` / `false` | 测试未通过时返回的节点 | — |

`is` 的判定语义：

- `true`：`BooleanUtils.toBooleanObject(select) == TRUE`（兼容 `"yes"`/`"on"`/`"1"` 等）
- `false`：反之
- `null`：select == null
- `blank`：null 或 trim 后空字符串
- `empty`：null 或不含子元素（List/Map/String/Optional/数组）
- `!xxx`：上述的取反

### 简写

```json
{
    "@if|!blank": "{@parameters.id}",
    "then": "id = :id"
}
```

等价于完整写法。

### 典型场景：动态 SQL

```json
{
    "select": "id, name, age",
    "from": "user",
    "where": {
        "@each": [
            { "@if|!blank": "{@parameters.id}", "then": "id = :id" },
            { "@if|blank":  "{@parameters.name}", "else": "name LIKE :name" }
        ]
    },
    "limit": "1, 100"
}
```

## 三、`@class: each` —— 循环

```json
{
    "@class": "each",
    "select": "{@likes}",
    "body": { "k": "{$key}", "v": "{$item}" }
}
```

| 属性 | 说明 | 默认 |
| :--- | :--- | :--- |
| `select` | 数组 / Map（必填） | — |
| `body` | 每次迭代渲染的节点；缺失则用 `{$item}` | — |
| `keyName` | 索引 / Map key 的变量名 | `key` |
| `itemName` | 元素的变量名 | `item` |
| `merge` | body 返回 List 时，是否把元素平铺进结果数组 | `true` |
| `nonnull` | 是否过滤掉 body 返回的 null | `true` |

### Map 遍历

```json
{
    "@class": "each",
    "select": "{@friends}",
    "body": "{=$key}, {=$item.name}, {$item.age}岁"
}
```

`$key` 是 Map 的 key（如 `"tom"`），`$item` 是对应的 value。

### body 返回类型

- body 返回非数组 → 作为结果数组的一个元素。
- body 返回数组 → 与 `merge` 配合：
  - `merge: true`（默认）：数组元素平铺进结果。
  - `merge: false`：数组元素作为单元素保留（结果变成二维）。

```json
{
    "@class": "each",
    "select": [1, 2, 3],
    "body": ["{$key}", "{$item}"]
}
```

`merge: true` → `[0,1,1,2,2,3]`  
`merge: false` → `[[0,1],[1,2],[2,3]]`

## 四、`@class: switch` —— 分支表

```json
{
    "@class": "switch",
    "select": "{@color}",
    "red":   { "code": 0, "hex": "#FF0000" },
    "blue":  { "code": 0, "hex": "#0000FF" },
    "green": { "code": 0, "hex": "#00FF00" },
    "default": { "code": -1, "hex": "未知" }
}
```

`select` 渲染后转字符串，作为 key 在同级兄弟中查找。**大小写敏感**。Number 会尝试匹配 `9 ↔ 9.0`。

## 五、`@class: invoke` —— 调用方法

```json
{
    "@class": "invoke",
    "select": "{= StringUtils }",
    "method": "firstNonBlank",
    "args": [" ", null, "abc"]
}
```

| 属性 | 说明 |
| :--- | :--- |
| `select` | 被调对象 / 类 / Groovy 表达式字符串 |
| `method` | 方法名 |
| `args` | 参数数组（或单个值） |

简写：

```json
{
    "@invoke|firstNonBlank": "{= StringUtils}",
    "args": [" ", null, "abc"]
}
```

也可单行文本：`"@invoke|firstNonBlank:{= StringUtils}"`（args 用 args 字段或默认空）。

> 静态方法也可以直接写：`{= StringUtils.firstNonBlank(@a, @b) }`，不必用 `invoke` 组件。`invoke` 适合"方法名/参数是动态"的场景。

## 六、`@class: render` —— 二次渲染

```json
{
    "@class": "render",
    "select": {
        "name": "{@name}",
        "greeting": "Hello {@name}"
    }
}
```

`select` 先被渲染一次。如果还提供了 `data`，会**用新数据**再渲染一次（适合晚绑定）。

### 动态加载模板

```json
{
    "$var1": "1",
    "@render": "{= templates.find('temp' + var1) }"
}
```

配合 `engine.withBean("templates", ...)` 实现"模板名 → 模板对象"的动态分发。

## 七、`@class: const` —— 不渲染

```json
{
    "@class": "const",
    "select": "{% return 1 %}"
}
```

`select` **不渲染**，原样作为结果返回。常配合 `&include`、`@render` 使用。

等价简写：

```json
{
    "@const": "{% return 1 %}"
}
```

或键名以 `@` 结尾：

```json
{
    "result@": "{% return 1 %}"
}
```

## 八、`@class: rest` —— HTTP 客户端

```json
{
    "@class": "rest",
    "url": "https://api.example.com/users/{@userId}",
    "method": "get",
    "headers": { "X-Token": "{@token}" },
    "data": { "expand": "profile" },
    "dataType": "json",
    "success": {
        "code": 0,
        "user": "{$responseData.data}"
    },
    "error": {
        "code": "{$responseData.status}",
        "msg": "{$responseData.message}"
    }
}
```

| 属性 | 说明 | 默认 |
| :--- | :--- | :--- |
| `url` / `select` | HTTP URL（必填） | — |
| `method` | `get` / `post` / `put` / `delete` | `get` |
| `data` | GET 拼 query；其它方法发 body（JSON） | — |
| `headers` | 自定义 header | — |
| `dataType` | `auto` / `json-object` / `json-array` / `yaml-object` / `yaml-array` / `string` | `auto` |
| `success` | 成功时把 `$responseData` 渲染为模板 | — |
| `error` | 失败时把 `$responseData`（含 `status` / `message`）渲染为模板 | — |

简写：

```json
"hot": "@rest|get:https://www.sina.com.cn/api/hotword.json"
```

`Content-Type` 为 `application/x-www-form-urlencoded` 或 `multipart/form-data` 时，`data` 会作为表单字段发送，否则默认 JSON。

### 8.1 增强子属性（生产编排）

`rest` 还支持以下子属性，专为 API 编排场景设计。所有增强都是可选的。

| 属性 | 类型 | 说明 | 默认 |
| :--- | :--- | :--- | :--- |
| `timeout` | number(ms) | 单次请求超时。超时走 `error` 分支（`$error.message` 含 `timeout`） | `30000` |
| `retry` | object | 重试策略：`{ count, backoff, baseMs, retryOn }` | 无重试 |
| `cache` | object | 响应缓存：`{ key, ttl }`。命中缓存不发起网络请求 | 无缓存 |
| `auth` | object | 鉴权注入：`{ type, token }` 或 `{ type, user, pass }` | 无 |
| `circuitBreaker` | object | 熔断器：`{ threshold, resetSec }` (v0.5+) | 无 |

`retry` 子属性：

| 字段 | 说明 | 默认 |
| :--- | :--- | :--- |
| `count` | 最大重试次数（不含首次） | `0` |
| `backoff` | `fixed` / `linear` / `exponential` | `fixed` |
| `baseMs` | 退避基准毫秒。设 `B = baseMs`, 第 N 次重试 (1-based) 等待: `fixed` = B; `linear` = B × N; `exponential` = B × 2^(N-1) | `100` |
| `retryOn` | 触发重试的条件：`5xx` / `network` / `all`（默认 `5xx,network`） | `5xx,network` |

`cache` 子属性：

| 字段 | 说明 |
| :--- | :--- |
| `key` | 缓存键（必填），通常是模板表达式如 `"{@userId}"` |
| `ttl` | 缓存有效期（ms），过期后下次请求重新拉取 |

`auth` 子属性：

| `type` | 必填字段 | 注入 header |
| :--- | :--- | :--- |
| `bearer` | `token` | `Authorization: Bearer <token>` |
| `basic`  | `user` + `pass` | `Authorization: Basic <base64(user:pass)>` |

`circuitBreaker` 子属性：

| 字段 | 说明 | 默认 |
| :--- | :--- | :--- |
| `threshold` | 连续失败次数达到此值后熔断 (5xx / 网络层失败都计入) | 无 (必填) |
| `resetSec` | 熔断后多少秒放一个 half-open 探测请求; 成功则关闭熔断, 失败则继续 open | 无 (必填) |

熔断状态机：`CLOSED` → `OPEN`（连续失败达 threshold）→ `HALF_OPEN`（resetSec 后下一次请求）→ `CLOSED`/`OPEN`。

- 熔断打开时不再发起 HTTP 请求, 直接走 `error` 模板（或抛 `HttpException`）。
- 2xx 成功立即重置 failureCount; 5xx / 网络失败累加。
- 状态按 `method + url` 全局共享, 跨 `RestRenderer` 实例。
- 主要用于"防止下游故障放大到上游", 与 `retry` 配合可"先重试再熔断"。

```json
{
    "@class": "rest",
    "url": "/api/orders",
    "auth": { "type": "bearer", "token": "{@token}" },
    "timeout": 5000,
    "retry": { "count": 3, "backoff": "exponential", "baseMs": 200, "retryOn": "5xx,network" },
    "cache": { "key": "orders:{@userId}", "ttl": 60000 },
    "success": "{$responseData}"
}
```

> 缓存为进程内（`ConcurrentHashMap` + 时间戳），适合短期去重（如多次渲染同一模板）。多实例部署请使用外部缓存。熔断器（`circuitBreaker`）与响应 schema 校验（`validate`）为后续版本规划。

## 九、`@class: parallel` —— 并发编排

并行执行多个子任务（典型为多个 `@class: rest` 调用），所有结果聚合后作为变量供 `body` 渲染。专为 BFF/API 聚合场景设计。

```json
{
    "@class": "parallel",
    "tasks": {
        "user":   { "@class": "rest", "url": "/users/{@userId}" },
        "orders": { "@class": "rest", "url": "/users/{@userId}/orders" }
    },
    "body": {
        "userName": "{$user.name}",
        "orderCount": "{$orders.size()}"
    }
}
```

| 属性 | 说明 | 默认 |
| :--- | :--- | :--- |
| `tasks` | 任务映射，键为变量名，值为子模板（必填） | — |
| `body` | 全部任务完成后渲染的模板，可访问 `tasks` 中每个键 | — |
| `timeout` | 整体超时（毫秒）。任一任务未在时限内返回则走 `error` | `30000` |
| `error` | 任一任务抛异常、或整体超时时的降级模板。`$error` 含 `task` / `message` / `cause` / `path` | — |

**语义细节**：

- 每个 `tasks` 子项是一个完整的子模板，可内嵌任意组件（包括再次嵌套 `parallel`）。
- 任务间无依赖时，所有任务同时启动，共享 `RestRenderer` 的全局 `HttpClient` 线程池。
- `body` 中以 `{$taskName}` 访问任务结果（与 `tasks` 的键同名）。
- 默认情况下任一任务失败即整体失败，走 `error` 分支。`continueOnError: true` 时失败任务结果为 `null`，其余正常聚合。
- 任务结果不渲染（即直接取子模板执行结果），如需再加工用 `body` 二次组合。

```json
{
    "@class": "parallel",
    "tasks": {
        "primary":   { "@class": "rest", "url": "/primary" },
        "secondary": { "@class": "rest", "url": "/secondary" }
    },
    "continueOnError": true,
    "body": {
        "primary": "{$primary}",
        "secondary": "{$secondary}"
    },
    "error": {
        "msg": "all tasks failed",
        "task": "{$error.task}"
    }
}
```

> 与 `rest` 的 `success`/`error` 分支不同，`parallel` 的 `error` 是单一聚合分支，不区分是哪个任务失败。如需精细控制，单个 `rest` 任务自带 `error` 子模板；`parallel` 的 `error` 仅兜底未捕获的异常。

## 十、`@class: try` —— 错误隔离与降级

包装子模板执行，捕获异常并切换到 `fallback` / `onError` 分支，避免局部失败拖垮整张模板。

```json
{
    "@class": "try",
    "select": { "@class": "rest", "url": "/flaky-service" },
    "fallback": { "cached": true, "value": "{$lastKnownValue}" },
    "onError": { "@class": "rest", "url": "/log", "method": "post", "data": { "msg": "{$error.message}" } }
}
```

| 属性 | 说明 | 默认 |
| :--- | :--- | :--- |
| `select` | 待包装的子模板（必填） | — |
| `fallback` | `select` 抛异常时的降级结果 | — |
| `onError` | `select` 抛异常时的副作用模板（如打日志、上报）。先 `onError` 后取 `fallback` | — |
| `rethrow` | `true` 时跳过 `fallback` 渲染，但 `onError` 仍执行，最终把原异常上抛。用于"先记录再上抛"场景 | `false` |

**语义细节**：

- `select` 抛出任何 `Exception`（含 `HttpException`、`RenderException`）都进入降级流程。
- 进入降级流程时，`$error` 变量提供：`message`（异常消息）、`cause`（Throwable 类型简名）、`path`（渲染路径）。不暴露原始异常对象，避免向模板泄露内部堆栈/类路径。
- `fallback` 与 `onError` 都可选：仅有 `fallback` 时直接返回它；仅有 `onError` 时执行后返回 `null`；两者都有时先执行 `onError`（用于副作用）再返回 `fallback`。
- 两者都缺省时等价于未使用 `try`，异常正常向上抛。
- `rethrow=true` 时跳过 `fallback` 渲染，但 `onError` 仍执行（"先记录再上抛"）。

```json
{
    "@class": "try",
    "select": { "@class": "rest", "url": "/payments/{@id}" },
    "onError": { "@class": "rest", "url": "/audit", "method": "post", "data": { "ctx": "{$error.path}", "err": "{$error.message}" } },
    "rethrow": true,
    "fallback": { "status": "unknown" }
}
```

> `try` 是组件级的"软失败"机制。配合 `parallel` 的 `continueOnError` 可实现"尽力聚合 + 局部降级"模式。

## 十一、组件简写规则

```text
{"@class": "<name>", "select": "<sel>"}                    →  {"@<name>": "<sel>"}
{"@class": "<name>", "select": "<sel>", "method": "<m>"}   →  {"@<name>|<m>": "<sel>"}
{"@class": "<name>", "select": "<sel>", "method": "<m>"}   →  "@<name>|<m>:<sel>"   （仅当 select 是字符串）
```

举例：

```json
{"@class":"if","select":"...","is":"blank"}    →  {"@if|blank":"..."}
{"@class":"rest","url":"...","method":"get"}   →  "@rest|get:..."
{"@class":"const","select":"..."}              →  {"@const":"..."}
```

## 十二、自定义组件

详见 [09 自定义组件](09-custom-component.md)。简言之：

```java
public class UpperRenderer implements RenderComponent {
    @Override public String name() { return "upper"; }
    @Override public Object render(RenderContext ctx) {
        Object v = ctx.getValue("select");   // 自动渲染
        return v == null ? null : v.toString().toUpperCase();
    }
}

engine.withComponent(new UpperRenderer());

// 模板
"{ \"out\": { \"@upper\": \"hello world\" } }"
// → {"out":"HELLO WORLD"}
```