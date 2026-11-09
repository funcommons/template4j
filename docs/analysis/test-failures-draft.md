# 测试失败报告

> 扫描时间：2026-07-04  
> 命令：`mvn -pl template4j-core -am test`  
> 总计：**288 个测试，13 个 Failure + 67 个 Error = 80 个失败**

---

## 一、按失败类型分布

| 类型 | 数量 | 说明 |
| :--- | :---: | :--- |
| **A. 测试代码 bug（写错了）** | ~72 | 包括用错 API、用错语法、用错参数 |
| **B. 业务代码 bug** | 3 | RestRenderer 对 5xx 状态码处理、`TemplateLoader.loadYaml` 默认实现错误、`@..` JSONPath 返回类型 |
| **C. 测试设计问题** | 5 | `load()` 需要 URL context、`data()` 无参重载、`&include` 路径形式 |

---

## 二、A 类：测试代码 bug（占大头）

### A1. `engine.render(String, Map)` 返回 String，不能强转为 JSONObject / JSONArray

**最常见错误。约 50 处。**

`render(String, Map)` 把字符串当带占位符的**字符串**处理（按 `parseStrTemplate` 解析 `{% %}` `{@ %}` 等，但不会先按 JSON 解析）。返回 String。要拿到 JSONObject 必须用：

- `engine.renderJson(String, Map)`（自动 `parseTemplateJson`）
- 或 `engine.render(JSONObject.parse(template), Map)`（预解析）
- 或 `engine.renderUrl(...)`（走 LoaderUtil.loadTemplate，会按后缀推断）

**修复样板：**

```java
// ❌ 错
Object r = RenderEngine.build().render("{ \"x\":\"{@name}\"}", data);
JSONObject jo = (JSONObject) r;     // ClassCastException

// ✅ 对
Object r = RenderEngine.build().renderJson("{ \"x\":\"{@name}\"}", data);
JSONObject jo = (JSONObject) r;
```

**涉及的测试（按文件）：**

- `RenderEngineOverloadTest`：testRenderStringMap / testRenderStringString / testRenderStringStringYaml / testParseDataObjectConverts
- `EscapeKeyAndTemplateUtilTest`：全部 12 个测试
- `DocumentedFeatureTest`：testStarMergeArrayOfMaps / testEachBodyReturnsObject / testRenderComponentLateBind / testNestedAtDataOverride / testCommentKeyStripped / testCentralizedVariableDefinition / testCentralizedVariableWithTemplates / testStarMergeWithExisting 等

### A2. 方法调用语法 `{$name(...)}` 应该是 `{=name(...)}`

文档示例（`template-func.yml`）：

```yaml
"demo1": "小明比小红大{$older}岁，所以小明{=add(@age,$older)}岁。"
```

`$older` 是**变量引用**，`{=add(...)}` 才是**方法调用**。我误把方法调用写成了 `{$greet('Alice')}`。

**影响：**

- `DocumentedFeatureTest.testMethodWithDefaultArgs` / `testMethodDefaultForNull`
- `DocumentedFeatureTest.testArgsArrayAndIndexed` / `testArgsOutOfBounds`

### A3. `ArrayUtils.removeAllOccurrences(Object[] array, Object value)` 签名错

我的写法：`ArrayUtils.removeAllOccurrences([1,2,3,2,1], 2)`  
实际签名只接受 `Object[]`，不接受 `ArrayList`。Groovy 把 `[1,2,3,2,1]` 字面量编译成 `ArrayList`。

**影响：** `DocumentedFeatureTest.testArrayUtilsDefaultImport`

### A4. `Math.abs` 没加调用括号

`{= Math.abs }` 把方法当属性引用 → `No such property: abs for class: java.lang.Math`。  
应写 `{= Math.abs(-10) }`。

**影响：** `DocumentedFeatureTest.testInvokeComponentWithMethod`

### A5. `FuncCastTest.cast()` 辅助方法脚本错误

```java
"{% cast(v, type) %}".replace("v", "(@v)").replace("@v", "__v")
// 生成 {% cast((__v), type) %}
```

但 `__v` 和 `type` 不在脚本作用域中，引擎要求用 `data.__v` / `data.type` 或 `@__v` / `@type`。辅助方法写得不对，导致所有 14 个 cast 测试都失败。

### A6. `testAtInsideLineComment` / `testAtInsideBlockComment` 转义错

