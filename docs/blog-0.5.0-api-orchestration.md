---
title: "template4j: declarative API orchestration in JSON"
date: 2026-07-XX
author: justin
tags: [java, json, template-engine, bff, api-orchestration, open-source]
---

# template4j: declarative API orchestration in JSON

> 本文首发于 2026-07。代码: [github.com/.../template4j](https://github.com/.../template4j),文档: [manual/](../manual/README.md)。

## TL;DR

`template4j` 是一个 JSON 模板引擎,但它的真实价值不在"模板",而在**"声明式 API 编排"**。本文用一个 BFF 场景讲清楚:

- 怎么用 30 行 JSON 完成"并发拉 3 个接口 + 超时降级 + 自动熔断 + 响应聚合"
- 为什么同类产品(JSLT / JOLT / Handlebars)做不了这件事
- 与 Spring Cloud Gateway / Kong 等网关方案的差异

## 问题:BFF 编排的痛苦

假设你在写一个移动端"用户详情"接口,后端要聚合 3 个微服务:

```
GET /mobile/user/{id}
  ├── GET /users/{id}              (用户基础)
  ├── GET /users/{id}/orders       (订单)
  └── GET /payments?user={id}      (支付)
```

要求:
- 3 个调用**并发**
- 整体超时 **300ms**
- 支付接口挂了用**缓存降级**,不阻塞主流程
- 用户接口连续失败 5 次**熔断** 60 秒

### 传统方案:Java + CompletableFuture

```java
@GetMapping("/mobile/user/{id}")
public CompletableFuture<Map<String, Object>> mobile(@PathVariable String id) {
    CompletableFuture<User> userF = userService.get(id);
    CompletableFuture<List<Order>> ordersF = orderService.list(id);
    CompletableFuture<Payment> paymentF = paymentService.find(id)
        .exceptionally(ex -> paymentCache.get(id));

    return CompletableFuture.allOf(userF, ordersF, paymentF)
        .orTimeout(300, TimeUnit.MILLISECONDS)
        .thenApply(_ -> Map.of(
            "user", userF.join(),
            "orders", ordersF.join(),
            "payment", paymentF.join()
        ));
}
```

问题:
1. **熔断**要另接 Resilience4j,代码膨胀
2. 业务逻辑改一行,Java 代码要重新打包部署
3. 不支持运行时配置(改超时阈值要重启)
4. 看不出整体结构,新人理解成本高

### 模板方案:30 行 JSON

```json
{
  "@class": "parallel",
  "tasks": {
    "user": {
      "@class": "try",
      "select": {
        "@class": "rest",
        "url": "/users/{@id}",
        "circuitBreaker": { "threshold": 5, "resetSec": 60 }
      },
      "fallback": { "error": "user service down" }
    },
    "orders": { "@class": "rest", "url": "/users/{@id}/orders" },
    "payment": {
      "@class": "try",
      "select": { "@class": "rest", "url": "/payments?user={@id}" },
      "fallback": { "@class": "rest", "url": "/cache/payments/{@id}" }
    }
  },
  "timeout": 300,
  "body": {
    "user": "{$user}",
    "orders": "{$orders}",
    "payment": "{$payment}"
  }
}
```

3 个 `rest` 调用并发,整体 300ms 超时,user 和 payment 单独 try/catch + 降级,user 加熔断。

存储在数据库 / 配置中心 / 文件,改一行重启即可生效。

## 为什么 template4j 能做到?

template4j 把 JSON 当作"声明式编程语言"。三个关键设计:

### 1. JSON 即模板

模板本身就是合法 JSON:

```json
{
  "name": "{@name}",
  "age": "{@age}"
}
```

`{@name}` 是占位符,渲染时用数据替换。整个 JSON 树递归渲染,不需要 DSL。

### 2. `@class` 组件系统

任何对象加 `@class: <name>` 字段,引擎把整个子树交给该组件处理:

```json
{
  "@class": "rest",        // ← 触发 HTTP 调用
  "url": "...",
  "method": "get"
}
```

内置 9 个组件:`if` / `each` / `switch` / `invoke` / `render` / `const` / `rest` / **`parallel`** / **`try`**。可以通过 `engine.withComponent(new MyRenderer())` 注册自定义组件。

### 3. 内嵌 Groovy(可选)

需要复杂表达式时:

```json
{
  "v": "{= @orders.size() > 0 ? 'vip' : 'normal' }"
}
```

`{=...}` 求值 Groovy 表达式,`{%...%}` 执行 Groovy 块。`@` 自动重写为 `data.`,所以模板里 `@orders` === `data.orders`。

## 同类对比

| 产品 | 类型 | API 编排 | JSON 模板 | HTTP 内置 | 熔断 | 评估 |
|---|---|---|---|---|---|---|
| **template4j** | JSON 模板引擎 | ✅ 一等公民 | ✅ | ✅ rest 组件 | ✅ 0.5+ | ★★★★★ |
| JSLT (Schibsted) | JSON→JSON 转换 | ❌ | ✅ | ❌ | ❌ | 转换强,编排弱 |
| JOLT (Bazaarvoice) | 声明式 spec | ❌ | spec | ❌ | ❌ | 无表达式 |
| Handlebars.java | 文本模板 | ❌ | ❌ | ❌ | ❌ | 非 JSON |
| Spring Cloud Gateway | 网关 | ✅ | ❌(YAML route) | ✅ | ✅ | 太重,要 Spring 生态 |
| Kong / Tyk | API gateway | ✅ | ❌ | ✅ | ✅ | Lua/插件,非 Java 嵌入 |

template4j 的差异化:**嵌入 Java 应用 + JSON 原生 + 完整编排三件套**。

## 一个真实例子:GitHub User 卡片

要求:输入 GitHub 用户名,输出展示卡片 JSON。包含:
- 用户基础信息
- 最近 5 个 repo
- repo 总 star 数(并发算 5 个 repo 的 star 求和)

```json
{
  "@class": "parallel",
  "tasks": {
    "user": { "@class": "rest", "url": "https://api.github.com/users/{@login}" },
    "repos": { "@class": "rest", "url": "https://api.github.com/users/{@login}/repos?per_page=5" }
  },
  "body": {
    "login": "{$user.login}",
    "name": "{$user.name}",
    "avatar": "{$user.avatar_url}",
    "followers": "{$user.followers}",
    "topRepos": {
      "@class": "each",
      "select": "{$repos}",
      "body": {
        "name": "{$item.name}",
        "stars": "{$item.stargazers_count}"
      }
    },
    "totalStars": "{= $repos.sum { it.stargazers_count } }"
  }
}
```

调用:

```bash
curl localhost:8080 -d '{"login": "torvalds"}'
```

输出:

```json
{
  "login": "torvalds",
  "name": "Linus Torvalds",
  "avatar": "https://avatars.githubusercontent.com/u/1024025?v=4",
  "followers": 198000,
  "topRepos": [
    { "name": "linux", "stars": 175000 },
    { "name": "subsurface", "stars": 1500 }
  ],
  "totalStars": 176500
}
```

整个聚合逻辑在 30 行 JSON 里,可热重载,可版本控制,可单元测试(用 HttpServer mock)。

## 与网关方案的关键区别

| 维度 | template4j | 网关 (Gateway / Kong) |
|---|---|---|
| 部署 | 嵌入应用 | 单独集群 |
| 模板来源 | 数据库 / 配置中心 / 文件 | YAML / Lua |
| 表达式 | Groovy(完整) | SpEL / 限制 Lua |
| HTTP 调用 | `rest` 组件一等公民 | filter 链 |
| 学习曲线 | 30 分钟(JSON) | 1-2 周(生态) |
| 适合场景 | 单应用 BFF / 数据聚合 | 全公司 API 出口 |

**不互斥**:可以 template4j 在应用内做聚合,再用网关统一鉴权 / 限流。

## 0.5.0 新增

- `parallel` / `try` 组件 + `rest` 增强(timeout/retry/cache/auth/circuitBreaker)
- `compile()` API + LRU 缓存 → 热路径 5.7x
- `withTraceConsumer` 调试追踪
- `withInputSchema` / `withOutputSchema` fail-fast 校验
- CLI(MCP server for AI agents)

## 路线图

- **真异步 HTTP**(CompletableFuture + sendAsync)— 进一步降低 parallel 延迟
- **模板预编译为 Lambda** — 性能再 5-10x
- **watch dev server** — 模板热重载
- **官网 + showcase demo** — 把"声明式 API 编排"叙事可视化

## 试用

```bash
# Maven
<dependency>
  <groupId>fun.commons</groupId>
  <artifactId>template4j-core</artifactId>
  <version>0.5.0</version>
</dependency>

# 或 CLI (打 fat jar 后)
mvn -Pshade -pl template4j-core package -DskipTests
java -jar template4j-core/target/template4j-core-shaded.jar render github-card.jt --data-file torvalds.json
```

文档: [manual/](../manual/README.md) 13 章 · GitHub: [template4j](https://github.com/.../template4j) · 1359 个测试全绿。

---

**讨论**:Hacker News / Reddit r/java / V2EX 欢迎反馈。
