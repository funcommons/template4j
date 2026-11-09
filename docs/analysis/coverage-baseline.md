# 分析报告：core 代码 vs 文档 vs 测试

> 扫描时间：2026-07-04  
> 范围：`template4j-core/` 全量源码 + `template4j-web/src/main/resources/docs/documents/*.yml` 用户文档 + 全部测试类

---

## 1. 测试统计

| 测试类 | 测试方法数 | 通过 |
| :--- | :---: | :---: |
| `JsonRendererTest` | 1 | ✅ |
| `TemplateSyntaxTest` | 30 | ✅ |
| `RenderEngineApiTest` | 24 | ✅ |
| `ComponentRendererTest` | 34 | ✅ |
| `ScriptUtilTest` | 2 | ✅ |
| `ComplexIntegrationTest` | 20 | ✅ |
| `utils.UDataTest` | 6 | ✅ |
| `utils.UTypeTest` | 1 | ✅ |
| **合计** | **118** | **118 ✅ / 0 ❌** |

构建命令：

```bash
mvn -pl template4j-core -am test
```

---

## 2. JaCoCo 覆盖率（`mvn … org.jacoco:jacoco-maven-plugin:0.8.11:report`）

### 2.1 整体

| 维度 | 覆盖率 |
| :--- | :---: |
| INSTRUCTION | **59.2%** |
| BRANCH | 48.0% |
| LINE | 62.9% |
| METHOD | 45.0% |

### 2.2 分包

| 包 | 类数 | 行覆盖率 |
| :--- | :---: | :---: |
| `core` (主包) | 17 | 64% |
| `core.components` | 7 | 75% |
| `core.beans` | 6 | 35% |

### 2.3 逐类（按行覆盖率排序）

| 类 | 行覆盖 | 方法覆盖 | 备注 |
| :--- | :---: | :---: | :--- |
| `ConstRenderer` | 100% (4/4) | 100% (3/3) | 完全覆盖 |
| `RenderException` | 85% (11/13) | 80% (4/5) | — |
| `ScriptUtil` | 94% (30/32) | 88% (7/8) | — |
| `JsonRenderer` | 90% (139/155) | 91% (10/11) | 主渲染器，覆盖充足 |
| `RenderBindings` | 86% (19/22) | 100% (3/3) | — |
| `EachRenderer` | 93% (43/46) | 100% (4/4) | — |
| `RenderRenderer` | 92% (11/12) | 100% (3/3) | — |
| `FuncObject`（匿名块除外） | 86% (12/14) | 67% (2/3) | — |
| `SwitchRenderer` | 81% (17/21) | 100% (3/3) | — |
| `IfRenderer` | 79% (23/29) | 100% (3/3) | — |
| `RenderComponent` (含 `RenderContext`) | 72% (28/32) | 73% (11/15) | 4 个 `RenderContext` 内匿名 `HashMap` 块几乎 0%（构造器未走测试） |
| `SelfObject` | 76% (67/88) | 87% (13/15) | — |
| `RenderEngine` | 30% (12/40) | 36% (9/25) | **大量便捷重载未覆盖** |
| `FuncVars` | 67% (6/9) | 60% (3/5) | — |
| `FuncData` | 67% (4/6) | 50% (2/4) | — |
| `TemplateUtil` | 62% (74/119) | 71% (10/14) | — |
| `GroovyImports` | 68% (26/38) | 67% (4/6) | — |
| `DefaultTemplateLoader` | 47% (8/17) | 100% (3/3) | 异常分支未触发 |
| `FuncRender` | 75% (3/4) | 50% (1/2) | — |
| `FuncLoad` | 43% (3/7) | 33% (1/3) | — |
| `FuncCast` | 33% (1/3) | 33% (1/3) | 大量类型分支未走 |
| `TemplateLoader` | 0% (0/2) | 0% (0/2) | 仅接口，未直接测 |
| **`RestRenderer`** | **5% (3/56)** | **29% (2/7)** | **基本未测** |
| **`LoaderUtil`** | **3% (2/67)** | **20% (2/10)** | **基本未测** |
| **`FuncFne`** | **6% (1/18)** | **50% (1/2)** | **基本未测** |

