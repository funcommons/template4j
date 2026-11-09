package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers the last remaining branches in expressionValue():
 * - Line 329 false branch: result != null but varPath is blank
 * - Also covers any remaining branches in the quote stripping
 */
public class ExpressionValueLastBranchTest {

    // $var with no path → varPath is blank → false branch of line 329
    @Test
    public void testDollarVarNoPathResultNotNull() throws Exception {
        // {$m} where m is defined → result is not null, varPath is blank
        Object r = RenderEngine.build().renderJson("""
                {"$m": "hello", "out": "{$m}"}
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "hello");
    }

    // $var with path → varPath is not blank → true branch of line 329
    @Test
    public void testDollarVarWithPathResultNotNull() throws Exception {
        // {$m.x} where m.x is defined → result is not null, varPath is not blank
        Object r = RenderEngine.build().renderJson("""
                {"$m": {"x": 42}, "out": "{$m.x}"}
                """, Map.of());
        Assertions.assertEquals(42, ((JSONObject) r).getIntValue("out"));
    }

    // $var with path but result is null → line 329 is skipped entirely
    @Test
    public void testDollarVarWithPathResultNull() throws Exception {
        // {$m.nonexistent} → vars returns null → result is null
        Object r = RenderEngine.build().renderJson("""
                {"$m": {"x": 42}, "out": "{$m.nonexistent}"}
                """, Map.of());
        Assertions.assertNull(((JSONObject) r).get("out"));
    }

    // {# #} literal text (alternative escape syntax)
    @Test
    public void testHashLiteral() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "{# hello #}"}
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), " hello ");
    }

    // <& &> literal text
    @Test
    public void testAngleLiteral() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "<& hello &>"}
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), " hello ");
    }

    // @path with empty expression → expr = "$" → JSONPath.eval(data, "$")
    @Test
    public void testAtPathEmptyExpression() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "{@ }"}
                """, Map.of("name", "test"));
        // When the @ expression is empty, it uses "$" which returns the root data
        Assertions.assertNotNull(r);
    }
}
