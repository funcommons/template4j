# 05 · 集成测试方案

> 用 Spring Boot 集成测试覆盖 template4j-core 关键路径 + demo 自身 REST API。

## 1. 测试金字塔

```
                  ▲
                  │  E2E (Playwright, 5%)
                  │  ── 前端 SPA 端到端
                  │
              ┌───┴───┐
              │  IT  │  集成测试（25%）
              │      │  ── MockMvc + 真实 RenderEngine
              └───┬───┘
              ┌───┴───┐
              │  UT  │  单元测试（70%）
              │      │  ── TemplateService / ExampleService 隔离测试
              └───────┘
```

| 层级 | 数量目标 | 覆盖目标 |
|------|---------|---------|
| UT | ≥20 | TemplateService / ExampleService / DTO |
| IT | ≥10 | 全部 REST API + 端到端渲染 |
| E2E | 5 | 三个核心页面（Home / Learn / Try）的关键流程 |

## 2. 测试类清单

```
src/test/java/fun/commons/template4j/demo/
├── service/
│   ├── TemplateServiceUT.java          # 单测
│   └── ExampleServiceUT.java
├── api/
│   ├── RenderApiIT.java                 # MockMvc 集成测试
│   ├── LearnApiIT.java
│   ├── TryApiIT.java
│   ├── EngineApiIT.java
│   └── GlobalExceptionHandlerIT.java
├── integration/
│   ├── ExamplesRenderingIT.java         # 把 8 个示例全部渲染一遍 + 与 expected 比对
│   └── EngineConformanceIT.java         # 与 template4j-core 的契约测试
└── smoke/
    └── ApplicationStartupIT.java        # 启动 + 健康检查
```

## 3. 关键测试用例

### 3.1 UT：TemplateServiceUT

| 用例 | 输入 | 期望 |
|------|------|------|
| renderJson_basic | `{"msg":"{@user.name}"}` + `{user:{name:"x"}}` | `{msg:"x"}` |
| renderJson_null_data | `{"msg":"hello"}` + `{}` | `{msg:"hello"}` |
| renderYaml_basic | `msg: "{@user.name}"` | `{msg:"x"}` |
| renderUrl_classpath | url=`/examples/01-syntax-data.json5` | 渲染结果与示例 1 一致 |
| renderJson_invalidJson | `not a json` | 抛 BizException(TEMPLATE_PARSE_ERROR) |
| renderJson_renderError | `{"msg":"{= undefined }"}` | 抛 BizException(TEMPLATE_RENDER_ERROR) |
| engine_defaultBeansRegistered | engine() | bean "time"/"math"/"text" 已注册 |

### 3.2 UT：ExampleServiceUT

| 用例 | 输入 | 期望 |
|------|------|------|
| list_returnsAllExamples | — | 8 个示例 |
| load_existing | "01-syntax-data" | 含 id/title/template/expected |
| load_notFound | "999-not-exist" | 抛 BizException(EXAMPLE_NOT_FOUND) |
| load_navChain | "02-syntax-vars" | prev_id="01-syntax-data", next_id="03-expression-groovy" |
| data_parsing_json | "01" | data 是 Map |
| data_parsing_yaml | "08" | data 是 Map |

### 3.3 IT：RenderApiIT

```java
@Test
void render_json_basic() throws Exception {
    mockMvc.perform(post("/api/render")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "template_type": "json",
                  "template": "{ \\"msg\\": \\"{@user.name}\\" }",
                  "data": { "user": { "name": "template4j" } }
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.rendered.msg").value("template4j"))
        .andExpect(header().exists("X-Trace-Id"));
}
```

| 用例 | 端点 | 关键断言 |
|------|------|---------|
| render_json_basic | POST /api/render | 200 + code=0 + rendered.msg |
| render_yaml_basic | POST /api/render (yaml) | 200 + code=0 + rendered |
| render_invalid_json | POST /api/render | 200 + code=10301 |
| render_engine_error | POST /api/render | 200 + code=10300 |
| render_trace_id_propagated | POST /api/render + Header X-Trace-Id | 响应头 X-Trace-Id 一致 |
| render_idempotency_key_accepted | POST + Idempotency-Key | 200（demo 不做去重，仅接受 Header） |
| render_long_to_string | POST /api/render | JSON 中 Long 字段序列化为字符串 |

### 3.4 IT：LearnApiIT

| 用例 | 端点 | 关键断言 |
|------|------|---------|
| list_returnsExamples | GET /api/learn | total≥8 |
| get_existing | GET /api/learn/01-syntax-data | template 非空 + expected 是 Map |
| get_notFound | GET /api/learn/999-not-exist | code=10404 |
| get_first | GET /api/learn/01-syntax-data | prev_id=null |
| get_last | GET /api/learn/08-yaml-config | next_id=null |