> ⚠️ `RestRenderer` / `LoaderUtil` / `FuncFne` 是覆盖率洼地：它们处理"远端拉取"、"路径解析合并"、"第一个非空"等典型模板逻辑，但没有专门的单测覆盖。仅在 `ComplexIntegrationTest` 的个别用例间接触及。

---

## 3. 差异表（文档 vs 代码）

> 说明：✅ 一致；📝 仅文档；💻 仅代码；⚠️ 文档描述与代码行为不一致。

### 3.1 占位符 / 转义

| 语法 | 文档（`documents/*.yml`） | 代码（`TemplateUtil.parseStrTemplate`） | 测试 | 结论 |
| :--- | :--- | :--- | :--- | :--- |
| `{@ path}` | ✅ `expression-started.yml` | ✅ `JsonRenderer.expressionValue` | ✅ `TemplateSyntaxTest.testJsonPath*` | 一致 |
| `{$ name}` | ✅ `vars-started.yml` | ✅ | ✅ `testLocalVariable*` | 一致 |
| `{= expr}` | ✅ `expression-started.yml`（隐含） | ✅ | ✅ `testGroovyExpression*` | 一致 |
| `{% script %}` | ✅ `script-started.yml` | ✅ | ✅ `testGroovyScriptBlock` | 一致 |
| `<& … &>` | ✅ `template-escape.yml` | ✅（regex `<&([\s\S]*?)&>`） | ⚠️ 未单独测 | ✅ |
| `{# … #}` | ✅ `template-comment.yml` | 💻 **未实现**（源码注释 `//{#...#} 不渲染` 是误导，实际只匹配 `<&…&>`） | ❌ | ⚠️ **文档声称支持，实际不支持** |

### 3.2 键前缀 / 后缀

| 形态 | 文档 | 代码 | 测试 | 结论 |
| :--- | :--- | :--- | :--- | :--- |
| `#` 注释 | ✅ `template-comment.yml` | ✅ `convertKey` 首字符 `#` 跳过 | ✅ `testSpecialFieldComment` | ✅ |
| `xxx#` 值原样 | ✅ `template-comment.yml`（"abc#" 与 "$abc#"） | 💻 **未实现**（`convertKey` 只识别 `%` / `&` 后缀，不识别 `#`） | ❌ | ⚠️ **文档声称支持，实际不支持** |
| `$name` 变量 | ✅ `template-vars.yml` | ✅ `convertVars` | ✅ `testVariableDefinition` | ✅ |
| `$name()` 方法 | ✅ `template-func.yml` | ✅ | ✅ `testMethodDefinition*` | ✅ |
| `$$` 预渲染变量 | ✅ `vars-started.yml` | ✅ | ✅ `testPreRenderVariablesWithDoubleDollar` | ✅ |
| `$enter{}` | ✅ `template-enter.yml` | ✅ | ✅ `testSpecialFieldEnterScript` | ✅ |
| `$return{}` | ✅ `template-return.yml` | ✅ | ✅ `testSpecialFieldReturnScript` | ✅ |
| `@` 局部数据 | ✅ `template-data.yml` | ✅ `convertVars` 调 `setData` | ✅ `testDataOverrideWithAt` / `testSpecialFieldDataOverride` | ✅ |
| `@@` 字面局部数据 | ✅ `template-data.yml` | 💻 **未实现**（`convertVars` 只识别 `@` / `$` / `$$`） | ❌ | ⚠️ **文档声称支持，实际不支持** |
| `xxx%` 脚本后缀 | ✅ `template-script.yml` | ✅ `convertKey` | ✅ `testMixedSyntax` | ✅ |
| `xxx&` 不渲染后缀 | ✅ `template-const.yml` | ✅ | ✅ 间接（`ConstRenderer` 测试） | ✅ |
| `@class` 组件 | ✅ `template-class.yml` | ✅ `JsonRenderer` 调度 | ✅ 大量 | ✅ |
| `@class|method` 简写 | ✅ `template-class.yml` | ✅ `simplify` | ✅ `testRenderRendererStringTemplate` 等 | ✅ |
| `*` 合并 | ✅ `template-star.yml` | ✅ `mergeAllMap` | ✅ `testSpecialFieldMerge` / `testStarField*` | ✅ |
| `&include` | ✅ `template-include.yml` | ✅ `LoaderUtil.includeValue` | ⚠️ 未单测（仅 `testRenderUrlFromClasspath` 顺带） | ⚠️ **功能覆盖不足** |
| `<&…&>` 转义 key | ✅ `template-escape.yml` | ✅ `escapeKey` | ❌ | ⚠️ **缺单测** |

