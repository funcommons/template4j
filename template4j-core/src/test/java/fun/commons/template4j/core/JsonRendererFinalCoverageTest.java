package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Targeted tests for the remaining missed coverage in JsonRenderer.
 *
 * JaCoCo missed (as of latest run):
 *   Line 106: 1 missed branch — jsonShape: startsWith("{") true, endsWith("}") false
 *   Line 107: 1 missed branch — jsonShape: startsWith("[") true, endsWith("]") false
 *   Line 121: 2 missed branches — ret instanceof JSONObject / JSONArray false branches
 *   Line 122: 7 missed instructions — body of (ret instanceof JSONObject || ret instanceof JSONArray) never entered
 *   Line 342: 1 missed branch — isBesieged(expression, "{%", "%}") false branch
 */
public class JsonRendererFinalCoverageTest {

    // ===== Line 106: jsonShape startsWith("{") true but endsWith("}") false =====
    // After parseStrTemplate, strResult must start with literal "{"
    // and NOT end with "}".
    // Template: "{notexpr{@x}" — the "{notexpr" part stays literal;
    // "{@x}" is replaced by a placeholder like %expr:NNN%.
    // Trimmed result: "{notexpr%expr:NNN%" — starts with "{", ends with "%" not "}".
    @Test
    public void testJsonShapeStartsWithCurlyButNotEndsWithCurly() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"out\": \"{notexpr{@x}\"}",
                Map.of("x", "val"));
        Assertions.assertNotNull(r);
        JSONObject result = (JSONObject) r;
        String out = result.getString("out");
        // strResult after parseStrTemplate: "{notexpr" + placeholder
        // jsonShape: startsWith("{") true, endsWith("}") false -> jsonShape = false
        Assertions.assertTrue(out.contains("val"));
    }

    // ===== Line 107: jsonShape startsWith("[") true but endsWith("]") false =====
    // Template: "[notexpr{@x}" — "[notexpr" stays literal;
    // "{@x}" is replaced by placeholder.
    // Trimmed result: "[notexpr%expr:NNN%" — starts with "[", ends with "%" not "]".
    @Test
    public void testJsonShapeStartsWithSquareButNotEndsWithSquare() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"out\": \"[notexpr{@x}\"}",
                Map.of("x", "val"));
        Assertions.assertNotNull(r);
        JSONObject result = (JSONObject) r;
        String out = result.getString("out");
        // jsonShape: startsWith("[") true, endsWith("]") false -> jsonShape = false
        Assertions.assertTrue(out.contains("val"));
    }

    // ===== Lines 121-122: ret instanceof JSONObject / JSONArray in mixed string =====
    // The key insight: JSONPath.eval on a Map returns a Map (not JSONObject).
    // To get a JSONObject/JSONArray as ret, we need a Groovy expression that
    // explicitly creates one. The GroovyImports include com.alibaba.fastjson2.*
    // so JSONObject and JSONArray are available without import.
    // The expression must be in a MIXED string (not single-expr) so we don't
    // return early at line 118.

    @Test
    public void testJsonObjectResultInMixedString() throws Exception {
        // "pre{= new JSONObject().fluentPut('a',1) }post" is a mixed string
        // The {= ... } expression returns a JSONObject
        // Since it's mixed (not single expr), line 117 is false, we reach line 121
        Object r = RenderEngine.build().renderJson(
                "{\"out\": \"pre{= new JSONObject().fluentPut('a',1) }post\"}",
                Map.of());
        Assertions.assertNotNull(r);
        JSONObject result = (JSONObject) r;
        String out = result.getString("out");
        // The JSONObject should be serialized via JSON.toJSONString and embedded
        Assertions.assertTrue(out.startsWith("pre"));
        Assertions.assertTrue(out.endsWith("post"));
        Assertions.assertTrue(out.contains("\"a\""));
    }

    @Test
    public void testJsonArrayResultInMixedString() throws Exception {
        // "pre{= new JSONArray().fluentAdd(1).fluentAdd(2) }post" is a mixed string
        // The {= ... } expression returns a JSONArray
        Object r = RenderEngine.build().renderJson(
                "{\"out\": \"pre{= new JSONArray().fluentAdd(1).fluentAdd(2) }post\"}",
                Map.of());
        Assertions.assertNotNull(r);
        JSONObject result = (JSONObject) r;
        String out = result.getString("out");
        Assertions.assertTrue(out.startsWith("pre"));
        Assertions.assertTrue(out.endsWith("post"));
        Assertions.assertTrue(out.contains("1"));
    }

    // ===== Line 342: isBesieged(expression, "{%", "%}") false branch =====
    // This branch is hit when expressionValue receives a raw script (no {= } or {% %} wrapping).
    // This cannot happen through parseStrTemplate (which always adds delimiters)
    // or through $enter{}/$return{} (which getProgramBesieged always wraps in {% %}).
    // We use reflection to call expressionValue directly with a bare script.
    @Test
    public void testExpressionValueWithRawScript() throws Exception {
        JsonRenderer renderer = (JsonRenderer) RenderEngine.build();
        SelfObject self = new SelfObject(renderer, Map.of());

        // Use reflection to access the protected expressionValue method
        Method method = JsonRenderer.class.getDeclaredMethod(
                "expressionValue", String.class, SelfObject.class, String.class);
        method.setAccessible(true);

        // Call with a raw Groovy script (no {= } or {% %} wrapping)
        Object result = method.invoke(renderer, "1 + 2", self, "test");

        Assertions.assertEquals(3, result);
    }

    // ===== Additional: confirm * key in array via String expression =====
    @Test
    public void testStarKeyInArrayViaStringExpression() throws Exception {
        // List with a String item that evaluates to a Map with "*" key
        Object r = RenderEngine.build().render(
                List.of("{= ['*':['a','b']] }"),
                Map.of());
        Assertions.assertTrue(r instanceof JSONArray);
        JSONArray arr = (JSONArray) r;
        // The "*" key should be extracted, yielding ['a','b'] which is a List
        // Since v (String) is not a List but v1 (List) is, the merge branch is taken
        Assertions.assertTrue(arr.size() >= 2);
    }

    // ===== Additional: confirm list merge in array (line 252) =====
    @Test
    public void testArrayItemNotListRendersToList() throws Exception {
        // An each component inside an array, with merge:true
        // The each component returns a List of rendered body results
        // Since the original item is a Map (not a List) but the rendered result
        // is a List, the merge branch (line 252-254) is triggered
        Object r = RenderEngine.build().renderJson(
                "[{\"@class\":\"each\",\"select\":[1,2,3],\"body\":{\"v\":\"{$item}\"},\"merge\":true}]",
                Map.of());
        Assertions.assertTrue(r instanceof JSONArray);
        JSONArray arr = (JSONArray) r;
        // The each produces 3 items, merged into the parent array
        Assertions.assertEquals(3, arr.size());
    }
}
