# 100% 覆盖率路径 + 测试失败确认

> 编写日期：2026-07-04  
> 测试基线：288 个测试，208 通过，80 失败（13 Failure + 67 Error）

---

## 一、每个失败的最终分类（基于源码阅读 + 实测 probe）

| 失败 | 我原分类 | 最终结论 | 证据 |
| :--- | :--- | :--- | :--- |
| `engine.render(String, Map)` cast 到 JSONObject（A1） | 测试 bug | **测试 bug（确认）** | probe 实测返回 `String`，value=`{ "x":"a"}`；`renderJson` 才是 JSONObject |
| `{$greet('Alice')}` 写法（A2） | 测试 bug | **测试 bug（确认）** | `template-func.yml` 文档示例 `{=add(18,2)}`；Groovy 中 `$` 是变量引用前缀，方法调用是 `{=...}` |
| `ArrayUtils.removeAllOccurrences([...], 2)`（A3） | 测试 bug | **测试 bug（确认）** | commons-lang3 签名 `removeAllOccurrences(Object[] array, Object value)`，不接受 List |
| `{= Math.abs }`（A4） | 测试 bug | **测试 bug（确认）** | 没有括号的属性引用，Groovy 抛 `MissingPropertyException` |
| FuncCastTest helper（A5） | 测试 bug | **测试 bug（确认）** | `__v` 不是 Groovy 内置标识符，引擎要求 `@__v` 走 data 路径 |
| `\\n` 在 Groovy 注释中（A6） | 测试 bug | **测试 bug（确认）** | Java 字面量 `\\n` 是 `\n` 两个字符，Groovy 行注释到 `\n` 字面位置不会终止 |
| FuncFneTest.testNonEmptyMapReturns（A7） | 测试 bug | **测试 bug（确认）** | `render(String, Map)` 返回 String，外层 cast 不到 List |
| FuncLoadTest 全部（C1） | 测试设计 | **测试设计** | `load()` 需 `self.getResourceUrl()` 上下文，`render(String, Map)` 不设置 |
| FuncRenderAndVarsTest.testDataRoot（C2） | 测试设计 | **测试设计** | `FuncData` 没有 `call()` 无参重载 |
| testRenderInlineTemplate 字符串格式（C3） | 测试设计 | **测试 bug** | 期望 `"[1, 2, 3]"` 实测 `"[1,2,3]"`，纯空白差 |
| LoaderUtilTest.testIncludeStringForm（C4） | 测试设计 | **测试设计** | `&include:` 在字符串中展开逻辑复杂，断言设计错 |
| RestRendererTest.testPostJsonBody（C5） | 测试设计 | **测试 bug** | 模板中 `"n":{@n}` 无引号包裹，`JSON.parse` 不接受 |
| `TemplateLoader.loadYaml` 默认实现（B1） | 业务 bug | **业务 bug（确认）** | `TemplateLoader.java:39` 直接传 `url` 给 YAML 解析器，应为 `load(url)` |
| `RestRenderer` 5xx 状态码（B2） | 业务 bug | **业务 bug（确认）** | `RestRenderer.java:158` 用 `HttpClientErrorException` 构造 500 抛 IAE，catch 分支不识别 |
| `@..x` deepScan 不支持（B3） | 业务 bug | **设计缺陷 + 测试 bug** | probe 实测：必须 `@'..x'`（带引号）才能跑出 `["x"]`；`@..x` 被 `codeReplaceAt` 改写成 `data..x` Groovy 崩 |

**结论**：80 个失败中，**测试 bug ≈ 72，业务 bug = 2（B1、B2），设计缺陷 = 1（B3）**。

---

## 二、对用户质疑的回应

> "是否确定 测试代码没有问题"

**确认**：测试代码（指**原有 118 个**）全部通过，**没有问题**。

我新写的 **170 个测试中**，绝大多数失败的根因是**新测试代码自身 bug**——特别是 A1（误用 `render(String, Map)` 当成 JSON 渲染）。**未动既有测试一行**。

业务代码里有 2 个真 bug（B1、B2）和 1 个设计缺陷（B3），不是测试的问题。

---

## 三、100% 覆盖率路径

### 3.1 当前覆盖率基线（既有测试 + 我新写的失败测试）

| 维度 | 数值 | 注 |
| :--- | :---: | :--- |
| INSTRUCTION | 59.2% | （修复测试 bug 后预计 65-70%） |
| BRANCH | 48.0% | |
| LINE | 62.9% | |
| METHOD | 45.0% | |

### 3.2 仍未覆盖的区域（按行覆盖率升序）

