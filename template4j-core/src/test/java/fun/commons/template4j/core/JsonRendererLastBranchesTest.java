package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Targeted tests for the last remaining missed branches in JsonRenderer.
 * convertSelf(): lines 106-107 (jsonShape), 248-249 (* key in array), 252 (list merge)
 * expressionValue(): lines 308-309 (quoted @path), 325-326 (quoted varPath), 329 (varPath with result)
 */
public class JsonRendererLastBranchesTest {

    // ===== convertSelf() branches =====

    // Line 107: jsonShape with square brackets — template string "[...]"
    @Test
    public void testJsonShapeSquareBrackets() throws Exception {
        // String that starts with [ and ends with ] → jsonShape=true via [ branch
        Object r = RenderEngine.build().renderJson("""
                {"out": "[1, 2, {@v}]"}
                """, Map.of("v", 3));
        Assertions.assertNotNull(r);
        String out = ((JSONObject) r).getString("out");
        Assertions.assertTrue(out.startsWith("["));
    }

    // Line 248-249: * key in array item
    @Test
    public void testStarKeyInArrayItem() throws Exception {
        // Array item that renders to a Map with * key → merge into parent
        Object r = RenderEngine.build().renderJson("""
                {"items": [{"*": {"a": 1, "b": 2}}, {"c": 3}]}
                """, Map.of());
        Assertions.assertTrue(r instanceof JSONObject);
        // Just verify it doesn't throw
    }

    // Line 252: v is NOT a List but v1 IS a List → merge into target
    @Test
    public void testArrayItemRendersToList() throws Exception {
        // An each component with merge:true returns a list
        // The item in the array is NOT a list, but after rendering it becomes a list
        // This should trigger the target.addAll() path
        Object r = RenderEngine.build().renderJson("""
                {"items": [{"@class":"each","select":[1,2],"body":{"v":"{$item}"},"merge":true}]}
                """, Map.of());
        Assertions.assertTrue(r instanceof JSONObject);
    }

    // ===== expressionValue() branches =====

    // Line 308: {@path} with single-quoted path
    @Test
    public void testAtExpressionSingleQuotedPath() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "{@ 'name' }"}
                """, Map.of("name", "Alice"));
        Assertions.assertEquals(((JSONObject) r).getString("out"), "Alice");
    }

    // Line 309: {@path} with double-quoted path
    @Test
    public void testAtExpressionDoubleQuotedPath() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "{@ \\"name\\" }"}
                """, Map.of("name", "Bob"));
        Assertions.assertEquals(((JSONObject) r).getString("out"), "Bob");
    }

    // Line 325: {$var['path']} with single-quoted path
    @Test
    public void testDollarVarSingleQuotedPath() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"$m": {"deep": {"val": "found"}}, "out": "{$m['deep']}"}
                """, Map.of());
        JSONObject result = (JSONObject) r;
        Assertions.assertNotNull(result.get("out"));
    }

    // Line 326: {$var["path"]} with double-quoted path
    @Test
    public void testDollarVarDoubleQuotedPath() throws Exception {
        // Can't use double quotes inside JSON string, use render() with Map
        Object r = RenderEngine.build().render(
                Map.of("out", "{$m[\"x\"]}"),
                Map.of("m", Map.of("x", 42)));
        Assertions.assertNotNull(r);
    }

    // Line 329: result != null && varPath is not blank → JSONPath.eval
    @Test
    public void testDollarVarWithJsonPath() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"$m": {"deep": {"val": "found"}}, "out": "{$m.deep.val}"}
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "found");
    }

    // Line 342: {% %} script block (vs {= })
    @Test
    public void testScriptBlockExpression() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "{% def x = 10; return x * 2 %}"}
                """, Map.of());
        Assertions.assertEquals(20, ((JSONObject) r).getIntValue("out"));
    }

    // Line 126-127: jsonShape with null value in quoted position
    @Test
    public void testJsonShapeNullInQuotedPosition() throws Exception {
        // jsonShape=true, null result, and the key is quoted in the template
        Object r = RenderEngine.build().renderJson("""
                {"out": "{\\"key\\": \\"{@missing}\\"}"}
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    // Line 121: JSONObject/JSONArray result in mixed string (not single-expression)
    @Test
    public void testJsonObjectInMixedString() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "prefix{@obj}suffix"}
                """, Map.of("obj", Map.of("a", 1)));
        String out = ((JSONObject) r).getString("out");
        Assertions.assertTrue(out.startsWith("prefix"));
        Assertions.assertTrue(out.endsWith("suffix"));
    }

    @Test
    public void testJsonArrayInMixedString() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "prefix{@arr}suffix"}
                """, Map.of("arr", List.of(1, 2)));
        String out = ((JSONObject) r).getString("out");
        Assertions.assertTrue(out.startsWith("prefix"));
        Assertions.assertTrue(out.endsWith("suffix"));
    }
}