### 3.5 IT：TryApiIT

| 用例 | 输入 | 期望 |
|------|------|------|
| try_basic | 标准 JSON 模板 | rendered + timing_ms≥0 |
| try_withTrace | options.with_trace=true | trace 数组非空 |
| try_templateSizeLimit | template 64KB+1 | code=10300 / 10301 |
| try_renderTimeout | template 含死循环 `{% while(true){} %}` | code=10300（超时） |
| try_yamlTemplate | YAML 模板 | rendered 正确 |

### 3.6 IT：ExamplesRenderingIT（端到端）

把所有 8 个示例从 classpath 加载并渲染，与 expected 文件对比。这是**最关键的回归测试**：任何对 template4j-core 的破坏性变更都会被它捕获。

```java
@SpringBootTest
class ExamplesRenderingIT {

    @Autowired ExampleService exampleService;
    @Autowired TemplateService templateService;

    @ParameterizedTest
    @MethodSource("allExampleIds")
    void render_matchesExpected(String id) throws Exception {
        ExampleDetail ex = exampleService.load(id);
        Object rendered = templateService.render(
            ex.getTemplateType(), ex.getTemplate(), ex.getData());
        Object expected = ex.getExpected();
        assertEquals(jsonString(expected), jsonString(rendered),
            "Example " + id + " mismatch");
    }

    static Stream<String> allExampleIds() {
        return Stream.of(
            "01-syntax-data", "02-syntax-vars", "03-expression-groovy",
            "04-component-if", "05-component-each", "06-component-rest",
            "07-include-outer", "08-yaml-config"
        );
    }
}
```

> 示例 6（rest）需用 MockWebServer 起 HTTP mock；其它示例纯 classpath。

### 3.7 IT：EngineConformanceIT

与 template4j-core 自身的契约断言，确保 demo 升级 template4j-core 时不会破坏。

| 用例 | 断言 |
|------|------|
| engine_componentsRegistered | `["rest","invoke","if","each","switch","const","render"]` 全部就绪 |
| engine_expressionFormsWork | 6 种表达式（`<&&>`/`{##}`/`{@}`/`{$}`/`{=}`/`{%...%}`）全部能求值 |
| engine_importsAvailable | `JSON`/`StringUtils` 在 Groovy 中可直接使用 |
| engine_renderUrl_works | `renderJsonUrl("classpath:/examples/01.json5", data)` 返回正确 |

### 3.8 IT：ApplicationStartupIT

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationStartupIT {

    @LocalServerPort int port;
    @Test void context_loads_and_healthEndpointResponds() {
        ResponseEntity<String> resp = new RestTemplate()
            .getForEntity("http://localhost:" + port + "/api/engine", String.class);
        assertEquals(200, resp.getStatusCode().value());
        assertTrue(resp.getBody().contains("\"components\""));
    }
}
```

## 4. E2E（Playwright）

`frontend/e2e/` 目录：

| 文件 | 用例 |
|------|------|
| `home.spec.ts` | 首页加载 + 标题/导航可见 |
| `learn.spec.ts` | 切换示例 + 渲染结果与 expected 一致 |
| `try.spec.ts` | 编辑模板 + 提交 + 看到结果 + 切换 YAML |
| `docs.spec.ts` | 文档页 markdown 渲染正确 |
| `i18n.spec.ts` | 切换英文 + UI 文本变化 |

## 5. 覆盖率目标

- **后端**：LINE ≥ 90%（与 mc-java-spec 一致）
- **前端**：语句 ≥ 80%

## 6. 测试数据

### 6.1 Examples 资源（src/main/resources/examples/）

| 文件 | 用途 |
|------|------|
| `index.json` | 总目录 |
| `<id>.json5` 或 `.yaml` | 8 个模板 |
| `<id>.data.json` | 配套数据（可选） |
| `<id>.expected.json` | 预期渲染结果 |

### 6.2 测试桩

| 文件 | 用途 |
|------|------|
| `src/test/resources/examples-broken/bad-json.json5` | 故意写错，触发 10301 |
| `src/test/resources/examples-broken/recursive-include.json5` | 触发 StackOverflowError（demo 应安全处理） |

## 7. 持续集成

```yaml
# .github/workflows/demo-ci.yml
name: demo-ci
on: { push: { paths: ['template4j-demo/**'] } }
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: 17, distribution: temurin }
      - run: mvn -pl template4j-demo -am verify
      - uses: actions/setup-node@v4
        with: { node-version: 20 }
      - run: cd template4j-demo/frontend && npm ci && npm run build
```

## 8. 性能与稳定性

- ExamplesRenderingIT 全部跑完应 < 5s（无网络 mock）
- rest 示例用 MockWebServer，避免真实 GitHub 调用
- 启动 IT 控制在 3s 内