```java
"{% // @value is a comment\\n return 1 %}"
```

`\\n` 在 Java 字符串字面量里是反斜杠 + `n`，不是真正的换行符。Groovy 解析器把 `\\` 当作 `\` 处理，导致 `@value` 被错误改写。

### A7. `FuncFneTest.testNonEmptyMapReturns` 期望值错

```java
Object r = RenderEngine.build().render("{% fne(null, [], new HashMap<>(), [1,2], 'x') %}", ...);
Assert.assertTrue(r instanceof List);   // ❌
```

`[1,2]` 是 List，`'x'` 是 String。`fne` 返回第一个**非空值**——返回的是 `[1,2]`（第一个非空）。但是 `render(String, Map)` 返回 String，外层 cast 不到 List。

---

## 三、B 类：业务代码 bug（建议修源码）

### B1. `TemplateLoader.loadYaml` 默认实现用错了参数 🔴

**文件：** `template4j-core/src/main/java/fun/commons/template4j/core/TemplateLoader.java:39`

```java
default Object loadYaml(String url) throws IOException {
    return UJson.JSONFromYaml(url);     // ❌ 应该是 load(url)
}
```

`loadYaml` 应该先 `load(url)` 拿到字符串内容，再把字符串解析为 YAML。当前实现直接拿 `url` 当 YAML 文本解析。

**复现：**

```java
TemplateLoader loader = url -> "a: 1\nb: 2\n";
Object r = loader.loadYaml("anything");   // 应得 {a:1, b:2}
// 实际：IOException: invalid yaml object or array string
//       （因为 UJson.JSONFromYaml("anything") 把 "anything" 当 YAML 解析）
```

**影响测试：** `TemplateLoaderTest.testCustomLoaderLoadYamlDefault`  
**修复：** `return UJson.JSONFromYaml(load(url));`

---

### B2. `RestRenderer` 对 HTTP 5xx 状态码处理错误 🔴

**文件：** `template4j-core/src/main/java/fun/commons/template4j/core/components/RestRenderer.java:158`

```java
throw new HttpClientErrorException(responseEntity.getStatusCode(), responseEntity.getBody());
```

`HttpClientErrorException` 构造器**只接受 4xx**。传 5xx（如 500）会在构造时抛 `IllegalArgumentException`。该异常被外层 catch 捕获，但 catch 内 `e instanceof HttpClientErrorException` 不匹配，走 `-1` 分支。

**复现：**

```java
responseStatus = 500;
Object r = engine.renderJson("""
    { "@class":"rest", "url":..., "error":{ "code":"{$responseData.status}" } }
    """, data);