### 3.3 组件

| 组件 | 文档 | 代码 | 测试 | 结论 |
| :--- | :--- | :--- | :--- | :--- |
| `if` | ✅ `component-if.yml` | ✅ | ✅ `testIfRenderer*`（8） | ✅ |
| `each` | ✅ `component-each.yml` | ✅ | ✅ `testEachRenderer*`（8） | ✅ |
| `switch` | ✅ `component-switch.yml` | ✅ | ✅ `testSwitchRenderer*`（5） | ✅ |
| `invoke` | ✅ `component-invoke.yml` | ✅ | ✅ `testInvokeRenderer*`（3） | ⚠️ 简写 `@invoke:...` 路径缺测 |
| `render` | ✅ `component-render.yml` | ✅ | ✅ `testRenderRenderer*`（5） | ✅ |
| `const` | ✅ `component-const.yml` | ✅ | ✅ `testConstRenderer*`（3） | ✅ |
| `rest` | ✅ `component-rest.yml` | ✅ | ❌ | ⚠️ **完全未测**（覆盖率 5%） |

### 3.4 Groovy 脚本工具（Func*）

| 工具 | 文档 | 代码 | 测试 | 结论 |
| :--- | :--- | :--- | :--- | :--- |
| `data(path)` | ✅ `script-data.yml` | ✅ `FuncData` | ⚠️ 部分 | ✅ |
| `vars.x` / `vars.x=…` | ✅ `script-vars.yml` | ✅ `FuncVars` | ✅ | ✅ |
| `render(template)` | ✅ `script-render.yml` | ✅ `FuncRender` | ⚠️ 间接 | ✅ |
| `load(url)` | ✅ `script-data.yml` | ✅ `FuncLoad` | ❌（覆盖率 43%） | ⚠️ **缺单测** |
| `cast(v, type)` | ✅ `script-cast.yml` | ✅ `FuncCast` | ⚠️ 间接（覆盖率 33%） | ⚠️ **缺单测** |
| `fne(...)` | ✅ `script-fne.yml` | ✅ `FuncFne` | ❌（覆盖率 6%） | ⚠️ **基本未测** |
| `@` 自动改写 | ✅ `script-data.yml`（三种语法） | ✅ `ScriptUtil.codeReplaceAt` | ✅ `testGroovyExpression*` | ✅ |
| 默认 import | CLAUDE.md 提到 | ✅ `GroovyImports` | ⚠️ 间接 | ✅ |
| `withBean` / `withImport` | `script-vars.yml` | ✅ | ✅ `testRegisterCustomBean` / `testImport*` | ✅ |
| `withComponent` | CLAUDE.md | ✅ | ✅ `testRegisterCustomComponent` | ✅ |

---

## 4. 测试维度差距（应该测但没测的）

按重要性排序：

### 🔴 高优先级（功能可见、错误敏感）

| 缺失 | 风险 |
| :--- | :--- |
| **`RestRenderer` 没有任何单测**（5% 覆盖） | HTTP 出站是 SSRF / 网络错误的入口，没有异常用例覆盖 |
| **`LoaderUtil` 基本未测**（3%） | `&include`、URL 相对解析、循环保护都未直接验证 |
| **`FuncFne` 基本未测**（6%） | "第一个非空"的 List/Map/Collection 分支全是裸跑 |
| **`{# … #}` 转义** | 若有人按文档写 `{# … #}` 期望不渲染，实际会被当成普通 JSON 字符串 |
| **`@@` 局部数据** | 文档教用户用 `@@`，但运行时会静默落入默认分支当作普通 key |
| **`xxx#` 值原样后缀** | 同上，用户按文档期望 `demo1#` 不渲染，实际会去渲染 |

### 🟡 中优先级（功能已实现但测试薄）

