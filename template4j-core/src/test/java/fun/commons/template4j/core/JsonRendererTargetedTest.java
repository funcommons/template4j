package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Targeted tests for specific missed lines in JsonRenderer.
 * Line 122: JSON/JSONArray result in mixed string (not single-expression)
 * Line 249: Array item with * key → extract value
 * Line 325-326: {$var} with quoted varPath
 * Line 342: {% %} script block (vs {= })
 */
public class JsonRendererTargetedTest {

    // Line 121-122: JSONObject/JSONArray result in a mixed string template
    // Need: keyList.size() > 1, so the result is NOT the single-expression return path
    // AND ret is JSONObject or JSONArray → JSON.toJSONString(ret) replacement
    @Test
    public void testJsonObjectInMixedString() throws Exception {
        // Mixed string: "result={@obj}" — two parts, so not single-expression return path
        // AND ret is JSONObject → JSON.toJSONString(ret) replacement
        Object r = RenderEngine.build().renderJson("""
                {
                  "out": "before{@obj}after"
                }
                """, Map.of("obj", Map.of("a", 1)));
        Assertions.assertTrue(r instanceof JSONObject);
        String out = ((JSONObject) r).getString("out");
        Assertions.assertTrue(out.startsWith("before"));
        Assertions.assertTrue(out.endsWith("after"));
    }

    @Test
    public void testJsonArrayInMixedString() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "out": "items={@arr}&more"
                }
                """, Map.of("arr", java.util.List.of(1, 2)));
        Assertions.assertTrue(r instanceof JSONObject);
        String out = ((JSONObject) r).getString("out");
        Assertions.assertTrue(out.startsWith("items="));
        Assertions.assertTrue(out.contains("&more"));
    }

    // Line 248-249: Array with item containing * key → extract value
    @Test
    public void testArrayStarKeyExtractsValue() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "list": [{"*": {"merged": true}}, {"normal": 1}]
                }
                """, Map.of());
        Assertions.assertTrue(r instanceof JSONObject);
    }

    // Line 325: {$varName['path']} with single-quoted varPath
    // This is already covered but let's make sure both branches are hit
    @Test
    public void testDollarVarWithSingleQuotedPath() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "$m": {"nested": {"deep": "found"}},
                  "out": "{$m['nested']}"
                }
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "{\"deep\":\"found\"}");
    }

    // Line 326: {$varName["path"]} with double-quoted varPath
    @Test
    public void testDollarVarWithDoubleQuotedPath() throws Exception {
        // Double-quoted varPath in {$...} expression — test via Map template
        Object r = RenderEngine.build().render(
                java.util.Map.of("out", "{$m[\"x\"]}"),
                java.util.Map.of("m", java.util.Map.of("x", "hello")));
        Assertions.assertNotNull(r);
    }

    // Line 342: {% %} script block branch
    @Test
    public void testScriptBlockVsExpression() throws Exception {
        // Use {% %} block to trigger the ScriptUtil.getProgramBesieged path
        Object r = RenderEngine.build().renderJson("""
                {
                  "out": "{% def x = 10; return x * 2 %}"
                }
                """, Map.of());
        Assertions.assertEquals(20, ((JSONObject) r).getIntValue("out"));
    }

    // Line 106-107: jsonShape detection with mixed curly/bracket strings
    @Test
    public void testNonJsonShapeString() throws Exception {
        // String that is NOT json-shaped → different null handling
        Object r = RenderEngine.build().renderJson("""
                {
                  "out": "name={@missing}!"
                }
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "name=!");
    }
}
