# 组件总览

模板中任意对象挂上 `@class` 键即可调起内置组件。自定义组件：实现 `RenderComponent`，暴露 `name()` 与 `render(RenderContext)`，经 `engine.withComponent(...)` 注册。

| 组件 | 类 | 用途 |
| --- | --- | --- |
| `if` | `IfRenderer` | 条件分支：`select` + `then` / `else`；`is` 支持 true / false / null / blank / empty 及取反 |
| `each` | `EachRenderer` | 循环：数组或 Map；`$key` / `$item` 变量名可配；`merge` 展平、`nonnull` 去空 |
| `switch` | `SwitchRenderer` | 字符串分支：`select` 匹配 `cases`，回退 `default` |
| `invoke` | `InvokeRenderer` | 调用 Bean 方法：`engine.withBean(...)` 注册后按名调用 |
| `render` | `RenderRenderer` | 递归渲染 `select`（late-binding / 子模板） |
| `const` | `ConstRenderer` | 原样返回 `select`，不做渲染 |
| `rest` | `RestRenderer` | HTTP 编排（见下） |
| `parallel` | `ParallelRenderer` | 并发执行 `tasks` 并以 `body` 聚合；整体 deadline、`continueOnError` |
| `try` | `TryRenderer` | 错误隔离：`fallback` / `onError` / `rethrow` |

## rest 编排组件

基于 JDK `java.net.http.HttpClient`（运行时零 Spring）：

```json
{
  "@class": "rest",
  "url": "/users/{@id}",
  "method": "GET",
  "headers": { "Accept": "application/json" },
  "timeout": 3000,
  "retry": { "count": 3, "backoff": "exponential", "baseMs": 100, "retryOn": "5xx" },
  "cache": { "key": "user:{@id}", "ttl": 60000 },
  "auth": { "type": "bearer", "token": "{@token}" },
  "circuitBreaker": { "threshold": 5, "resetSec": 30 },
  "success": { "userName": "{$responseData.name}" },
  "error": { "fallback": true }
}
```

| 子属性 | 说明 |
| --- | --- |
| `url` / `method` / `headers` / `data` | 请求四要素；相对路径走 baseUrl |
| `timeout` | 单请求超时（ms，默认 30000） |
| `retry` | count + backoff（fixed/linear/exponential）+ retryOn（5xx/network/all） |
| `cache` | 进程内有界 LRU（默认 1024 条），命中返回 defensive deep copy |
| `auth` | bearer / basic |
| `circuitBreaker` | CLOSED / OPEN / HALF_OPEN 三态熔断（resilience4j） |
| `success` / `error` | 分支模板；`$responseData` 携带响应数据 |

**安全边界**：SSRF 白名单 `template4j.rest.allowed-hosts` fail-closed（不在白名单一律拒绝）；响应体积上限 `template4j.rest.maxResponseBytes`（默认 2MB）；响应数据永远按数据处理，`@class` 等键不会被二次执行（语义由专项测试钉死）。

## 变量与上下文

- `$name` / `$name()`：当前节点局部变量（`$name()` 包装为函数，防无限递归）
- `$$`：先渲染后入变量
- `$enter{}` / `$return{}`：子节点渲染前/后的 Groovy 钩子，可读写 `_template` / `_result`
- `*` 键：渲染后条目合并进父对象
- Groovy 内可直接使用 `data` / `vars` / `render` / `load` / `cast` / `fne`，以及 `withBean` 注册的 Bean
