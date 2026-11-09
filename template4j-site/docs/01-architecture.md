# 01 · 架构设计

## 1. 依赖关系

```
┌─────────────────────────────────────────────────────────────┐
│                    template4j-demo (Spring Boot)             │
│                                                               │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐       │
│  │ Controller  │ -> │  Service    │ -> │  Engine     │       │
│  │  (REST)     │    │ TemplateSvc│    │ (Renderer)  │       │
│  └─────────────┘    └─────────────┘    └─────┬───────┘       │
│         │                                    │               │
│         │                                    │               │
│         v                                    v               │
│  ┌─────────────┐                    ┌───────────────┐        │
│  │ ExampleSvc  │                    │ template4j-core│        │
│  │ (resources) │                    │  (RenderEngine)│        │
│  └─────────────┘                    └───────────────┘        │
└─────────────────────────────────────────────────────────────┘
                              ▲
                              │
                    ┌───────────────────┐
                    │  Vue 3 SPA        │
                    │  (frontend/dist)  │
                    └───────────────────┘
```

## 2. 核心组件

### 2.1 TemplateService（template4j-core 的薄封装）

| 方法 | 入参 | 出参 | 用途 |
|------|------|------|------|
| `renderJson(templateJson, dataJson)` | String, String | Object | 渲染 JSON 模板（playground 用） |
| `renderYaml(templateYaml, dataJson)` | String, String | Object | 渲染 YAML 模板 |
| `renderUrl(url, type, data)` | String, String, Map | Object | 从 classpath:/examples/ 渲染 |
| `engine()` | — | RenderEngine | 暴露原始引擎用于高级用法 |

**初始化策略**：
- 单例 Spring bean
- 启动时调用 `RenderEngine.build()` 构造引擎
- 通过 `@PostConstruct` 注册 demo 专有 bean（`utils` / `time` / `math` 等），便于学习者在模板中直接调用

### 2.2 ExampleService（学习示例加载器）

| 方法 | 入参 | 出参 | 用途 |
|------|------|------|------|
| `list()` | — | `List<ExampleMeta>` | 列出所有示例（从 examples/index.json 读取） |
| `load(id)` | String | `Example` | 加载指定示例：源模板 + 说明 + 预期结果 |

**资源约定**：
- 路径：`classpath:/examples/<id>.json5` 或 `.yaml`
- 元数据：`classpath:/examples/index.json`
- 编码：UTF-8

### 2.3 Controller 拆分

| Controller | 路径前缀 | 职责 |
|-----------|---------|------|
| `RenderController` | `/api/render` | 通用渲染 API（JSON/YAML/URL 三种来源） |
| `LearnController` | `/api/learn` | 示例目录浏览与查询 |
| `TryController` | `/api/try` | Playground：提交模板 + 数据，返回渲染结果 + 日志 |
| `StaticController` | `/` | 服务前端 SPA（fallback 到 index.html） |

## 3. 关键类与文件清单

```
demo/
├── DemoApplication.java
├── common/
│   ├── ApiResponse.java         # 响应信封
│   ├── ApiError.java            # 错误子项
│   ├── BizException.java        # 业务异常
│   └── ErrorCode.java           # 错误码枚举
├── config/
│   ├── JacksonConfig.java       # snake_case + Long→String
│   ├── TraceIdFilter.java       # MDC + X-Trace-Id
│   ├── DemoProperties.java      # @ConfigurationProperties
│   ├── TemplateEngineConfig.java# RenderEngine bean 配置
│   └── WebMvcConfig.java        # CORS + SPA fallback
├── controller/
│   ├── RenderController.java
│   ├── LearnController.java
│   ├── TryController.java
│   └── GlobalExceptionHandler.java
├── service/
│   ├── TemplateService.java
│   └── ExampleService.java
└── dto/
    ├── RenderRequest.java
    ├── RenderResponse.java
    ├── ExampleMeta.java
    ├── ExampleDetail.java
    └── TryRequest.java
```

## 4. 关键流程

### 4.1 渲染流程（/api/render）

```
[Client]
  POST /api/render
  { "template_type": "json", "template": "{...}", "data": {...} }
       │
       v
[RenderController]
  1. 校验 RenderRequest（@Valid）
  2. 调 TemplateService.renderJson/Yaml
       │
       v
[TemplateService]
  3. engine.render(parsedTemplate, data)
       │
       v
[RenderEngine → JsonRenderer]
  4. parseStrTemplate → convertSelf → 表达式/组件求值
       │
       v
[Controller]
  5. 包装为 ApiResponse.success
       │
       v
[Client]
  HTTP 200
  { "code": 0, "data": <rendered>, "trace_id": "..." }
```

异常路径：
- 模板语法错误 → `RenderException` → `BizException(TEMPLATE_PARSE_ERROR)` → HTTP 200 + code=10301
- 模板求值错误 → `RenderException` → `BizException(TEMPLATE_RENDER_ERROR)` → HTTP 200 + code=10300

### 4.2 playground 流程（/api/try）

```
POST /api/try
{
  "template_type": "json",              // json | yaml
  "template": "{ \"msg\": \"{@name}\" }",
  "data": { "name": "template4j" },
  "options": {
    "with_trace": true,                 // 返回渲染过程 trace
    "with_timing": true                 // 返回耗时（ms）
  }
}

→
{
  "code": 0,
  "data": {
    "rendered": { "msg": "template4j" },
    "rendered_text": "{\"msg\":\"template4j\"}",
    "timing_ms": 12,
    "trace": [                          // 仅 with_trace=true
      {"step":"parse","detail":"..."},
      {"step":"convert_root","detail":"..."}
    ]
  },
  "trace_id": "abc123"
}
```

## 5. 配置项

`application.yml`：

```yaml
template4j:
  demo:
    examples:
      root: classpath:/examples        # 示例根目录
      cache: true                       # 启动时预加载
    playground:
      max_template_size: 65536          # 单次提交最大字节数
      timeout_ms: 5000                  # 渲染超时
    engine:
      register_default_beans: true      # 注册 utils/time/math bean
server:
  port: 8088
spring:
  jackson:
    default-property-inclusion: non_null
  servlet:
    multipart:
      max-request-size: 1MB
```

## 6. 与 template4j-core 的对接点

| demo 需求 | template4j-core API |
|----------|---------------------|
| 渲染 JSON 字符串 | `engine.renderJson(templateJson, data)` |
| 渲染 YAML 字符串 | `engine.renderYaml(templateYaml, data)` |
| 从 classpath 加载并渲染 | `engine.renderJsonUrl("/examples/01.json", data)` |
| 注册自定义 bean | `engine.withBean("time", new TimeBean())` |
| 注册自定义类导入 | `engine.withImport("org.apache.commons.lang3.StringUtils")` |
| 注册自定义组件 | `engine.withComponent(new MyComponent())` |
| 替换 TemplateLoader | `engine.withLoader(loader)` |
| 错误信息 | `RenderException.getMessage()` 含模板路径 |

## 7. 线程模型

- `RenderEngine` 是单例，所有请求共享
- `JsonRenderer` 内部 `ScriptEngine` 是 static，并发安全（每个 `eval` 用独立 `Bindings`）
- 每请求创建独立 `SelfObject` 树，无共享可变状态

## 8. 安全边界

- playground 限制模板大小（默认 64KB）和渲染超时（5s）
- Groovy 脚本无沙箱，**仅限本地学习使用**，不暴露公网
- 生产部署应加白名单/认证（demo 不实现）
