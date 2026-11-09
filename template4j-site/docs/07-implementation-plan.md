# 07 · 实现计划

> 实施前的总规划：阶段拆分、任务清单、估时、风险、验收标准。**先批准计划，再实施**。

## 1. 阶段总览

| 阶段 | 内容 | 估时 | 依赖 | 可交付物 |
|------|------|------|------|---------|
| P0 | 后端最小骨架 + 健康 | 1h | — | mvn package 通过；GET /api/engine 返回 200 |
| P1 | 后端核心 API | 2h | P0 | /api/render /api/learn /api/try 全部可用 |
| P2 | 8 个学习示例 + 集成测试 | 3h | P1 | ExamplesRenderingIT 全绿 |
| P3 | 前端 Vue 骨架 | 2h | P1 | Learn/Try/Docs/Home 四页面可访问 |
| P4 | 前端业务页面打磨 | 2h | P3 | UI 完整可用、国际化、错误提示 |
| P5 | 一体化打包 + 文档收尾 | 1h | P2,P4 | java -jar 可启动，README 完整 |
| **合计** | | **11h** | | |

每阶段交付后必须满足：

- `mvn -pl template4j-site verify` 通过
- 覆盖率 LINE ≥ 90%（与 mc-java-spec 一致）
- 关键 IT 全绿

## 2. P0：后端最小骨架（1h）

### 任务

| # | 任务 | 工时 |
|---|------|------|
| P0-1 | 父 pom 注册 module + template4j-site/pom.xml | 5 min |
| P0-2 | SiteApplication + DemoProperties | 10 min |
| P0-3 | common/{ApiResponse, ApiError, BizException, ErrorCode} | 15 min |
| P0-4 | config/{JacksonConfig, TraceIdFilter} | 15 min |
| P0-5 | exception/GlobalExceptionHandler | 10 min |
| P0-6 | controller/EngineController（GET /api/engine） | 10 min |
| P0-7 | application.yml | 5 min |
| P0-8 | ApplicationStartupIT（启动 + GET /api/engine） | 15 min |
| P0-9 | mvn verify 通过 + 覆盖率达标 | 15 min |

### 验收

```bash
mvn -pl template4j-site spring-boot:run
curl http://localhost:8088/api/engine
# 应返回 200 + JSON 信封
```

## 3. P1：后端核心 API（2h）

### 任务

| # | 任务 | 工时 |
|---|------|------|
| P1-1 | service/TemplateService（render Json/Yaml/Url + engine 暴露） | 30 min |
| P1-2 | service/ExampleService（list + load + 数据缓存） | 30 min |
| P1-3 | dto/{RenderRequest, RenderResponse, ExampleMeta, ExampleDetail, TryRequest, TryResponse} | 30 min |
| P1-4 | controller/RenderController | 20 min |
| P1-5 | controller/LearnController | 15 min |
| P1-6 | controller/TryController | 15 min |
| P1-7 | 8 个 UT（TemplateServiceUT + ExampleServiceUT） | 30 min |
| P1-8 | 5 个 IT（RenderApiIT / LearnApiIT / TryApiIT / EngineApiIT / HandlerIT） | 30 min |

### 验收

```bash
curl -X POST http://localhost:8088/api/render -H 'Content-Type: application/json' \
  -d '{"template_type":"json","template":"{\"msg\":\"{@user.name}\"}","data":{"user":{"name":"x"}}}'
# 应返回 {"code":0,"data":{"rendered":{"msg":"x"},...}}

curl http://localhost:8088/api/learn
# 应返回 8 个示例

curl http://localhost:8088/api/learn/01-syntax-data
# 应返回第一个示例详情
```

## 4. P2：8 个学习示例 + 集成测试（3h）

### 任务

| # | 任务 | 工时 |
|---|------|------|
| P2-1 | examples/index.json 编写 | 15 min |
| P2-2 | 示例 1（数据路径） | 15 min |
| P2-3 | 示例 2（变量） | 20 min |
| P2-4 | 示例 3（Groovy 脚本） | 20 min |
| P2-5 | 示例 4（if 组件） | 15 min |
| P2-6 | 示例 5（each 组件） | 20 min |
| P2-7 | 示例 6（rest 组件 + MockWebServer 测试桩） | 30 min |
| P2-8 | 示例 7（&include） | 20 min |
| P2-9 | 示例 8（YAML） | 15 min |
| P2-10 | ExamplesRenderingIT 全量回归 | 30 min |
| P2-11 | EngineConformanceIT 契约测试 | 15 min |

### 验收

- 全部 8 个示例渲染结果与 `.expected.json` 完全一致
- `ExamplesRenderingIT` 8/8 通过
- 修改任意一个核心 template4j-core 文件 → IT 至少有 1 个失败（回归保护）

## 5. P3：前端 Vue 骨架（2h）

### 任务

| # | 任务 | 工时 |
|---|------|------|
| P3-1 | 复制 mc-webui-spec/example/ → template4j-site/frontend/ | 5 min |
| P3-2 | 改 package.json（项目名 / proxy 配置） | 5 min |
| P3-3 | 改 nav-config.ts（导航：Home/Learn/Try/Docs） | 10 min |
| P3-4 | 改 brands.ts（保留 mchuan，注释其他） | 5 min |
| P3-5 | 改 locales/{zh-CN,en-US}.ts（应用文案） | 20 min |
| P3-6 | api/client.ts（axios 拦截器，对齐场景三模板） | 15 min |
| P3-7 | types/api.ts（ApiResponse<T>/RenderRequest/ExampleMeta 等） | 15 min |
| P3-8 | api/{render,learn,try}.ts（接口封装） | 20 min |
| P3-9 | router/index.ts（4 路由） | 5 min |
| P3-10 | stores/{preference,try}.ts | 15 min |
| P3-11 | npm run build 通过 | 5 min |

