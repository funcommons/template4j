# 04 · 学习路径

> 面向不同基础的开发者，提供 4 条递进式学习路径。每条路径含：动机、推荐顺序、练习题、验收标准。

## 1. 路径总览

```
路径 A（前端/全栈入门）→ 路径 B（后端工程师）→ 路径 C（平台/工具开发）→ 路径 D（贡献者）
      ↑                          ↑                          ↑                          ↑
   用模板做配置               写自定义组件                集成 template4j              扩展引擎
```

| 路径 | 适合人群 | 目标 | 推荐时长 |
|------|---------|------|---------|
| A | 第一次接触模板引擎 | 看完 8 个示例，能改模板 | 30 min |
| B | 后端工程师 | 能写 `@class:myComp` 自定义组件 | 60 min |
| C | 平台/架构师 | 把 template4j 集成进自己的应用 | 90 min |
| D | 框架贡献者 | 能给 template4j-core 提 PR | 180 min |

## 2. 路径 A：模板入门

**动机**：理解"模板是 JSON"和"两种表达式（数据/变量）"两个核心概念。

### 步骤

1. **打开 demo**：访问 `http://localhost:8088/`，进入「学习」页面
2. **示例 1：数据路径** — 阅读说明 → 修改 data 中 `user.name` → 重新渲染
3. **示例 2：变量** — 区分 `$x` 与 `$$y` 的求值时机
4. **示例 4：if 组件** — 改 `is` 字段，观察输出
5. **示例 5：each 组件** — 加一个用户到 data.users，观察新行

### 练习题

- 用 `each` 渲染一个商品列表，每项含 `name` / `price` / `inStock`
- 用 `if` 实现「VIP 折扣」逻辑

### 验收

- 能解释 `{@user.name}` 与 `{$user.name}` 的区别
- 能独立写出一段模板：输入 `data.list=[1,2,3]`，输出 `{"doubled":[2,4,6]}`

## 3. 路径 B：自定义组件

**动机**：当 if/each/rest 不够用，自己写组件。

### 步骤

1. **阅读示例 3,4,5**：理解 if/each 如何用 `RenderContext` 渲染子树
2. **学习 RenderComponent 接口**：

   ```java
   public interface RenderComponent {
       String name();
       Object render(RenderContext context) throws Exception;
   }
   ```

3. **写第一个组件：`upper`** — 把 select 字符串转大写：

   ```java
   public class UpperRenderer implements RenderComponent {
       @Override public String name() { return "upper"; }
       @Override public Object render(RenderContext ctx) throws Exception {
           ctx.assertField("select");
           Object sel = ctx.renderField("select");
           return String.valueOf(sel).toUpperCase();
       }
   }
   ```

4. **注册**：`engine.withComponent(new UpperRenderer())`
5. **测试**：写一个模板 `{"@class":"upper","select":"hello"}` 期望 `HELLO`

### 练习题

- 实现 `@class:default` — select 为 null/blank 时返回默认值
- 实现 `@class:concat` — 把 args 数组拼接成字符串

### 验收

- 能解释 `context.renderField()` 与 `context.getValue()` 的区别（前者会触发渲染，后者直接拿值）
- 能用自定义组件解决一个真实问题

## 4. 路径 C：集成到自己的应用

**动机**：把 template4j 嵌入业务系统做配置驱动渲染。

### 步骤

1. **依赖**：

   ```xml
   <dependency>
       <groupId>fun.commons</groupId>
       <artifactId>template4j-core</artifactId>
       <version>0.4.0</version>
   </dependency>
   ```

2. **构造引擎**（参考 demo 的 `TemplateEngineConfig`）：

   ```java
   RenderEngine engine = RenderEngine.build()
       .withBean("myService", myService)
       .withImport("com.fasterxml.jackson.databind.ObjectMapper")
       .withLoader(new MyDbTemplateLoader());  // 自定义加载器（DB / Redis / OSS）
   ```

