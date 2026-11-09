# Contributing to template4j

感谢你的贡献！本指南说明如何提交改动。

## 开发环境

- JDK 17+（推荐 21）
- Maven 3.8+
- 无需额外环境（Lombok 在编译期处理）

## 第一次构建

```bash
git clone <your-fork>
cd template4j
mvn -B verify
```

`verify` 包含 compile + test + JaCoCo 覆盖率 gate。如果失败，看 `template4j-core/target/site/jacoco/index.html` 找未覆盖的分支。

## 提交 PR

1. **Fork + branch**：从 `main` 拉 feature branch，命名 `feat/xxx` / `fix/xxx` / `docs/xxx`。
2. **测试**：`mvn -B verify` 必须全绿。新增功能要补测试。
3. **覆盖率**：JaCoCo 阈值 `0.95 (LINE) / 0.85 (BRANCH)`。新增代码尽量达到 100%，不要让阈值下降。
4. **代码风格**：
   - 4 空格缩进，无 tab
   - UTF-8，LF 换行（核心源码），manual/ 文档可用 CRLF（但鼓励 LF）
   - 中文注释 OK，但要清晰
5. **CHANGELOG**：在 `[0.5.0-SNAPSHOT]` 区块下加一行（Added / Changed / Fixed / Tests 中合适的分类）。
6. **PR 描述**：附 benchmark / 截图 / 链接相关 issue。

## Commit message

不强制 conventional commits，但建议：

```
feat(parallel): support dynamic task count from data
fix(rest): handle null response body on 204
docs(manual): clarify backoff formula
test(coverage): cover defensiveCopy serialization failure
```

## CI

`main` 分支受保护。PR 必须满足：

- ✅ `mvn -B verify`（4 模块全绿）
- ✅ 至少 1 个 review approval
- ✅ 分支与 main 同步（无 merge conflict）

CI 跑：
- JDK 17 + 21 matrix
- macOS + Ubuntu matrix
- JaCoCo gate
- benchmark 不阻塞合并，但结果会打印

## 测试规范

- 优先 JUnit 5（JUnit 4 已全部迁移）
- 用 `@TempDir`、`@ParameterizedTest` 等内置工具
- mock HTTP 用 JDK 内置 `com.sun.net.httpserver.HttpServer`（参考 `RestRendererServerTest`）
- 性能测试放 `PerformanceBenchmarkTest`（默认 skip，`-Pbenchmark` 启用）

## 新增组件

`@class: <name>` 组件实现 `RenderComponent`：

```java
public class MyRenderer implements RenderComponent {
    @Override public String name() { return "mycomp"; }

    @Override
    public Object render(RenderContext ctx) throws Exception {
        ctx.assertField("select");
        Object sel = ctx.renderField("select");
        // ...
    }
}
```

注册：在 `ComponentRegistry.registerAllDefaults` 加 `register(new MyRenderer())`。

文档：在 `manual/06-components.md` 加章节，至少包含 schema 表 + 2 个例子。

测试：见 `ParallelRendererTest` / `RestRendererServerTest` 风格。

## 安全 / 可信模板

template4j 模板可执行任意 Groovy（`{=...}` / `{%...%}`）。**永远不要**在不可信环境部署 MCP server 或 demo。如果在生产接收用户模板，必须：

- 自定义 `RenderComponent` 替换 `rest`，加 URL 白名单
- 用 Groovy CodeSource + `SecureASTCustomizer` 限制脚本（独立大坑，PR 欢迎讨论）

## 问题 / 讨论

- GitHub Issues：bug 报告 + feature request
- 不开 Discord / Slack，所有讨论留在 GitHub
