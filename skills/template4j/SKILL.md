---
name: template4j
description: 在 Java / Spring Boot 项目中集成 template4j (JSON/YAML 模板引擎)。支持 9 个内置组件 (if/each/switch/render/invoke/const/rest/parallel/try)、Groovy 脚本、HTTP 编排、Schema 校验、MCP/CLI 工具。触发词:模板引擎、template4j、JSON 模板、YAML 模板、模板渲染、并行编排、组件、if 组件、each 组件、rest 组件、parallel 组件、try 组件、占位符、{@x}、{$x}、{=expr}、{% block %}、脚本、JSONPath、模板热重载、API 编排、BFF、声明式 API 编排、声明式模板。
version: 1.0.0
enabled: true
metadata:
  type: library-installer
  category: backend
  language: java
  tags: [template-engine, json, yaml, groovy, http, parallel, bff, orchestrator]
  spec-version: v1
  examples: [examples/TemplateController.java, examples/Smoke.java, examples/templates/greeting.json, examples/templates/bff-user-profile.json]
---

# template4j 集成 Skill

> 把 `template4j-core` 集成进用户的 Java / Spring Boot 项目。Skill 内不含脚本——所有操作由本 Skill 的阅读者(Agent)按以下步骤直接执行。

## 0. 用户速查

