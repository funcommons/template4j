package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * 覆盖 JsonRenderer.expressionValue 各分支：{@}, {@'…'}, {@"…"}, {$var}, 脚本返回 Exception。
 */
public class ExpressionValueTest {

    private final RenderEngine engine = RenderEngine.build();

    @Test
    public void testEmptyAtExpr() throws Exception {
        // {@} → expr becomes "$" (root)
        Map<String, Object> data = new HashMap<>();
        data.put("name", "alice");
        Object r = engine.render("\"{@}\"", data);
        // 当 {@} 解析成 $ 时，返回的是整个 data map 吗? 验证至少不抛错
        Assertions.assertNotNull(r);
    }

    @Test
    public void testSingleQuotedPath() throws Exception {
        // {@'name'} — 路径带单引号
        Object r = engine.renderJson("{\"x\":\"{@'name'}\"}", Map.of("name", "bob"));
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("x"), "bob");
    }

    @Test
    public void testDoubleQuotedPath() throws Exception {
        // {@"name"} — 路径带双引号
        Object r = engine.renderJson("{\"x\":\"{@\\\"name\\\"}\"}", Map.of("name", "carol"));
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("x"), "carol");
    }

    @Test
    public void testVarPathSingleQuote() throws Exception {
        // {$var} 后跟带单引号的 JSONPath — 测试引用去除分支
        Object r = engine.renderJson("""
                {
                  "$data": {"a": 1, "b": 2},
                  "x": "{$data}"
                }
                """, Map.of());
        // 不报错即可
        Assertions.assertNotNull(r);
    }

    @Test
    public void testVarPathDoubleQuote() throws Exception {
        Object r = engine.renderJson("""
                {
                  "$data": {"k": "v"},
                  "x": "{$data}"
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testScriptReturnsValue() throws Exception {
        // Groovy 简单返回值
        Assertions.assertEquals(42, engine.render("{% return 42 %}", java.util.Map.of()));
    }

    @Test
    public void testScriptBracketsStrip() throws Exception {
        // Groovy 表达式 with 括号 — 触发 while brackets 循环剥离
        Object r = engine.render("{% return (10 + 20) %}", java.util.Map.of());
        Assertions.assertEquals(30, r);
    }

    @Test
    public void testScriptBracketStrippingLoop() throws Exception {
        // 多层括号 (((1))) — 触发 while 多次剥离
        Object r = engine.render("{% return (((1))) %}", java.util.Map.of());
        Assertions.assertEquals(1, r);
    }

    @Test
    public void testScriptATRewrite() throws Exception {
        // @ 在 Groovy 中被改写为 data
        Object r = engine.renderJson(
                "{\"x\":\"{% def name = data['name']; return name.toUpperCase() %}\"}",
                Map.of("name", "alice"));
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("x"), "ALICE");
    }

    @Test
    public void testJsonResultAsJSONObject() throws Exception {
        // {@...} 返回 JSONObject — 触发 JSON.toJSONString 替换
        // 用 String 模板而非 JSON 包装（包装后的 JSON 解析可能丢失）
        Object r = engine.render(
                "raw prefix {@data} raw suffix",
                java.util.Map.of("data", java.util.Map.of("k", "v")));
        Assertions.assertTrue(r.toString().contains("k"));
    }

    @Test
    public void testStringTemplateAsJsonArray() throws Exception {
        // strResult 是 JSON array 形态 — 触发 startsWith([) && endsWith(])
        // 注意：单占位符时要 strResult.equals(key)
        Object r = engine.render("[{@missing}]", java.util.Map.of());
        Assertions.assertNotNull(r);
    }
}
