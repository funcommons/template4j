# template4j-demo

> template4j-core 的学习 / 集成测试 / 在线试用 demo 应用。

## 1. 定位

template4j-core 是一个 JSON / YAML 模板引擎（见 `template4j-core/CLAUDE.md`）。模板本身是 JSON 形态的对象，混合字面量与表达式（`{@...}` / `{$...}` / `{=…}` / `{%…%}`），并通过 `@class` 调用组件（`if` / `each` / `switch` / `invoke` / `render` / `const` / `rest`）。

template4j-demo 围绕该引擎搭建一个可运行的全栈应用，达成三大目标：

| 目标 | 实现路径 |
|------|---------|
| **系统学习** | 按知识梯度提供 8 个递进式示例（语法 → 表达式 → 组件 → Groovy 脚本 → 资源加载），每示例附说明文档、源模板、渲染结果 |
| **集成测试** | 用 Spring Boot 集成测试覆盖引擎关键路径（render / renderUrl / withBean / withImport / withComponent） |
| **在线试用** | 提供 Web playground：左侧编辑器、右侧实时预览，可切换 JSON / YAML 输入 |

## 2. 目录结构

```
template4j-demo/
├── README.md                         # 本文件
├── pom.xml                           # Maven 配置
├── docs/                             # 设计文档
│   ├── 01-architecture.md            # 架构与依赖
│   ├── 02-api.md                     # REST API 规范
│   ├── 03-examples.md                # 示例目录
│   ├── 04-learning-path.md           # 学习路径
│   ├── 05-integration-test.md        # 集成测试方案
│   └── 06-frontend.md                # 前端结构
├── src/
│   ├── main/
│   │   ├── java/fun/commons/template4j/demo/
│   │   │   ├── DemoApplication.java
│   │   │   ├── common/               # ApiResponse / ErrorCode / BizException / ApiError
│   │   │   ├── config/               # JacksonConfig / TraceIdFilter
│   │   │   ├── controller/           # RenderController / LearnController / TryController
│   │   │   ├── service/              # TemplateService / ExampleService
│   │   │   └── exception/            # GlobalExceptionHandler
│   │   └── resources/
│   │       ├── application.yml
│   │       └── examples/             # 学习示例（JSON / YAML 模板）
│   │           ├── 01-syntax-data.json5
│   │           ├── 02-syntax-vars.json5
│   │           ├── 03-expression-groovy.json5
│   │           ├── 04-component-if.json5
│   │           ├── 05-component-each.json5
│   │           ├── 06-component-rest.json5
│   │           ├── 07-include-outer.json5
│   │           ├── 08-yaml-config.yaml
│   │           └── index.json
│   └── test/
│       └── java/fun/commons/template4j/demo/
│           ├── RenderApiIT.java
│           ├── LearnApiIT.java
│           ├── TryApiIT.java
│           └── TemplateServiceIT.java
└── frontend/                         # Vue 3 + Element Plus 前端（独立 npm 项目）
    ├── package.json
    ├── vite.config.ts
    ├── src/
    │   ├── main.ts
    │   ├── api/client.ts
    │   ├── router/index.ts
    │   ├── stores/preference.ts
    │   ├── locales/{zh-CN,en-US}.ts
    │   ├── styles/...
    │   └── views/{Home,Learn,Try,Docs}.vue
    └── index.html
```

## 3. 运行方式

### 后端独立运行

```bash
mvn -pl template4j-demo spring-boot:run
# 默认端口 8088，访问 http://localhost:8088/api/examples 列出全部示例
```

### 前端独立运行（开发热更新）

```bash
cd template4j-demo/frontend
npm install
npm run dev    # 默认端口 5173，自动代理 /api → http://localhost:8088
```

### 一体化打包

```bash
cd template4j-demo/frontend && npm run build      # 产物 → frontend/dist
cd .. && cp -r frontend/dist src/main/resources/static
mvn -pl template4j-demo package                   # 产物 → target/template4j-demo.jar
java -jar target/template4j-demo.jar              # 访问 http://localhost:8088/
```

## 4. 三大目标对应文档

| 目标 | 详细文档 |
|------|---------|
| 系统学习 | [docs/04-learning-path.md](docs/04-learning-path.md) |
| 集成测试 | [docs/05-integration-test.md](docs/05-integration-test.md) |
| 在线试用 | [docs/02-api.md](docs/02-api.md) §playground + [docs/06-frontend.md](docs/06-frontend.md) |

## 5. 技术栈

| 层 | 选型 | 版本 |
|----|------|------|
| 引擎 | template4j-core | 0.5.0-SNAPSHOT |
| 后端 | Spring Boot | 3.4.1 |
| JSON | Jackson（snake_case + Long→String） | Spring Boot 管理 |
| 日志 | Log4j2 + SLF4J | Spring Boot 管理 |
| 测试 | JUnit 4 + spring-boot-starter-test | Spring Boot 管理 |
| 前端 | Vue 3 + Element Plus + Vite | ^3.5 / ^2.14 / ^6.0 |
| 国际化 | vue-i18n | ^9.14 |
| 路由 | vue-router | ^4.5 |
| 状态 | Pinia | ^2.3 |

## 6. 与 template4j-web 的关系

| 维度 | template4j-web | template4j-demo |
|------|----------------|-----------------|
| 定位 | 生产文档服务器 | 学习/试用 demo |
| 引擎使用 | 直接 new JsonRenderer() | 通过 TemplateService 封装 |
| API 风格 | 模板文件路由（/docs/*.jt 等） | REST 信封 + playground |
| 前端 | 无独立前端 | Vue 3 SPA |
| 测试 | JaCoCo 100% 覆盖 | 集成测试为主 |
| 配置 | `template4j.web.*` | `template4j.demo.*` |

demo 复用 web 中的 `.gsp` / `.jt` / `.yt` 渲染逻辑，但用 REST API 暴露而非 URL 路由。

## 7. 进一步阅读

- [架构设计](docs/01-architecture.md)
- [API 规范](docs/02-api.md)
- [示例目录](docs/03-examples.md)
- [学习路径](docs/04-learning-path.md)
- [集成测试方案](docs/05-integration-test.md)
- [前端结构](docs/06-frontend.md)