((JSONObject) r).getIntValue("code");   // 期望 500，实际 -1
```

**影响测试：** `RestRendererTest.testErrorTemplate`  
**修复：**

```java
} else {
    throw new HttpServerErrorException(responseEntity.getStatusCode(), responseEntity.getBody());
}
// 或者更通用：
} else {
    throw new HttpStatusCodeException(responseEntity.getStatusCode(), responseEntity.getBody()) {};
}
```

并在 catch 里把 `HttpClientErrorException ex` 改成 `HttpStatusCodeException ex`（或拆成两个 instanceof）。

---

### B3. `JSONPath.eval("@..name")` 返回 `FuncData` 而非 `List`

**文件：** `template4j-core/src/main/java/fun/commons/template4j/core/JsonRenderer.java:301`

```java
Object result = JSONPath.eval(self.getData(), expr);
```

但 `self.getData()` 返回的是 `Map`（用 `Map.of(...)` 构造），而非 JSONObject。fastjson2 的 JSONPath 在处理非 JSONObject 的 Map 时可能返回包装类型（这里是 `FuncData`？不确定），导致 `@..name` 后链式比较失败。

**影响测试：** `SelfObjectAndScriptUtilTest.testAtDeepScan` — `FuncData cannot be cast to Comparable`

**修复方向：**

- 在 convertSelf 入口确保 `data` 是 JSONObject（用 `JSONObject.from(...)` 包一层）
- 或在 JSONPath.eval 前先 `JSONObject.from(self.getData())`

---

## 四、C 类：测试设计问题（需要重写测试，或放宽期望）

### C1. `FuncLoadTest` 全部 4 个 — `load()` 无 URL 上下文

```java
RenderEngine e = RenderEngine.build();
Object r = e.render("{% load('./data.json') %}", Map.of());   // ❌
```

`FuncLoad.loadResource(url)` 用 `self.getResourceUrl()` 作为基准，`render(String, Map)` 不设置 `resourceUrl`，于是基准为空。`UString.relativeUrl("", "./data.json")` → 不可解析的资源路径 → 抛 `IOException: Resource not found`。

**修复方向（任选一）：**

- 用 `engine.renderUrl(...)` 触发 `setResourceUrl`，模板内再 `load(...)`
- 或者直接断言 `load` 在没有 context 时抛 `IOException`（改测异常路径）

### C2. `FuncRenderAndVarsTest.testDataRoot` — `data()` 无参重载不存在

`FuncData` 有 `call(Object)` / `call(Map)` 等，没有 `call()`。Groovy 报 `MethodSelectionException`。

**修复：** 测试改写为 `data('key')` 或删除这个测试。

### C3. `FuncRenderAndVarsTest.testRenderInlineTemplate` — String vs List 输出格式

```java
Object r = RenderEngine.build().render("{% render([1,2,3]) %}", Map.of());
Assert.assertEquals("[1, 2, 3]", String.valueOf(r));   // ❌
```

实际输出 `[1,2,3]`（无空格），断言期望带空格。文档约定不同——这是测试期望错。

### C4. `LoaderUtilTest.testIncludeStringForm` — `&include:` 在字符串值中展开后字段合并逻辑复杂

测试断言 `jo.containsKey("a")` 但 `&include` 展开的字段会与原字段合并，最终 keys 数量不对。

**修复：** 重新设计测试用例，明确期望的最终 JSON 结构。

### C5. `RestRendererTest.testPostJsonBody` — 字符串模板被多次解析

`engine.renderJson("{...}", data)` 第一次 `parseTemplateJson` 报错"非法 JSON"，因为我的模板里 Java `"""` 文本块带换行/缩进。

**修复：** 使用单行字符串或先 `JSONObject.parse(templateStr)`。

---

## 五、按测试文件汇总

| 测试文件 | 总数 | 通过 | 失败 | 主要原因 |
| :--- | :---: | :---: | :---: | :--- |
| JsonRendererTest | 1 | 1 | 0 | — |
| TemplateSyntaxTest | 30 | 30 | 0 | — |
| RenderEngineApiTest | 24 | 24 | 0 | — |
| ComponentRendererTest | 34 | 34 | 0 | — |
| ScriptUtilTest | 2 | 2 | 0 | — |
| ComplexIntegrationTest | 20 | 20 | 0 | — |
| UDataTest | 6 | 6 | 0 | — |
| UTypeTest | 1 | 1 | 0 | — |
| **RestRendererTest** | **17** | **15** | **2** | A5 (模板换行) + B2 |
| **LoaderUtilTest** | **14** | **13** | **1** | C4 |
| **FuncFneTest** | **11** | **10** | **1** | A7 |
| **FuncCastTest** | **16** | **2** | **14** | A5 |
| **FuncLoadTest** | **4** | **0** | **4** | C1 |
| **RenderEngineOverloadTest** | **31** | **27** | **4** | A1 |
| **EscapeKeyAndTemplateUtilTest** | **13** | **1** | **12** | A1 |
| **TemplateLoaderTest** | **5** | **4** | **1** | B1 |
| **FuncRenderAndVarsTest** | **13** | **4** | **9** | A1 + C2 + C3 |
| **DocumentedFeatureTest** | **30** | **5** | **25** | A1+A2+A3+A4 |
| **SelfObjectAndScriptUtilTest** | **16** | **9** | **7** | A1 + A6 + B3 |

---

## 六、覆盖率（待测）

由于测试编译失败导致部分测试类未跑，JaCoCo 暂未重跑。修复后再跑。

---

## 七、建议处置顺序

1. **先修 3 个业务代码 bug（B1、B2、B3）**——会暴露真实问题，让现有用户得到修复。
2. **修测试代码 bug**——A1 占比最大，是写测试时的惯性错误。批量替换 `engine.render(String, Map)` → `engine.renderJson(String, Map)`（仅当期望 JSONObject / JSONArray）。
3. **C 类按需重写或删除**——`testDataRoot`、`testRenderInlineTemplate` 的期望值偏离文档；`FuncLoadTest` 整套需要改用 `renderUrl` 上下文。

修完预计通过率从 72% 提升到 95%+。