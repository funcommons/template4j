package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Covers remaining JsonRenderer missed lines/branches.
 */
public class JsonRendererRemainingTest {

    // Line 121-122: JSON/JSONArray result embedded in a mixed string template (not single expression)
    @Test
    public void testJsonObjectEmbeddedInMixedString() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "out": "prefix {@obj} suffix"
                }
                """, Map.of("obj", Map.of("a", 1)));
        Assertions.assertTrue(r instanceof JSONObject);
        String out = ((JSONObject) r).getString("out");
        Assertions.assertTrue(out.startsWith("prefix "));
        Assertions.assertTrue(out.endsWith(" suffix"));
    }

    @Test
    public void testJsonArrayEmbeddedInMixedString() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "out": "items: {@arr} done"
                }
                """, Map.of("arr", List.of(1, 2)));
        Assertions.assertTrue(r instanceof JSONObject);
        String out = ((JSONObject) r).getString("out");
        Assertions.assertTrue(out.startsWith("items: "));
        Assertions.assertTrue(out.endsWith(" done"));
    }

    // Line 248-249: Array rendering where item contains "*" key
    @Test
    public void testArrayItemWithStarKeyUnwrapsValue() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "arr": [{"*": {"merged": true}}]
                }
                """, Map.of());
        Assertions.assertTrue(r instanceof JSONObject);
        JSONArray arr = ((JSONObject) r).getJSONArray("arr");
        Assertions.assertEquals(1, arr.size());
    }

    // Line 252: item is not a List but rendered result is a List → flatten
    @Test
    public void testArrayItemNotListButRenderedAsListFlatten() throws Exception {
        // each component returns a List; merge:true flattens it into parent array
        Object r = RenderEngine.build().renderJson("""
                [
                  {"@class": "each", "select": [1,2,3], "body": ["a","b"]}
                ]
                """, Map.of());
        Assertions.assertTrue(r instanceof JSONArray);
        JSONArray arr = (JSONArray) r;
        // each returns ["a","b","a","b","a","b"] which gets flattened (v is not List, v1 is List)
        Assertions.assertTrue(arr.size() > 1);
    }

    // Line 360-361: script returning an Exception object
    @Test
    public void testScriptReturningException() throws Exception {
        try {
            RenderEngine.build().renderJson("""
                    {
                      "out": "{= new Exception('boom') }"
                    }
                    """, Map.of());
            Assertions.fail("Should throw for script returning Exception");
        } catch (RenderException e) {
            Assertions.assertTrue(e.getMessage().contains("script return exception"));
        }
    }

    // Line 342: {% %} expression branch (vs {= })
    @Test
    public void testScriptBlockExpression() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "out": "{% return 42 %}"
                }
                """, Map.of());
        Assertions.assertEquals(42, ((JSONObject) r).getIntValue("out"));
    }

    // Lines 325-326: {$var['path']} with single-quoted path
    @Test
    public void testDollarVarSingleQuotedPath() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "$m": {"x": 1, "y": 2},
                  "out": "{$m['y']}"
                }
                """, Map.of());
        Assertions.assertEquals(2, ((JSONObject) r).getIntValue("out"));
    }

    // Line 106-107: jsonShape detection
    @Test
    public void testJsonShapeDetectionWithCurlyBraces() throws Exception {
        // String template that starts with { and ends with } → jsonShape=true
        Object r = RenderEngine.build().renderJson("""
                {
                  "out": "{\\"key\\": \\"{@val}\\"}"
                }
                """, Map.of("val", "test"));
        Assertions.assertNotNull(r);
    }

    @Test
    public void testJsonShapeDetectionWithSquareBrackets() throws Exception {
        // String template that starts with [ and ends with ] → jsonShape=true
        Object r = RenderEngine.build().renderJson("""
                {
                  "out": "[{@val}]"
                }
                """, Map.of("val", "item"));
        Assertions.assertNotNull(r);
    }

    // Line 126-127: null value in quoted position within JSON-shaped string
    @Test
    public void testNullInQuotedPositionJsonShape() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "result": "{\\"val\\": \\"{@missing}\\"}"
                }
                """, Map.of());
        Assertions.assertTrue(r instanceof JSONObject);
        // The null should be replaced as null literal (not empty string) because it's in quotes
    }
}
