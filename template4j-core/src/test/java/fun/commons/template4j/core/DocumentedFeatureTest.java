package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * 覆盖 documents/*.yml 文档中提到但其他测试未直接验证的特性。
 *
 * 所有模板都用 renderJson 以获得 JSONObject 结果。
 * 方法调用语法：{=name(...)} 而非 {$name(...)}（文档模板-func.yml）。
 */
public class DocumentedFeatureTest {

    // ========== $enter{} ==========

    @Test
    public void testEnterModifiesTemplate() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "$enter{}": "{% _template.put('greeting', 'Hi ' + @name) %}",
                    "greeting": "default"
                }
                """, Map.of("name", "alice"));
        JSONObject jo = (JSONObject) r;
        Assertions.assertEquals(jo.getString("greeting"), "Hi alice");
    }

    @Test
    public void testEnterCanReadVars() throws Exception {
        // $enter{} 脚本可以通过 @ref 引用 data，也可以通过裸标识符引用 $vars
        Object r = RenderEngine.build().renderJson("""
                {
                    "$ref": "x",
                    "$enter{}": "{% _template.put('out', $ref) %}",
                    "out": "default"
                }
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "x");
    }

    // ========== $return{} ==========

    @Test
    public void testReturnModifiesResult() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "items": [1, null, 2, "", 3],
                    "$return{}": "{% _result.items.removeIf(v -> v == null || String.valueOf(v).isEmpty()) %}"
                }
                """, Map.of());
        JSONObject jo = (JSONObject) r;
        JSONArray arr = jo.getJSONArray("items");
        Assertions.assertEquals(3, arr.size());
        Assertions.assertEquals(1, arr.get(0));
        Assertions.assertEquals(2, arr.get(1));
        Assertions.assertEquals(3, arr.get(2));
    }

    // ========== 集中式变量定义 ==========

    @Test
    public void testCentralizedVariableDefinition() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "$": {
                        "first": "Alice",
                        "second": "Bob"
                    },
                    "out": "{$first} 和 {$second}"
                }
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "Alice 和 Bob");
    }

    @Test
    public void testCentralizedVariableWithTemplates() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "$": {
                        "greeting": "{@name}!"
                        },
                    "out": "{$greeting}"
                }
                """, Map.of("name", "Alice"));
        Assertions.assertEquals(((JSONObject) r).getString("out"), "Alice!");
    }

    // ========== $name() 默认值 ==========

    @Test
    public void testMethodWithDefaultArgs() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "$greet()": "{% return 'Hi ' + _args(0, 'Anonymous') %}",
                    "out1": "{= greet('Alice')}",
                    "out2": "{= greet()}"
                }
                """, Map.of());
        JSONObject jo = (JSONObject) r;
        Assertions.assertEquals(jo.getString("out1"), "Hi Alice");
        Assertions.assertEquals(jo.getString("out2"), "Hi Anonymous");
    }

    @Test
    public void testMethodDefaultForOutOfBounds() throws Exception {
        // _args 越界时通过 _args(idx, default) 拿到默认值
        Object r = RenderEngine.build().renderJson("""
                {
                    "$g()": "{% return _args(1, 'default') %}",
                    "out": "{= g('first')}"
                }
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "default");
    }

    // ========== 默认导入验证 ==========

    @Test
    public void testStringUtilsDefaultImport() throws Exception {
        Object r = RenderEngine.build().render(
                "{% StringUtils.isBlank(@s) %}",
                Map.of("s", "  "));
        Assertions.assertEquals(Boolean.TRUE, r);
    }

    @Test
    public void testBooleanUtilsDefaultImport() throws Exception {
        Object r = RenderEngine.build().render(
                "{% BooleanUtils.toBooleanObject(@v) == Boolean.TRUE %}",
                Map.of("v", "yes"));
        Assertions.assertEquals(Boolean.TRUE, r);
    }

    @Test
    public void testArrayUtilsDefaultImport() throws Exception {
        // removeAllOccurrences(Object[], Object) — 必须用数组，不是 List
        Object r = RenderEngine.build().render(
                "{% ArrayUtils.removeAllOccurrences(new Integer[]{1,2,3,2,1}, 2).length %}",
                Map.of());
        Assertions.assertEquals(3, ((Number) r).intValue());
    }

    @Test
    public void testFastjson2DefaultImport() throws Exception {
        Object r = RenderEngine.build().render(
                "{% def o = new com.alibaba.fastjson2.JSONObject(); o.put('k', 1); return o.getIntValue('k') %}",
                Map.of());
        Assertions.assertEquals(1, ((Number) r).intValue());
    }

    // ========== 组件简写 ==========

    @Test
    public void testComponentShorthandIf() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "@if|!blank": "{@x}",
                    "then": "yes",
                    "else": "no"
                }
                """, Map.of("x", "hello"));
        Assertions.assertEquals(r, "yes");
    }

    @Test
    public void testComponentShorthandIfElse() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "@if|blank": "{@x}",
                    "then": "blank",
                    "else": "not-blank"
                }
                """, Map.of("x", ""));
        Assertions.assertEquals(r, "blank");
    }

    @Test
    public void testComponentShorthandSingleLineRest() throws Exception {
        // "@rest|get:url" 单行简写：使用一个不可达的 URL，断言异常传播
        RenderEngine engine = RenderEngine.build();
        try {
            engine.renderJson("""
                    {
                      "@rest|get:./does-not-exist-12345.json"
                    }
                    """, Map.of());
            Assertions.fail("不存在的 URL 应抛 IOException");
        } catch (Exception ex) {
            Assertions.assertNotNull(ex);
        }
    }

    // ========== "*": [...] 数组合并 ==========

    @Test
    public void testStarMergeArrayOfMaps() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "*": [
                        { "a": "A1" },
                        { "b": "B1" }
                    ]
                }
                """, Map.of());
        JSONObject jo = (JSONObject) r;
        Assertions.assertEquals(jo.getString("a"), "A1");
        Assertions.assertEquals(jo.getString("b"), "B1");
    }

    @Test
    public void testStarMergeWithExisting() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "a": "before",
                    "*": { "a": "after", "b": "added" }
                }
                """, Map.of());
        JSONObject jo = (JSONObject) r;
        Assertions.assertEquals(jo.getString("a"), "after");
        Assertions.assertEquals(jo.getString("b"), "added");
    }

    // ========== # 注释 ==========

    @Test
    public void testCommentKeyStripped() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "#": "this is a comment",
                    "#TODO": "fixme",
                    "name": "{@name}"
                }
                """, Map.of("name", "alice"));
        JSONObject jo = (JSONObject) r;
        Assertions.assertFalse(jo.containsKey("#"));
        Assertions.assertFalse(jo.containsKey("#TODO"));
        Assertions.assertEquals(jo.getString("name"), "alice");
    }

    // ========== YAML 多行脚本 ==========

    @Test
    public void testYamlMultilineScript() throws Exception {
        String yaml = """
                result%: |-
                  int a = 1;
                  int b = 2;
                  return a + b
                """;
        Object r = RenderEngine.build().renderYaml(yaml, Map.of());
        Assertions.assertEquals(3, ((JSONObject) r).getIntValue("result"));
    }

    // ========== @ 后跟 @ 数据 (递归切换) ==========

    @Test
    public void testNestedAtDataOverride() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "name": "@",
                    "sub": {
                        "@": { "name": "inner" },
                        "name": "@"
                    }
                }
                """, Map.of("name", "outer"));
        JSONObject jo = (JSONObject) r;
        Assertions.assertEquals(jo.getString("name"), "outer");
        Assertions.assertEquals(((JSONObject) jo.get("sub")).getString("name"), "inner");
    }

    // ========== each body 返回对象 ==========

    @Test
    public void testEachBodyReturnsObject() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "@class": "each",
                    "select": [1, 2, 3],
                    "body": { "v": "{$item}" }
                }
                """, Map.of());
        JSONArray arr = (JSONArray) r;
        Assertions.assertEquals(3, arr.size());
        Assertions.assertEquals(1, arr.getJSONObject(0).getIntValue("v"));
        Assertions.assertEquals(3, arr.getJSONObject(2).getIntValue("v"));
    }

    // ========== _args 基本行为 ==========

    @Test
    public void testArgsArrayAndIndexed() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "$sum()": "{% return _args[0] + _args[1] + _args[2] %}",
                    "out": "{= sum(1,2,3)}"
                }
                """, Map.of());
        Assertions.assertEquals(6, ((JSONObject) r).getIntValue("out"));
    }

    @Test
    public void testArgsOutOfBounds() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "$f()": "{% return _args[5] %}",
                    "out": "{= f(1,2)}"
                }
                """, Map.of());
        Assertions.assertNull(((JSONObject) r).get("out"));
    }

    // ========== invoke 组件 ==========

    @Test
    public void testInvokeComponent() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "@class": "invoke",
                    "select": "StringUtils.firstNonBlank(' ', null, 'hello')"
                }
                """, Map.of());
        Assertions.assertEquals(r, "hello");
    }

    @Test
    public void testInvokeComponentWithMethod() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "@class": "invoke",
                    "select": "{= Math }",
                    "method": "abs",
                    "args": [-10]
                }
                """, Map.of());
        Assertions.assertEquals(10, ((Number) r).intValue());
    }

    // ========== render 组件 二次渲染 ==========

    @Test
    public void testRenderComponentLateBind() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "name": "alice",
                    "out": {
                        "@class": "render",
                        "select": { "name": "{@name}" }
                    }
                }
                """, Map.of("name", "alice"));
        JSONObject jo = (JSONObject) r;
        Assertions.assertEquals(((JSONObject) jo.get("out")).getString("name"), "alice");
    }

    // ========== switch 数字匹配 ==========

    @Test
    public void testSwitchNumberIntMatch() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "@class": "switch",
                    "select": "{= @code + '' }",
                    "cases": {
                        "200": "OK",
                        "404": "Not Found",
                        "500": "Server Error"
                    }
                }
                """, Map.of("code", 200));
        Assertions.assertEquals(r, "OK");
    }

    @Test
    public void testSwitchDoubleMatchedAsInt() throws Exception {
        // 9.0 也应该匹配 "9"
        Object r = RenderEngine.build().renderJson("""
                {
                    "@class": "switch",
                    "select": "{= @code }",
                    "cases": {
                        "9": "nine"
                    }
                }
                """, Map.of("code", 9.0));
        Assertions.assertEquals(r, "nine");
    }

    @Test
    public void testSwitchDefaultFallback() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "@class": "switch",
                    "select": "{@color}",
                    "cases": { "red": "r" },
                    "default": "unknown"
                }
                """, Map.of("color", "blue"));
        Assertions.assertEquals(r, "unknown");
    }

    // ========== 简化 is 写法 ==========

    @Test
    public void testIfWithTrueAlias() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "@class": "if",
                    "select": "{@v}",
                    "true": "yes",
                    "false": "no"
                }
                """, Map.of("v", "yes"));
        Assertions.assertEquals(r, "yes");
    }

    @Test
    public void testIfEmpty() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "@class": "if",
                    "select": "{@xs}",
                    "is": "empty",
                    "then": "empty",
                    "else": "not empty"
                }
                """, Map.of("xs", List.of()));
        Assertions.assertEquals(r, "empty");
    }
}