| 类 | 当前 | 100% 所需 |
| :--- | :---: | :--- |
| `TemplateLoader` | 0% | 仅接口默认方法，直接测即可 |
| `RestRenderer` | 5% | 补 7 类：GET/POST/PUT/DELETE × success/error × JSON/form/raw body × 4 种 dataType |
| `LoaderUtil` | 3% | 补 8 类：3 种格式回退、&include 三种形式、嵌套、循环保护、错误传播 |
| `FuncFne` | 6% | 补 6 类：String/Collection/Map/Number 各类型 + null 行为 |
| `FuncCast` | 33% | 11 种类型 + null + 异常输入 |
| `FuncLoad` | 43% | URL 解析相对/绝对 + 嵌套 include + 异常 |
| `RenderEngine` | 30% | 12 个便捷重载各 1 个用例 + parseDataString 异常 + withBean 重名 |
| `FuncRender` | 50% | 复杂模板、嵌套渲染、变量传递 |
| `DefaultTemplateLoader` | 47% | classpath 不存在 / IO 异常 |
| `FuncVars` | 62% | putAt、call、property 访问全分支 |
| `FuncData` | 67% | 嵌套 data() 调用、property 访问 |
| `TemplateUtil` | 62% | simplify 全简写分支、convertVars 全特殊键 |
| `SelfObject` | 76% | path() / parent 递归 / vars 跨作用域 / locks |
| `RenderBindings` | 86% | containsKey 边界 |
| `JsonRenderer` | 90% | 错误路径、各分支 |

### 3.3 100% 覆盖的具体测试清单

> 估算：新增 ~80-100 个测试，覆盖以下清单，可达 ≥95% 行覆盖 / ≥90% 分支覆盖。
> 真正的 100% 需要覆盖每一行（包括日志、私有工具方法、防御性 null 检查），约 120 个新测试 + 谨慎设计的 mock。

#### A. RestRenderer（已有部分，待补）

```java
// 现有 17 个测试中大部分失败。修测试 bug 后估算覆盖率 80%。
// 还需要：
@Test public void testGetWithCustomHeaders()
@Test public void testPostFormUrlencodedViaContentType()        // 已有
@Test public void testPostJsonContentTypeExplicit()
@Test public void testSuccessWithResponseDataRendering()
@Test public void testErrorWithNetworkException()
@Test public void testErrorWithHttpStatus()
@Test public void testAllDataTypeBranches()                       // 已有但需修
@Test public void testUrlEncoding()
@Test public void testInvalidUrlThrows()
@Test public void testNullResponseBody()
```

#### B. LoaderUtil（基本未测）

```java
@Test public void testIncludeObjectWithListValue()
@Test public void testIncludeObjectWithStringValue()
@Test public void testIncludeStringForm()                         // 已有，需修
@Test public void testIncludeNestedIncludes()
@Test public void testIncludeCircularReferenceProtection()        // 重要
@Test public void testFormatInferenceYamlFallback()
@Test public void testFormatInferenceTextFallback()
@Test public void testFormatInferenceJsonFallback()
@Test public void testRelativeUrlResolution()
@Test public void testLoadResource404()
```

#### C. FuncFne（基本未测）

```java
@Test public void testFneStringNonBlank()
@Test public void testFneCollectionEmpty()
@Test public void testFneMapEmpty()
@Test public void testFneNumber()                                 // 数字不视为空
@Test public void testFneAllEmpty()
@Test public void testFneNullArgs()
@Test public void testFneEmptyArgs()
@Test public void testFneMixedTypes()                             // 已有
```

#### D. FuncCast（部分覆盖）

```java
@Test public void testCastAllPrimitiveTypes()                    // 8 种基本类型
@Test public void testCastStringToAllTypes()
@Test public void testCastNullReturnsNull()                       // 已有，需修
@Test public void testCastInvalidStringThrows()
@Test public void testCastJsonObjectFromString()
@Test public void testCastJsonArrayFromString()
@Test public void testCastDateFromString()
@Test public void testCastBigDecimal()
@Test public void testCastBigInteger()
```

#### E. RenderEngine 便捷重载

```java
@Test public void testRenderObjectMap()                          // 已有
@Test public void testRenderObjectString()
@Test public void testRenderObjectObject()
@Test public void testRenderStringMap()                          // 已有，需修
@Test public void testRenderStringString()
@Test public void testRenderStringStringYaml()                   // 已有，需修
@Test public void testRenderJsonString()
@Test public void testRenderJsonObject()
@Test public void testRenderJsonMap()                            // 已有
@Test public void testRenderYamlMap()
@Test public void testRenderYamlString()
@Test public void testRenderYamlObject()
@Test public void testRenderUrlWithType()
@Test public void testRenderUrlWithoutType()
@Test public void testRenderJsonUrlOverloads()
@Test public void testRenderYamlUrlOverloads()
@Test public void testWithBeansBulk()
@Test public void testWithBeanDuplicateThrows()                   // 已有，但需检查
@Test public void testParseTemplateJsonInvalidThrows()
@Test public void testParseTemplateYamlInvalidThrows()
@Test public void testParseDataStringInvalidThrows()
@Test public void testParseDataObjectInvalidThrows()
```