### 验收

- `npm run dev` + `mvn spring-boot:run` → 浏览器访问 http://localhost:5173 看到首页
- 切换 locale / theme / brand 生效
- 浏览器控制台 0 错误

## 6. P4：前端业务页面打磨（2h）

### 任务

| # | 任务 | 工时 |
|---|------|------|
| P4-1 | Home.vue（特性卡片 + CTA 按钮） | 30 min |
| P4-2 | Learn.vue（左侧示例列表 + 右侧详情 + 上/下导航） | 40 min |
| P4-3 | Try.vue（双编辑器 + 结果面板 + trace） | 40 min |
| P4-4 | Docs.vue（markdown 渲染 docs/ + CLAUDE.md） | 20 min |
| P4-5 | 通用组件：TemplateEditor / DataEditor / ResultPanel | 30 min |
| P4-6 | E2E：home / learn / try 各 1 个 spec | 30 min |

### 验收

- 用户在 Try 页面输入 `{"msg":"{@user.name}"}` + data `{"user":"demo"}` → 看到结果 `{"msg":"demo"}`
- Learn 页面切换 8 个示例均能正确渲染
- Docs 页面渲染 README.md 和 docs/*.md
- E2E 5/5 通过

## 7. P5：一体化打包 + 文档收尾（1h）

### 任务

| # | 任务 | 工时 |
|---|------|------|
| P5-1 | frontend/ → src/main/resources/static/ 构建产物拷贝脚本 | 15 min |
| P5-2 | WebMvcConfig：SPA fallback（GET 非 /api → index.html） | 15 min |
| P5-3 | README 完善（运行方式 + 截图） | 20 min |
| P5-4 | CHANGELOG.md | 10 min |
| P5-5 | 最终回归：mvn verify + cd frontend && npm run build | 10 min |
| P5-6 | 一键启动验证：java -jar target/template4j-site.jar | 10 min |

### 验收

- `java -jar template4j-site.jar` 启动 → 浏览器 http://localhost:8088 → 全功能可用
- README 描述的 3 种运行方式（mvn / npm dev / jar）全部可用

## 8. 风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| ~~template4j-core 的 spring-web 在 demo 中没传递~~ (已过期: core 已用 JDK HttpClient, 无 Spring 依赖) | — | — | 无需缓解 |
| ExamplesRenderingIT 在 rest 示例上失败 | 高 | 中 | 用 MockWebServer 起本地 HTTP mock |
| Groovy 脚本死循环阻塞 playground | 低 | 高 | TryController 用 Future + timeout(5s) 包装渲染 |
| 前端构建产物路径错配导致 jar 内找不到 | 中 | 中 | 用 `spring-boot:repackage` + resource filter 验证 |
| `&include` 相对路径解析与 template4j-core 行为不一致 | 低 | 中 | 示例 7 用绝对路径 `examples/07-include-inner.json5` 避免歧义 |
| 中文 i18n 文案含英文逗号导致解析错误 | 低 | 低 | 用 i18n-ally / IDE 检查 |

## 9. 资源清单

### 9.1 后端 Java 类（27 个）

```
common/        4 (ApiResponse, ApiError, BizException, ErrorCode)
config/        4 (JacksonConfig, TraceIdFilter, DemoProperties, TemplateEngineConfig, WebMvcConfig)
controller/    4 (EngineController, RenderController, LearnController, TryController)
service/       2 (TemplateService, ExampleService)
exception/     1 (GlobalExceptionHandler)
dto/           6 (RenderRequest, RenderResponse, ExampleMeta, ExampleDetail, TryRequest, TryResponse)
SiteApplication.java
```

### 9.2 资源文件（11 个）

```
application.yml
examples/index.json
examples/01..08 (模板 + 数据 + expected)
```

### 9.3 前端文件（约 30 个）

参考 [06-frontend.md](06-frontend.md) §1。

### 9.4 测试文件（约 18 个）

| 类型 | 文件数 |
|------|-------|
| UT (service) | 2 |
| IT (api) | 5 |
| IT (integration) | 2 |
| IT (smoke) | 1 |
| E2E (playwright) | 5 |
| 测试资源（broken examples） | 3 |

## 10. 验收 Checklist

### 后端

- [ ] `mvn -pl template4j-site verify` 通过
- [ ] LINE 覆盖率 ≥ 90%
- [ ] 8 个示例渲染 IT 全绿
- [ ] /api/render /api/learn /api/try /api/engine 四个端点全部返回正确信封
- [ ] trace_id 双通道（Header + body）一致
- [ ] Long 字段在 JSON 中是字符串

### 前端

- [ ] `npm run build` 通过
- [ ] createWebHistory（非 Hash）
- [ ] 切换 locale / theme / brand 持久化
- [ ] ID/金额字段全部 string 类型
- [ ] axios 写操作带 Idempotency-Key
- [ ] 5 个 E2E 测试通过

### 一体化

- [ ] `java -jar target/template4j-site.jar` 单独启动可访问全部功能
- [ ] README 三种运行方式均验证
- [ ] docs/ 6 篇文档全部成稿

## 11. 实施前的批准流程

1. **本计划评审**（你 + 我同步对齐）：30 min
2. **修订计划**（如需）：15 min
3. **逐阶段实施**：按 P0 → P5 顺序，每阶段交付后 demo 一次
4. **每阶段验收**：跑 checklist + 你确认才进入下一阶段

> 当前状态：**等待你批准本计划**。批准后我从 P0 开始实施。
