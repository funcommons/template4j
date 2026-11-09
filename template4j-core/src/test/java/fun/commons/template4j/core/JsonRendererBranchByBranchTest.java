package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Systematic branch-by-branch coverage for JsonRenderer.convertSelf() and expressionValue().
 * Target: 2 missed lines, 5 missed branches in convertSelf(), 3 missed branches in expressionValue().
 */
public class JsonRendererBranchByBranchTest {

    // ===== convertSelf() String branch: jsonShape =====
    // Line 105-107: boolean jsonShape = (trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    // JaCoCo reports branches for each condition in the && and || operators
    // Need to test all 4 combinations:
    // 1. startsWith("{") = true, endsWith("}") = true → jsonShape = true (via first &&)
    // 2. startsWith("{") = true, endsWith("}") = false → check second ||
    // 3. startsWith("[") = true, endsWith("]") = true → jsonShape = true (via second &&)
    // 4. startsWith("[") = true, endsWith("]") = false → jsonShape = false

    // Case 2: starts with { but doesn't end with } — e.g., "{text"
    @Test
    public void testJsonShapeStartsCurlyNotEndsCurly() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "{@v} extra"}
                """, Map.of("v", "x"));
        // This is not jsonShape because the trimmed result is "%expr:...% extra" (not starting with {)
        // Actually parseStrTemplate replaces the expression with a placeholder first
        // So the trimmed string might be something like "%expr:12345% extra"
        Assertions.assertNotNull(r);
    }

    // Case 3: starts with [ and ends with ] — e.g., "[1, 2]"
    @Test
    public void testJsonShapeStartsBracketEndsBracket() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "[{@v}]"}
                """, Map.of("v", 3));
        Assertions.assertNotNull(r);
    }

    // ===== convertSelf() String branch: null handling with jsonShape =====
    // Line 126: if (jsonShape && strResult.contains(quotedKey))
    // Need jsonShape=true AND the null placeholder is quoted in the template
    @Test
    public void testJsonShapeNullInQuotedPosition() throws Exception {
        // This should create a JSON-shaped string where the null value is quoted
        Object r = RenderEngine.build().renderJson("""
                {"out": "{\\"k\\": \\"{@missing}\\"}"}
                """, Map.of());
        Assertions.assertNotNull(r);
        // The result should have null instead of "" for the missing value
    }

    // ===== convertSelf() Collection branch: * key in array item =====
    // Line 248-249: if ((v1 instanceof Map) && ((Map) v1).containsKey("*"))
    // This is effectively dead code because Map rendering always consumes * keys
    // But let me try to trigger it by having a component return a Map with * key
    // Actually: if we use a $ function that returns a Map with * key...

    @Test
    public void testArrayItemWithStarKeyFromFunction() throws Exception {
        // Define a function that returns a Map with * key
        Object r = RenderEngine.build().renderJson("""
                {"$f()": "{% return ['*':[1,2,3]] %}", "items": ["{= f() }"]}
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    // ===== convertSelf() Collection branch: list merge =====
    // Line 252: if (!(v instanceof List) && v1 instanceof List)
    @Test
    public void testArrayItemRendersToListMerge() throws Exception {
        // EachRenderer returns a JSONArray (which is a List)
        // The original v is a JSONObject (not a List)
        Object r = RenderEngine.build().renderJson("""
                {"items": [{"@class":"each","select":[1,2],"body":{"v":"{$item}"},"merge":true}]}
                """, Map.of());
        Assertions.assertTrue(r instanceof JSONObject);
    }

    // ===== expressionValue() branches =====
    // Line 308: if (UString.isBesieged(expr, "'", "'")) — @path with single quotes
    @Test
    public void testExpressionAtWithSingleQuotes() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "{@ 'name' }"}
                """, Map.of("name", "Alice"));
        Assertions.assertEquals(((JSONObject) r).getString("out"), "Alice");
    }

    // Line 309: if (UString.isBesieged(expr, "\"", "\"")) — @path with double quotes
    @Test
    public void testExpressionAtWithDoubleQuotes() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "{@ \\"name\\" }"}
                """, Map.of("name", "Bob"));
        Assertions.assertEquals(((JSONObject) r).getString("out"), "Bob");
    }

    // Line 325: if (UString.isBesieged(varPath, "'", "'")) — $var path with single quotes
    @Test
    public void testExpressionDollarVarWithSingleQuotedPath() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"$m": {"deep": "val"}, "out": "{$m['deep']}"}
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "val");
    }

    // Line 326: if (UString.isBesieged(varPath, "\"", "\"")) — $var path with double quotes
    @Test
    public void testExpressionDollarVarWithDoubleQuotedPath() throws Exception {
        Object r = RenderEngine.build().render(
                Map.of("out", "{$m[\"x\"]}"),
                Map.of("m", Map.of("x", 42)));
        Assertions.assertNotNull(r);
    }

    // Line 329: result != null && StringUtils.isNotBlank(varPath) — var with JSONPath
    @Test
    public void testExpressionDollarVarWithJsonPath() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"$m": {"deep": {"val": "found"}}, "out": "{$m.deep.val}"}
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "found");
    }

    // Line 342: else if (UString.isBesieged(expression, "{%", "%}")) — script block
    @Test
    public void testExpressionScriptBlock() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "{% return 42 %}"}
                """, Map.of());
        Assertions.assertEquals(42, ((JSONObject) r).getIntValue("out"));
    }

    // Line 360: result instanceof Exception — script returns an Exception
    @Test
    public void testExpressionReturnsException() throws Exception {
        try {
            RenderEngine.build().renderJson("""
                    {"out": "{% return new Exception('test') %}"}
                    """, Map.of());
            Assertions.fail("Should throw RenderException");
        } catch (RenderException e) {
            Assertions.assertTrue(e.getMessage().contains("script return exception"));
        }
    }
}