3. **API 入口**：

   | 需求 | API |
   |------|-----|
   | 字符串模板 + Map data | `engine.renderJson(tpl, data)` |
   | YAML 模板 | `engine.renderYaml(tpl, data)` |
   | 从 classpath 加载 | `engine.renderJsonUrl("/path.tpl", data)` |
   | 注册可调用 bean | `engine.withBean("userSvc", userSvc)` → 模板内 `{= userSvc.findById(@id) }` |

4. **错误处理**：捕获 `RenderException`，getMessage 含完整路径（如 `template path:/out/% script error by ...`）

5. **生产注意事项**：
   - Groovy 脚本无沙箱，**严禁**让终端用户直接编辑模板（除非有 RBAC）
   - 引擎是线程安全的，可作为单例
   - 大模板考虑缓存解析结果（template4j-core 当前未提供缓存层）

### 练习题

- 把一个现有"用 if/else 拼字符串"的逻辑改写成 template4j 模板
- 用 `@class:rest` 重写一个简单 API 网关（聚合两个上游接口）

### 验收

- 能说出 template4j 在你项目中的 3 个落地场景
- 能讲清楚"为什么模板是 JSON 而不是文本"的好处

## 5. 路径 D：贡献者

**动机**：给 template4j-core 提 PR。

### 必读源码

| 文件 | 用途 |
|------|------|
| `RenderEngine.java` | 接口与所有 default 方法 |
| `JsonRenderer.java` | 主渲染流程 |
| `SelfObject.java` | 渲染上下文 + 变量作用域 |
| `TemplateUtil.java` | 字符串/键解析 |
| `ScriptUtil.java` | Groovy 脚本预处理（`@`→`data.`） |
| `RenderComponent.java` | 组件接口 + RenderContext |
| `components/IfRenderer.java` | 简单组件示例 |
| `components/EachRenderer.java` | 带子模板渲染示例 |
| `LoaderUtil.java` | 资源加载 + `&include` |

### 开发环境

```bash
git clone <repo>
cd template4j
mvn clean verify     # 必须通过 + 100% 覆盖率
```

### 贡献方向（按优先级）

1. **沙箱化 Groovy 脚本** — 当前 Groovy 能调用任意类（如 System.exit），加 SecureCustomizer 白名单
2. **解析结果缓存** — 同一字符串模板多次渲染时复用 AST
3. **更多组件** — `@class:try`（异常捕获）、`@class:retry`、`@class:batch`（并发聚合）
4. **调试工具** — 渲染过程可视化（trace 输出每步表达式求值结果）
5. **性能基准** — JMH 基准测试 + 与 jsonpath / velocity 对比

### 验收

- 在 fork 上提交 PR，跑通 CI（mvn verify + JaCoCo 100%）
- 至少 1 个集成测试覆盖新特性
- 更新 CLAUDE.md / 示例文档

## 6. 各路径推荐示例

| 路径 | 必读示例 | 选读示例 |
|------|---------|---------|
| A | 1, 2, 4, 5 | 3, 8 |
| B | 1, 2, 4, 5, 6 | 7 |
| C | 1, 2, 6, 7, 8 | 全部 |
| D | 全部 | — |

## 7. FAQ 速查

| 问题 | 答案 |
|------|------|
| 模板里能写注释吗？ | JSON5 支持 `// 行内` 和 `/* 块 */`；YAML 用 `#`；JSON 用 `#` 前缀的 key 作为注释 |
| 怎么调试 Groovy 脚本？ | 在脚本里 `println` 会输出到服务端日志 |
| 模板能递归调用吗？ | 不能直接递归；可用 `@class:invoke` 配合 `$name()` 变量函数 |
| 怎么共享一组 bean？ | `engine.withBeans(map)` 批量注册 |
| 多语言支持？ | 模板内用 `{= msg.get('key') }`，msg 是 Map bean |
