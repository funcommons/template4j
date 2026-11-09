package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * 覆盖 {@link SelfObject} 公开行为（path、vars 作用域、locks 递归守卫）
 * 与 {@link ScriptUtil} 边界用例（@ 在注释 / 字符串中的保留）
 */
public class SelfObjectAndScriptUtilTest {

    // ========== 路径 ==========

    @Test
    public void testPathBuiltFromChain() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "user": {
                        "profile": {
                            "name": "{@name}"
                        }
                    }
                }
                """, Map.of("name", "alice"));
        Assertions.assertEquals(((JSONObject) ((JSONObject) ((JSONObject) r).getJSONObject("user")).getJSONObject("profile")).getString("name"), "alice");
    }

    // ========== 变量作用域 ==========

    @Test
    public void testVariableScopeOverride() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "$x": "outer",
                    "sub": {
                        "$x": "inner",
                        "v": "{$x}"
                    },
                    "top": "{$x}"
                }
                """, Map.of());
        JSONObject jo = (JSONObject) r;
        Assertions.assertEquals(jo.getString("top"), "outer");
        Assertions.assertEquals(((JSONObject) jo.get("sub")).getString("v"), "inner");
    }

    @Test
    public void testVariableScopeInherits() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                    "$x": "outer",
                    "sub": {
                        "v": "{$x}"
                    }
                }
                """, Map.of());
        Assertions.assertEquals(((JSONObject) ((JSONObject) r).get("sub")).getString("v"), "outer");
    }

    // ========== 递归守卫 ==========

    @Test
    public void testSelfRecursiveMethodReturnsNull() throws Exception {
        // 同名递归调用会被 locks 阻止，被阻止的调用返回 null。
        // 因此 f(3) 内部尝试 f(2) 时拿到 null，最终表达式 `_args[0] <= 0 ? 0 : null` 返回 null。
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                    "$f()": "{% return _args[0] <= 0 ? 0 : f(_args[0]-1) %}",
                    "out": "{= f(3)}"
                }
                """, Map.of());
        Assertions.assertNull(((JSONObject) r).get("out"));
    }

    @Test
    public void testMutualRecursionReturnsNull() throws Exception {
        // 相互递归：a() 调 b()、b() 调 a()，locks 阻止同名重入，被阻止侧返回 null
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                    "$a()": "{% return b() %}",
                    "$b()": "{% return a() %}",
                    "out": "{= a()}"
                }
                """, Map.of());
        Assertions.assertNull(((JSONObject) r).get("out"));
    }

    // ========== ScriptUtil @ 改写 边界 ==========

    @Test
    public void testAtInsideSingleQuote() throws Exception {
        Object r = RenderEngine.build().render(
                "{% return 'literal @value' %}", Map.of("value", "X"));
        Assertions.assertEquals(r, "literal @value");
    }

    @Test
    public void testAtInsideDoubleQuote() throws Exception {
        Object r = RenderEngine.build().render(
                "{% return \"literal @value\" %}", Map.of("value", "X"));
        Assertions.assertEquals(r, "literal @value");
    }

    @Test
    public void testAtInsideLineComment() throws Exception {
        // Java 文本块中的真实换行
        Object r = RenderEngine.build().render(
                "{% // @value is a comment\n return 1 %}", Map.of("value", "X"));
        Assertions.assertEquals(1, r);
    }

    @Test
    public void testAtInsideBlockComment() throws Exception {
        Object r = RenderEngine.build().render(
                "{% /* @value */ return 1 %}", Map.of("value", "X"));
        Assertions.assertEquals(1, r);
    }

    @Test
    public void testChainedAtOperators() throws Exception {
        Object r = RenderEngine.build().render(
                "{% return @a.b.c %}", Map.of("a", Map.of("b", Map.of("c", 42))));
        Assertions.assertEquals(42, r);
    }

    @Test
    public void testAtStringPath() throws Exception {
        Object r = RenderEngine.build().render(
                "{% return @'a.b.c' %}", Map.of("a", Map.of("b", Map.of("c", "deep"))));
        Assertions.assertEquals(r, "deep");
    }

    @Test
    public void testAtCallFunction() throws Exception {
        Object r = RenderEngine.build().render(
                "{% return @('a.' + 'b') %}", Map.of("a", Map.of("b", 99)));
        Assertions.assertEquals(99, r);
    }

    // ========== @ 重写后的尾随空格/括号 ==========

    @Test
    public void testAtInArithmetic() throws Exception {
        Object r = RenderEngine.build().render(
                "{% return @a + @b * @c %}", Map.of("a", 1, "b", 2, "c", 3));
        Assertions.assertEquals(7, r);
    }

    @Test
    public void testAtInStringConcat() throws Exception {
        Object r = RenderEngine.build().render(
                "{% return 'a' + @name + 'b' %}", Map.of("name", "X"));
        Assertions.assertEquals(r, "aXb");
    }

    // ========== 复杂数据类型：JSONObject / 数组 ==========

    @Test
    public void testAtJsonPathFilter() throws Exception {
        Object r = RenderEngine.build().render(
                "{% return @'items[?(@.price > 100)]' %}", Map.of(
                "items", List.of(
                        Map.of("price", 50, "name", "A"),
                        Map.of("price", 150, "name", "B"),
                        Map.of("price", 200, "name", "C"))));
        Assertions.assertTrue(r instanceof List);
        Assertions.assertEquals(2, ((List<?>) r).size());
    }

    @Test
    public void testAtDeepScan() throws Exception {
        // @..x JSONPath deepScan 0.3.0 起支持：改写为 data("..x")，避免 Groovy spread
        Object r = RenderEngine.build().render(
                "{% return @..name %}", Map.of(
                "a", Map.of("name", "x"),
                "b", Map.of("nested", Map.of("name", "y"))));
        Assertions.assertTrue(r instanceof List, "应返回 List, got " + (r == null ? "null" : r.getClass()));
        Assertions.assertEquals(2, ((List<?>) r).size());
    }

    @Test
    public void testAtDeepScanWithQuotes() throws Exception {
        // @'..x' 也支持（带引号作为 JSONPath 字符串）
        Object r = RenderEngine.build().render(
                "{% return @'..name' %}", Map.of(
                "a", Map.of("name", "x"),
                "b", Map.of("nested", Map.of("name", "y"))));
        Assertions.assertTrue(r instanceof List);
        Assertions.assertEquals(2, ((List<?>) r).size());
    }
}