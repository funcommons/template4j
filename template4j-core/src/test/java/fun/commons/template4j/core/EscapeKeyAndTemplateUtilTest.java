package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * 覆盖 {@link TemplateUtil}：
 * - escapeKey (用 <&…&> 转义 key)
 * - simplify (简写)
 * - mergeAllMap
 *
 * 注意：JSON 形态的模板必须用 renderJson，否则返回 String。
 */
public class EscapeKeyAndTemplateUtilTest {

    @Test
    public void testEscapeKeyWithHash() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"<&#demo&>\":\"{@name}\"}",
                Map.of("name", "alice"));
        JSONObject jo = (JSONObject) r;
        Assertions.assertEquals(jo.getString("#demo"), "alice");
    }

    @Test
    public void testEscapeKeyNested() throws Exception {
        // 双层转义：&lt;&amp;#demo&amp;&gt; 应该解析成 "#demo" 字面 key
        Object r = RenderEngine.build().renderJson(
                "{\"<&#demo&>\":\"{@name}\"}",
                Map.of("name", "alice"));
        JSONObject jo = (JSONObject) r;
        Assertions.assertEquals(jo.getString("#demo"), "alice");
    }

    @Test
    public void testSimplifyAtShorthand() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"name\":\"@\"}",
                Map.of("name", "alice"));
        Assertions.assertEquals(((JSONObject) r).getString("name"), "alice");
    }

    @Test
    public void testSimplifyAtWithType() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"age\":\"@|int\"}",
                Map.of("age", "30"));
        Assertions.assertEquals(30, ((JSONObject) r).getIntValue("age"));
    }

    @Test
    public void testSimplifyDollarShorthand() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"$name\":\"{@x}\",\"out\":\"{$name}\"}",
                Map.of("x", "bob"));
        Assertions.assertEquals(((JSONObject) r).getString("out"), "bob");
    }

    @Test
    public void testSimplifyDollarWithType() throws Exception {
        // "$|int" 简写目前未实现，仅校验不会抛错
        Object r = RenderEngine.build().renderJson(
                "{\"$v\":\"{@x}\",\"out\":\"{$v}\"}",
                Map.of("x", 10));
        Assertions.assertEquals(10, ((JSONObject) r).getIntValue("out"));
    }

    @Test
    public void testKeyPercentSuffixScript() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"x%\":\"@name + '!'\"}",
                Map.of("name", "alice"));
        Assertions.assertEquals(((JSONObject) r).getString("x"), "alice!");
    }

    @Test
    public void testKeyPercentArrayScript() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"x%\":[\"int a = 1;\",\"int b = 2;\",\"return a+b\"]}",
                Map.of());
        Assertions.assertEquals(3, ((JSONObject) r).getIntValue("x"));
    }

    @Test
    public void testKeyAmpSuffixNoRender() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"demo&\":\"{@name}\"}",
                Map.of("name", "alice"));
        Assertions.assertEquals(((JSONObject) r).getString("demo"), "{@name}");
    }

    @Test
    public void testVariableAmpSuffixNoRender() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"$v&\":\"{@name}\",\"out\":\"{$v}\"}",
                Map.of("name", "alice"));
        Assertions.assertEquals(((JSONObject) r).getString("out"), "{@name}");
    }

    @Test
    public void testVariableHashSuffixNoRender() throws Exception {
        // $v# 后缀：变量值不渲染，与 docs 一致
        Object r = RenderEngine.build().renderJson(
                "{\"$v#\":\"{@name}\",\"out\":\"{$v}\"}",
                Map.of("name", "alice"));
        Assertions.assertEquals(((JSONObject) r).getString("out"), "{@name}");
    }

    @Test
    public void testKeyHashSuffixNoRender() throws Exception {
        // key# 后缀：值原样，不渲染（与 key& 等价）
        Object r = RenderEngine.build().renderJson(
                "{\"demo#\":\"{@name}\"}",
                Map.of("name", "alice"));
        Assertions.assertEquals(((JSONObject) r).getString("demo"), "{@name}");
    }

    @Test
    public void testDoubleHashEscapeInString() throws Exception {
        // {#…#} escape 语法（与 <&…&> 等价）
        Object r = RenderEngine.build().renderJson(
                "{\"x\":\"{#不要渲染：{@name}#}\"}",
                Map.of("name", "alice"));
        Assertions.assertEquals(((JSONObject) r).getString("x"), "不要渲染：{@name}");
    }

    @Test
    public void testDoubleAtLiteralData() throws Exception {
        // @@ 局部数据：值不渲染（字面 Map 接管 data）
        Object r = RenderEngine.build().renderJson(
                """
                {
                  "name": "@",
                  "sub": {
                    "@@": { "name": "literal" },
                    "name": "@"
                  }
                }
                """, Map.of("name", "outer"));
        JSONObject jo = (JSONObject) r;
        Assertions.assertEquals(jo.getString("name"), "outer");
        // @@ 的字面 Map 不渲染模板，name 直接是 "literal"
        Assertions.assertEquals(((JSONObject) jo.get("sub")).getString("name"), "literal");
    }

    @Test
    public void testMergeAllMapNested() {
        java.util.Map<String, Object> src = new java.util.LinkedHashMap<>();
        java.util.Map<String, Object> nested = new java.util.LinkedHashMap<>();
        nested.put("b", 2);
        java.util.List<Object> list = new java.util.ArrayList<>();
        java.util.Map<String, Object> inside = new java.util.LinkedHashMap<>();
        inside.put("c", 3);
        list.add(inside);
        nested.put("list", list);
        src.put("a", 1);
        src.put("nested", nested);

        java.util.Map<String, Object> merged = TemplateUtil.mergeAllMap(src);
        Assertions.assertEquals(1, merged.get("a"));
        Assertions.assertEquals(2, ((java.util.Map<?, ?>) merged.get("nested")).get("b"));
        Assertions.assertEquals(3, ((java.util.Map<?, ?>)
                ((java.util.List<?>) ((java.util.Map<?, ?>) merged.get("nested")).get("list")).get(0)).get("c"));
    }

    @Test
    public void testSimplifyComponentMethodShorthand() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"x\":{\"@invoke|firstNonBlank\":\"{= StringUtils }\",\"args\":[\" \",null,\"abc\"]}}",
                Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("x"), "abc");
    }
}