| 缺失 | 风险 |
| :--- | :--- |
| `escapeKey`（`<&…&>` 包裹的 key） | 用户需要"用 `#` 当字面 key"的唯一方式未测 |
| `RenderEngine` 便捷重载（`renderJson*` / `renderYaml*` / `renderUrl*` 的 String / Object 重载） | 25% 覆盖率，错误信息格式化、`parseDataString` / `parseDataObject` 异常路径未走 |
| `FuncLoad` / `FuncCast` 全分支 | 类型转换 / URL 加载错误处理覆盖不足 |
| `$enter{}` / `$return{}` 与 `@` / `@@` / `$enter{}` 之间的交互顺序 | 文档强调"只支持定义在 @ 前的变量"，但未单测 |
| `ScriptUtil.encodeSubString` 边界 | 已测，但嵌套注释 + 多行字符串组合未覆盖 |

### 🟢 低优先级（属于抛光）

| 缺失 |
| :--- |
| `withLoader` 自定义加载器（`TemplateLoader` 接口） |
| `TemplateLoader.loadJson` / `loadYaml` 默认实现 |
| `RenderException` 各 path / code 字段 |
| `mergeAllMap` 多层 List 嵌套合并 |

---

## 5. 建议补测清单（按修复顺序）

```text
1. 修复 3 个文档/代码不一致（任选其一）：
   - 选项 A：实现 {@@, "xxx#", "{#…#}"} 与文档对齐
   - 选项 B：从文档删除这三种写法，并在 CLAUDE.md / manual/ 中说明不支持

2. 给 RestRenderer / LoaderUtil / FuncFne 各补 5-8 个单测：
   - RestRenderer：success / error 分支、GET 拼 query、POST 发送 JSON、dataType 转换、HTTP 错误
   - LoaderUtil：&include 嵌套、相对 URL、循环保护、JSON/YAML/纯文本三种回退
   - FuncFne：String 空 / Collection 空 / Map 空 / 全 null / 混合类型

3. 给 RenderEngine 便捷重载补覆盖率：
   - renderJson(Object data) / renderYaml(Object data) / renderJsonUrl(...) / renderYamlUrl(...)
   - parseDataString 异常：data 不是 JSON 也不是 YAML

4. 给 escapeKey 补测试：
   - "<&#demo&>" → 字面 key "#demo"
   - "<&<&demo&>&>" → 嵌套转义

5. FuncCast 全类型分支：
   - int/long/double/float/boolean/String/Date/BigInteger/BigDecimal/JSONObject/JSONArray
   - null → null

6. RenderContext 的 4 个匿名 HashMap{} 块：
   - 触发条件是 render(template, varName, varObject) 这类带 var 的重载，
     目前测试都走 render(template) 或 render(template, map)，未触达
```

---

## 6. 覆盖率门槛建议

| 指标 | 当前 | 推荐门槛 | 差距 |
| :--- | :---: | :---: | :--- |
| 行覆盖率 | 62.9% | 80% | -17 |
| 分支覆盖率 | 48.0% | 70% | -22 |
| 方法覆盖率 | 45.0% | 80% | -35 |

集中补 §5 列表后估算可达：行 78%、分支 65%、方法 70%。剩余缺口主要在 `RestRenderer` 远端分支与异常处理。

---

## 7. 关键文件清单（核对用）

| 用途 | 路径 |
| :--- | :--- |
| 渲染主类 | `template4j-core/src/main/java/fun/commons/template4j/core/JsonRenderer.java` |
| 键 / 字符串转换 | `template4j-core/src/main/java/fun/commons/template4j/core/TemplateUtil.java` |
| 脚本预处理 | `template4j-core/src/main/java/fun/commons/template4j/core/ScriptUtil.java` |
| 组件契约 | `template4j-core/src/main/java/fun/commons/template4j/core/RenderComponent.java` |
| 组件实现 | `template4j-core/src/main/java/fun/commons/template4j/core/components/*.java` |
| Func\* | `template4j-core/src/main/java/fun/commons/template4j/core/beans/*.java` |
| 公共 API | `template4j-core/src/main/java/fun/commons/template4j/core/RenderEngine.java` |
| 加载 | `template4j-core/src/main/java/fun/commons/template4j/core/{LoaderUtil,DefaultTemplateLoader,TemplateLoader}.java` |
| 文档（用户） | `template4j-web/src/main/resources/docs/documents/*.yml` |
| 测试 | `template4j-core/src/test/java/fun/commons/template4j/**` |
| JaCoCo 报告 | `template4j-core/target/site/jacoco/index.html` |
| JaCoCo CSV | `template4j-core/target/site/jacoco/jacoco.csv` |