package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers JsonRenderer missed branches/lines.
 */
public class JsonRendererMissedLinesTest {

    @Test
    public void testJsonShapeNullQuotedKeyReplacement() throws Exception {
        // JSON-shaped string with null in quoted position → "key" replaced with null
        Object r = RenderEngine.build().renderJson("""
                {
                  "result": "{\\"val\\": \\"{@missing_field}\\"}"
                }
                """, Map.of());
        Assertions.assertTrue(r instanceof JSONObject);
    }

    @Test
    public void testArrayMergeWithStarKey() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "items": [
                    {"*": {"a": 1}},
                    {"*": {"b": 2}}
                  ]
                }
                """, Map.of());
        Assertions.assertTrue(r instanceof JSONObject);
        JSONObject result = (JSONObject) r;
        JSONArray items = result.getJSONArray("items");
        Assertions.assertNotNull(items);
    }

    @Test
    public void testDollarVarWithQuotedPath() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "$myvar": {"nested": "deep"},
                  "out": "{$myvar['nested']}"
                }
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "deep");
    }

    @Test
    public void testDollarVarWithoutPath() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "$val": 42,
                  "out": "{$val}"
                }
                """, Map.of());
        Assertions.assertEquals(42, ((JSONObject) r).getIntValue("out"));
    }

    @Test
    public void testJsonShapeStringWithNullUnquoted() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "result": "[{@nonexistent}]"
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testStringTemplateWithSingleExpressionReturnsNonString() throws Exception {
        // When a single expression is the entire string → return non-string result
        // Use render() with a bare string template, not renderJson
        Object r = RenderEngine.build().render("{@items}", Map.of("items", new JSONArray()));
        Assertions.assertTrue(r instanceof JSONArray);
    }

    @Test
    public void testJsonShapeWithJsonObjectResult() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "result": "{@obj}"
                }
                """, Map.of("obj", Map.of("a", 1)));
        Assertions.assertTrue(r instanceof JSONObject);
    }

    @Test
    public void testParenthesizedScriptExpression() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "out": "{= (1 + 2) }"
                }
                """, Map.of());
        Assertions.assertEquals(3, ((JSONObject) r).getIntValue("out"));
    }
}