#### F. FuncLoad / FuncRender / FuncVars

```java
@Test public void testLoadAbsoluteUrl()                          // 已有，需修
@Test public void testLoadRelativeUrl()                          // 已有，需修
@Test public void testLoadJsonObject()
@Test public void testLoadJsonArray()
@Test public void testLoadYaml()
@Test public void testLoadNestedInclude()
@Test public void testLoadCircularProtection()

@Test public void testRenderInline()
@Test public void testRenderJsonObject()
@Test public void testRenderWithVar()
@Test public void testRenderNested()
@Test public void testRenderNullTemplate()

@Test public void testVarsPropertyAccess()
@Test public void testVarsGetMissingReturnsNull()
@Test public void testVarsPutAt()
@Test public void testVarsRemove()
@Test public void testVarsChainCall()
```

#### G. SelfObject / TemplateUtil 边界

```java
@Test public void testPathNestedChain()
@Test public void testGetVarFunctionKeyWrapping()
@Test public void testRemoveVarNormalizesKeys()
@Test public void testPutVarsNull()
@Test public void testLockUnlock()
@Test public void testConvertVarsAllSpecialKeys()
@Test public void testSimplifyAllShorthands()
@Test public void testEscapeKeyNested()
@Test public void testConvertKeyCommentHash()                     // 已有
@Test public void testConvertKeyPercentSuffix()
@Test public void testConvertKeyAmpSuffix()
@Test public void testMergeAllMapDeepNested()
```

#### H. DefaultTemplateLoader / TemplateLoader

```java
@Test public void testDefaultLoadJson()
@Test public void testDefaultLoadYaml()
@Test public void testDefaultLoadMissing()                        // 已有
@Test public void testDefaultLoadWithClasspathPrefix()
@Test public void testDefaultLoadWithoutLeadingSlash()
@Test public void testCustomLoaderLoadJson()                      // 已有
@Test public void testCustomLoaderLoadYamlDefault()              // 已有，需修（暴露 B1）
@Test public void testCustomLoaderChainedLookup()
```

#### I. RenderBindings

```java
@Test public void testContainsKeyForVar()
@Test public void testGetInternalGroovyKey()
@Test public void testGetDollarPrefixedVar()
```

---

## 四、达到 100% 的关键路径

```
修复 3 个业务 bug (B1, B2, B3)
        ↓
修复 72 个测试 bug（批量 A1 替换 + A2-A7 局部修）
        ↓
新增 §3.3 列表中的 ~80 个测试（覆盖未走分支）
        ↓
覆盖率：LINE 95%+  BRANCH 90%+  METHOD 90%+
        ↓
再补 ~40 个边界 / 异常测试
        ↓
LINE 99%+ BRANCH 95%+ METHOD 95%+
```

剩余 ~1-3% 通常是：
- 私有工具方法的容错分支（如 `StringUtils` null 返回）
- 日志 / 监控调用（mock 不易触达）
- 极端边界（如 `Map.of()` 抛 OOM）

---

## 五、不建议追逐 100% 的理由

| 原因 | 说明 |
| :--- | :--- |
| 边际成本陡增 | 从 95% → 100% 的代码大多是日志和防御性代码，价值低 |
| 测试稳定性 | 覆盖所有异常路径需要 mock，mock 越多维护成本越高 |
| 改一行崩一片 | 高覆盖测试对重构不友好 |
| 行业实践 | 大多数项目（Spring、Hibernate 等）覆盖率门槛在 70-85% |

**推荐门槛：**

| 维度 | 推荐 |
| :--- | :---: |
| LINE | 80% |
| BRANCH | 70% |
| METHOD | 75% |

达成这个门槛需要：① 修业务 bug B1/B2/B3 + ② 修测试 bug A1-A7 + ③ 补 §3.3 中 ~50 个核心测试。

---

## 六、推荐执行顺序

```text
第一步  修 B1（TemplateLoader.loadYaml）           — 5 分钟，1 行改动
第二步  修 B2（RestRenderer 5xx 处理）            — 10 分钟，5 行改动
第三步  修 B3（codeReplaceAt 支持 @..x）          — 30 分钟，需要测试 deepScan 改写规则
第四步  修测试 A1（render → renderJson 批量）     — 30 分钟，sed 替换
第五步  修测试 A2-A7                            — 20 分钟
第六步  新增 §3.3 中关键测试                      — 2-3 小时
第七步  跑 JaCoCo 出新覆盖率                      — 5 分钟
第八步  按差距补 §3.3 中剩余测试                  — 1-2 小时
```

要不要我从第一步开始执行？或者你想要先看 B1/B2 的修复 diff？