| 你想 | 入口 |
|---|---|
| 加 Maven / Gradle 依赖 | [§3 安装依赖](#3-安装依赖) |
| 生成 Spring Boot 模板控制器脚手架 | [§4 脚手架](#4-脚手架) |
| 跑冒烟测试(确认能渲染) | [§5 冒烟测试](#5-冒烟测试) |
| 查 9 个内置组件的语法 | [§6 9 个内置组件速查](#6-9-个内置组件速查) |
| 暴露给 AI Agent (Claude Desktop / Cursor) | [§7 MCP Server](#7-mcp-server) |
| 退出本 Skill | 「退出 template4j」 |

## 1. 元信息

| 项 | 说明 |
|---|---|
| 技术栈 | Java 17+ / Groovy 4 / fastjson2 / commons-text |
| **适用** | Spring Boot 后端 / 任何 Java 17+ 项目;BFF / API 编排 / 配置渲染 / 报表生成 |
| **不适用** | 前端模板(→ Vue Mustache / Handlebars);Node.js(→ nunjucks / eta) |
| 核心原则 | **JSON-shaped 优先,字符串 fallback**;占位符 5 种;组件用 `@class` 注入 |
| 版本 | 当前最新稳定版:0.5.0 |
| 退出 | 「退出 template4j」 |

## 2. 全局铁律

1. **JDK 17+** — 项目已用 `maven-enforcer-plugin` 锁住,低于 17 直接报错。
2. **占位符 5 种,不要混用**:
   - `{@path}` — JSONPath 读 data
   - `{$var}` — 读 var / 组件结果
   - `{= groovy_expr }` — 内联 Groovy 表达式
   - `{% groovy_block %}` — 多行 Groovy(必须 `return`)
   - `<& literal &>` — 字面输出,不解析
3. **`@class: "name"` 是组件入口** — 写在 JSON 对象的 key 上,值是组件名(`if` / `each` / `rest` / `parallel` / `try` / `render` / `invoke` / `const` / `switch`)。
4. **JSON-shaped 模板是首选** — 字符串模板占位符缺失时渲染为 JSON `null`(不是 `""`),下游 `parseObject` 才能保住 key。
5. **Groovy 表达式里 `@x` 自动翻译为 `data("x")`** — 别在 Groovy 里手写 `data.get('x')`。
6. **HTTP 组件走 JDK 自带 `HttpClient`**,无第三方依赖;不要引入 RestTemplate。
7. **`parallel` 组件用共享线程池(16)** — 别自己 `new Thread`;超过 16 个任务会排队。
8. **`try` 组件不暴露原始 `Throwable`** — `$error.message` / `$error.cause` / `$error.path`,不要把 stack trace 渲染到模板里。
9. **schema 校验** — `engine.withInputSchema(json)` / `engine.withOutputSchema(json)` 用 networknt JSON Schema,传入字符串即可。
10. **trace 零开销** — 不调用 `withTraceConsumer()` 就不会触发任何额外代码,生产环境可不传。

## 3. 安装依赖

### 3.1 选择安装方式

模板4j 提供两种集成方式:

| 方式 | 适用场景 | 所需前置 |
|---|---|---|
| **方式一:JitPack**(推荐) | template4j 已推到 GitHub release/tag | 用户项目有外网访问 JitPack |
| **方式二:本地 install** | template4j 仅在本机 | 用户的 dev 机器能访问源码仓库 |

### 3.2 方式一:JitPack(推荐,无需手动 install)

前提:template4j 已经推到 GitHub release/tag(如 `v0.5.0`)。

**步骤 1:在用户项目 `pom.xml` 加 JitPack 仓库**

在 `<project>` 节点下、与 `<dependencies>` 平级,加:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

**步骤 2:在 `<dependencies>` 里加模板4j 依赖**

```xml
<dependencies>
    <!-- 已有依赖 ... -->

    <!-- template4j: declarative JSON/YAML template engine -->
    <dependency>
        <groupId>com.github.funcommons.template4j</groupId>
        <artifactId>template4j</artifactId>
        <version>v0.5.0</version>
    </dependency>
</dependencies>
```

> JitPack 的 groupId 是 `com.github.{owner}` 形式,artifactId 通常是仓库名(去掉 `.git`)。version 用 GitHub tag。

**步骤 3:验证**

```bash
mvn -q dependency:resolve -DincludeArtifact=com.github.funcommons:template4j
```

成功 → 跳到 §4。
失败 → 检查网络 / JitPack 是否可达 / GitHub release tag 是否存在。失败则改用方式二。

### 3.3 方式二:本地 install

**步骤 1:拉源码并 install 到本地 Maven 仓库**

```bash
git clone https://github.com/funcommons/template4j.git /tmp/template4j
cd /tmp/template4j
mvn -DskipTests install
```

成功后,本地 `~/.m2/repository/fun/commons/template4j-core/0.5.0/` 下会有 `template4j-core-0.5.0.jar`。

> 如果用户机器已 clone 过 template4j 源码,跳过 clone,直接 `cd <path> && mvn -DskipTests install`。

**步骤 2:在用户项目 `pom.xml` 加依赖**

```xml
<dependencies>
    <!-- 已有依赖 ... -->

    <!-- template4j: declarative JSON/YAML template engine -->
    <dependency>
        <groupId>fun.commons</groupId>
        <artifactId>template4j-core</artifactId>
        <version>0.5.0</version>
    </dependency>
</dependencies>
```

**步骤 3:验证**

```bash
mvn -q dependency:resolve -DincludeArtifact=fun.commons:template4j-core
```

### 3.4 Gradle 用户

```gradle
// build.gradle (Groovy DSL)
implementation 'fun.commons:template4j-core:0.5.0'

// build.gradle.kts (Kotlin DSL)
implementation("fun.commons:template4j-core:0.5.0")
```

JitPack 走 Gradle 需在 `settings.gradle` 加 `maven { url = uri("https://jitpack.io") }`。

## 4. 脚手架

模板4j 不需要 Spring Boot(纯 Java 也能用),但 Spring Boot 是最常见场景。

### 4.1 检测项目类型

读用户 `pom.xml`,确认:
- 已有 `org.springframework.boot:spring-boot-starter-web` 依赖 → 继续 §4.2
- 没有 → 提示用户先加 Spring Boot 依赖,或跳过此节,直接跳 §5 用纯 Java 测
- 是 Gradle → 在 `build.gradle` 加 `implementation 'org.springframework.boot:spring-boot-starter-web'`,然后继续

### 4.2 生成 TemplateController

1. 找用户 Java 包路径:在 `src/main/java/` 下递归找一个已有的 `.java` 文件,读它的 `package` 声明作为 `TARGET_PKG`。
2. 如果 `src/main/java/` 是空的,默认用 `com.example.demo`,自动创建 `src/main/java/com/example/demo/`。
3. 把 Skill 里的 `examples/TemplateController.java` 复制到 `src/main/java/<TARGET_PKG-as-path>/TemplateController.java`。
4. 替换文件里的 `__PACKAGE__` 占位符为 `TARGET_PKG`。
5. **如果目标文件已存在,拒绝覆盖**——提示用户删除或重命名。
6. 不要修改 `TemplateController.java` 的方法签名(`renderGet` / `renderPost` / `doRender`),Agent 在使用时可改业务逻辑,不要在脚手架里改。

### 4.3 启动验证

```bash
mvn -q spring-boot:run
```

另开终端:

```bash
curl 'http://localhost:8080/api/render?template=%7B%22v%22%3A%22Hello%2C%20%7B%40name%7D%22%7D&data=%7B%22name%22%3A%22world%22%7D'
# 期望返回: {"rendered":{"v":"Hello, world"}}
```

成功 → 进入 §5。

## 5. 冒烟测试

5 个用例覆盖核心 API,在用户项目里跑一次,确保依赖装好。

### 5.1 准备 Smoke.java

读 Skill 的 `examples/Smoke.java`,复制到用户项目根目录(不进 `src/`,作为一次性脚本)。

### 5.2 跑测试

```bash
cd <user-project>
mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt -Dmdep.includeScope=runtime
CP="target/classes:$(cat /tmp/cp.txt)"
javac -d /tmp/build -cp "$CP" Smoke.java
java -cp "$CP:/tmp/build" Smoke
```

期望输出(每行 `[name] OK`):

```
[string-placeholder] OK
[groovy-expr] OK
[if-pass] OK
[each] OK
[nulls-raw] OK
```

最后一行 `Passed: 5 / 5`。

**5 个用例说明**:

| 名称 | 验证内容 |
|---|---|
| `string-placeholder` | `{@name}` 替换 data 字段 |
| `groovy-expr` | `{= @n * 2 }` 内联 Groovy 计算 |
| `if-pass` | `@class:if` 组件,select=true,is=true → 走 then |
| `each` | `@class:each` 组件,数组 → 每项渲染 body |
| `nulls-raw` | JSON-shaped 模板,缺失字段渲染为 JSON null(不是 `""`) |

### 5.3 失败处理

| 错误 | 原因 | 修复 |
|---|---|---|
| `ClassNotFoundException: fun.commons.template4j.core.RenderEngine` | 依赖没装 | 回到 §3 重新装 |
| `NoClassDefFoundError: org/codehaus/groovy/...` | 缺 groovy-jsr223 | 升级 template4j-core ≥ 0.5.0(已自带 groovy 4 传递依赖) |
| 占位符 `{@x}` 没替换 | 模板用了 String 而不是 JSONObject | 改用 `{"key": "{@x}"}` 形式 |

## 6. 9 个内置组件速查

| 组件 | 用途 | 关键字段 |
|---|---|---|
| `if` | 条件分支 | `select` (条件值) + `is` (谓词) + `then` / `else` |
| `each` | 数组 / map 迭代 | `select` + `body` + `keyName` (默认 `key`) + `itemName` (默认 `item`) |
| `switch` | 多分支 | `select` + `cases` (map) + `default` |
| `render` | 嵌套渲染 | `select` (子模板) + `data` (新 data) |
| `invoke` | 调用 bean / lambda | `select` (方法名) + `args` |
| `const` | 返回原值 | `select` |
| `rest` | HTTP 调用 | `url` + `method` (GET/POST/...) + `data` + `timeout` / `retry` / `cache` / `auth` / `circuitBreaker` |
| `parallel` | 并发执行 | `tasks` (map) + `body` (聚合) + `timeout` (整体) + `continueOnError` |
| `try` | 错误隔离 | `select` + `fallback` + `onError` + `rethrow` |

完整 API 见 `manual/06-components.md`。

## 7. MCP Server

让 Claude Desktop / Cursor 等 AI Agent 通过 JSON-RPC 调用 template4j 渲染模板。

**步骤 1**:在用户项目加 MCP 端点(可选——`template4j-site` 已有现成的 `/api/demo/mcp`):

参考 Skill 仓库里 `template4j-site` 的 `McpController.java` 模式。

**步骤 2**:用户启动 MCP server:

```bash
java -jar template4j-core-shaded.jar mcp
```

> shade jar 需用 `mvn -Pshade -pl template4j-core package -DskipTests` 构建。

**步骤 3**:在 Claude Desktop 配置 `claude_desktop_config.json`:

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

AI Agent 现在可以调 `render_template` 工具,传入 `template` 和 `data`,拿到渲染结果。

## 8. 进阶:场景化模板示例

Skill 仓库里 `examples/templates/` 目录提供了两个生产可用模板:

| 文件 | 场景 | 用法 |
|---|---|---|
| `greeting.json` | 简单 Greeting,含日期格式化 + 等级判断 | 复制到 `src/main/resources/templates/`,`engine.renderJson(loadFromClasspath("templates/greeting.json"), data)` |
| `bff-user-profile.json` | BFF 并发拉 user + orders,聚合返回 | 复制到 `src/main/resources/templates/`,配置 rest 组件的 `url` / `auth` |

## 9. 关键文件索引

| 路径 | 用途 |
|---|---|
| `SKILL.md` | 本文件,集成手册(快速开始 / 安装 / 脚手架 / 冒烟 / 速查) |
| `manual/` | **软链接** → 仓库根 `manual/`(13 章详细文档,2319 行,分 `zh-CN/` 和 `en/` 子目录) |
| `manual/manifest.json` | 章节机器可读索引(标题/描述中英双语,`path.zh-CN` 指向文件) |
| `examples/TemplateController.java` | Spring Boot 控制器脚手架(68 行) |
| `examples/Smoke.java` | 5 用例冒烟测试(82 行) |
| `examples/templates/greeting.json` | 简单模板(8 行) |
| `examples/templates/bff-user-profile.json` | BFF 并行编排模板(24 行) |

## 9.1 深度参考(在 Skill 软链接的 `manual/` 下,按需读取)

**目录结构**:
```
manual/
├── README.md           ← 语言无关总览(指向 zh-CN/ 或 en/)
├── manifest.json       ← 机器可读索引(每章 id/标题/描述/路径/tags)
├── zh-CN/              ← 中文正文(13 章,完整)
└── en/                 ← 英文版(暂未翻译,README.md 占位说明)
```

**机器可读索引**:`manual/manifest.json`
- `manifest.language.default = "zh-CN"`、`supported = ["zh-CN", "en-US"]`
- 每章含 `id` / `lines` / `title.{lang}` / `desc.{lang}` / `path.{lang}` / `tags[]`
- `path.zh-CN` 总是有值,`path.en-US` 暂为 `null`(待翻译)
- Agent 应先 Read 这个 JSON,根据用户语言(locale)挑选 `title[lang]` / `desc[lang]` 推荐章节

**章节索引**(参考用,权威数据以 `manifest.json` 为准;路径以 `zh-CN/` 为例):

| 场景 | 读哪一章 |
|---|---|
| 第一次跑通,看 5 行代码 | `manual/zh-CN/01-quick-start.md` |
| 占位符 5 种语法(JSONPath / var / Groovy / block / literal) | `manual/zh-CN/02-syntax.md` + `manual/zh-CN/11-grammar-reference.md` |
| 业务数据怎么传(嵌套 map / 数组 / 标量) | `manual/zh-CN/03-data-and-vars.md` |
| Groovy 脚本:return / 闭包 / `$vars` / `@` 重写 | `manual/zh-CN/04-scripting.md` |
| `data` / `render` / `load` / `cast` / `fne` 5 个内置函数 | `manual/zh-CN/05-functions.md` |
| **9 个内置组件详解**(if/each/switch/render/invoke/const/rest/parallel/try) | `manual/zh-CN/06-components.md` |
| `&include` / 模板合并 / 嵌套 | `manual/zh-CN/07-merge-and-include.md` |
| classpath / URL / 文件系统加载 | `manual/zh-CN/08-resource-loading.md` |
| 自定义组件(实现 `RenderComponent` + `withComponent`) | `manual/zh-CN/09-custom-component.md` |
| 性能 / 安全 / 可维护 实践 | `manual/zh-CN/10-best-practices.md` |
| **CLI** 子命令(render / watch / mcp / version) | `manual/zh-CN/12-cli.md` |
| **MCP Server** (Claude Desktop / Cursor 接入) | `manual/zh-CN/13-mcp-server.md` |

**i18n 状态**:
- ✅ 章节标题/描述:中英双语(在 `manifest.json`)
- ✅ 章节正文:中文完整(在 `zh-CN/`);英文版占位(在 `en/README.md`)
- Agent 遇到用户用英文咨询时:**用 `manifest.json` 的 `title.en-US` / `desc.en-US` 告诉用户章节内容,然后读 `zh-CN/` 的内容**(告知用户可让用户决定是否继续读)

> **调用规则**:Agent 接到 template4j 任务时,先用本 SKILL.md 判断场景;进入具体场景后再按上表 `Read` 对应 `manual/zh-CN/*.md` 文件,不要预读全部。

## 10. 与其他 Skill 协作

| 涉及 | 同时参考 |
|---|---|
| 模板引擎基础 / 跑测试 | 父项目的 `run-template4j-core` Skill |
| Java 代码规范 | `mc-java-spec` |
| Spring Boot 集成 | `mc-java-spec` §6 |
| API 错误码(模板渲染 10500 等) | `mc-api-spec` |
| HTTP 安全(rest 组件鉴权) | `mc-java-security` |

**集成原则**:`template4j` 是工具,业务逻辑写在 Spring Boot Controller,模板是 data 的"形状"声明,不是核心代码。

## 11. 快速失败

| 错误现象 | 原因 | 修复 |
|---|---|---|
| `ClassNotFoundException: fun.commons.template4j.core.RenderEngine` | 依赖没装 | 回到 §3 |
| `GroovyShell 加载失败` | 缺 groovy-jsr223 传递依赖 | 升级 template4j-core ≥ 0.5.0 |
| 占位符 `{@x}` 不替换 | 模板是字符串,不是 JSONObject | 改用 `{"key": "{@x}"}` 形式 |
| `parallel` 死锁 | 任务数 > 线程池 16 | 拆分大任务,或增大 `template4j.parallel.poolSize` 系统属性 |
| `try` 暴露了 stack trace | 用了 `ex.printStackTrace()` 而不是 `$error` | 改用模板里的 `{$error.message}` |
| `rest` 调用超时 | 没设 `timeout` 子属性 | 加 `"timeout": 3000` (ms) |
